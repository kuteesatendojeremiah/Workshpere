package com.worksphere.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.outbox.DomainEventType;
import com.worksphere.common.outbox.OutboxService;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plans, subscriptions and payment webhooks (spec section 20).
 *
 * <p>Key rules:
 * <ul>
 *   <li>Choosing a PAID plan only starts a payment. The plan changes when the provider's
 *       {@code payment.succeeded} webhook arrives, never because the browser says "I paid".
 *   <li>The webhook is processed IDEMPOTENTLY: its provider event id is inserted into
 *       {@code payment_events} (primary key) in the same transaction as the state change, so a
 *       redelivered event is recognised and ignored.
 *   <li>Which plan is in force is computed from the stored state and the clock (grace period after a
 *       failed payment, cancellation at the end of the period), so nothing needs a scheduled job.
 *   <li>Limits only stop NEW projects / members. Existing ones are never deleted on a downgrade.
 * </ul>
 */
@Service
public class BillingService {

    private static final String MANAGE_BILLING = "MANAGE_BILLING";
    private static final String DEFAULT_CURRENCY = "USD";

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final OrganizationService organizationService;
    private final PermissionChecker permissionChecker;
    private final PaymentGateway paymentGateway;
    private final AuditService auditService;
    private final OutboxService outboxService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BillingService(
            PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            BillingRecordRepository billingRecordRepository,
            OrganizationService organizationService,
            PermissionChecker permissionChecker,
            PaymentGateway paymentGateway,
            AuditService auditService,
            OutboxService outboxService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.billingRecordRepository = billingRecordRepository;
        this.organizationService = organizationService;
        this.permissionChecker = permissionChecker;
        this.paymentGateway = paymentGateway;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Plan> listPlans() {
        return planRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getMonthlyPriceCents(), b.getMonthlyPriceCents()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID organizationId, UUID callerUserId) {
        organizationService.requireMembership(organizationId, callerUserId);
        return view(subscriptionRepository.findByOrganizationId(organizationId).orElse(null));
    }

    @Transactional
    public CheckoutResponse changePlan(UUID organizationId, UUID callerUserId, String planKey) {
        requireBillingPermission(organizationId, callerUserId);
        Plan plan = planRepository.findById(planKey).orElseThrow(() -> new PlanNotFoundException(planKey));
        Subscription subscription = subscriptionRepository
                .findByOrganizationId(organizationId)
                .orElseGet(() -> subscriptionRepository.save(new Subscription(organizationId)));

        Plan current = effectivePlan(subscription);
        if (current.getPlanKey().equals(plan.getPlanKey()) && !subscription.isCancelAtPeriodEnd()) {
            throw new AlreadyOnPlanException(plan.getName());
        }

        if (plan.isFree()) {
            subscription.switchToFree();
            auditService.record(organizationId, callerUserId, AuditAction.SUBSCRIPTION_CHANGED,
                    AuditResourceType.SUBSCRIPTION, subscription.getOrganizationId(), Map.of("plan", plan.getPlanKey()));
            announce(subscription, "FREE_PLAN_SELECTED");
            return new CheckoutResponse(view(subscription), null, null);
        }

        String reference = paymentGateway.createPayment(organizationId, plan);
        subscription.startCheckout(plan.getPlanKey(), reference);
        auditService.record(organizationId, callerUserId, AuditAction.SUBSCRIPTION_CHANGED,
                AuditResourceType.SUBSCRIPTION, subscription.getOrganizationId(),
                Map.of("plan", plan.getPlanKey(), "step", "checkout_started"));
        return new CheckoutResponse(view(subscription), reference, plan.getMonthlyPriceCents());
    }

    @Transactional
    public SubscriptionResponse cancel(UUID organizationId, UUID callerUserId) {
        requireBillingPermission(organizationId, callerUserId);
        Subscription subscription = subscriptionRepository
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new AlreadyOnPlanException("Free"));
        if (effectivePlan(subscription).isFree()) {
            throw new AlreadyOnPlanException("Free");
        }

        subscription.cancelAtPeriodEnd();
        auditService.record(organizationId, callerUserId, AuditAction.SUBSCRIPTION_CANCELED,
                AuditResourceType.SUBSCRIPTION, organizationId, Map.of("plan", subscription.getPlanKey()));
        announce(subscription, "CANCEL_SCHEDULED");
        return view(subscription);
    }

    @Transactional(readOnly = true)
    public Page<BillingRecord> history(UUID organizationId, UUID callerUserId, Pageable pageable) {
        requireBillingPermission(organizationId, callerUserId);
        return billingRecordRepository.findAllByOrganizationId(organizationId, pageable);
    }

    /** Called by ProjectService before it creates a project. {@code currentActiveProjects} comes from the caller. */
    @Transactional(readOnly = true)
    public void requireProjectCapacity(UUID organizationId, int currentActiveProjects) {
        Plan plan = currentPlan(organizationId);
        if (plan.getMaxProjects() != null && currentActiveProjects >= plan.getMaxProjects()) {
            throw new PlanLimitReachedException("active projects", plan.getMaxProjects(), plan.getName());
        }
    }

    /** Called by InvitationService; the count is members plus invitations still waiting to be accepted. */
    @Transactional(readOnly = true)
    public void requireMemberCapacity(UUID organizationId, int membersAndPendingInvitations) {
        Plan plan = currentPlan(organizationId);
        if (plan.getMaxMembers() != null && membersAndPendingInvitations >= plan.getMaxMembers()) {
            throw new PlanLimitReachedException("members", plan.getMaxMembers(), plan.getName());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Payment webhook
    // ------------------------------------------------------------------------------------------

    /**
     * Applies one provider event. The caller has already verified the signature.
     *
     * @return what happened, e.g. {@code PLAN_ACTIVATED}, or {@code DUPLICATE} if this event id was
     *     handled before.
     */
    @Transactional
    public String handlePaymentEvent(PaymentWebhookEvent event, String rawBody) {
        // Claim the event id first. If it is already there, this is a redelivery: do nothing at all.
        int claimed = jdbcTemplate.update(
                "INSERT INTO payment_events (provider_event_id, event_type, outcome, payload) "
                        + "VALUES (?, ?, 'PROCESSING', ?) ON CONFLICT (provider_event_id) DO NOTHING",
                event.eventId(), event.type(), rawBody);
        if (claimed == 0) {
            return "DUPLICATE";
        }

        Optional<Subscription> pending = subscriptionRepository.findByPendingPaymentRef(event.paymentReference());
        Optional<Subscription> active = subscriptionRepository.findByPaymentRef(event.paymentReference());
        Instant now = Instant.now();
        String outcome;
        UUID organizationId = null;

        if (pending.isEmpty() && active.isEmpty()) {
            outcome = "IGNORED_UNKNOWN_REFERENCE";
        } else {
            Subscription subscription = pending.orElseGet(active::get);
            organizationId = subscription.getOrganizationId();
            outcome = switch (event.type() == null ? "" : event.type()) {
                case "payment.succeeded" -> onPaymentSucceeded(event, subscription, pending.isPresent(), now);
                case "payment.failed" -> onPaymentFailed(event, subscription, pending.isPresent(), now);
                case "subscription.canceled" -> onSubscriptionCanceled(subscription, pending.isPresent());
                default -> "IGNORED_UNKNOWN_TYPE";
            };
        }

        jdbcTemplate.update(
                "UPDATE payment_events SET outcome = ?, organization_id = ? WHERE provider_event_id = ?",
                outcome, organizationId, event.eventId());
        return outcome;
    }

    private String onPaymentSucceeded(PaymentWebhookEvent event, Subscription subscription, boolean isPending, Instant now) {
        String planKey = isPending ? subscription.getPendingPlanKey() : subscription.getPlanKey();
        Plan plan = planRepository.findById(planKey).orElseThrow(() -> new PlanNotFoundException(planKey));

        // Never trust the amount blindly: a payment for the wrong amount must not unlock the plan.
        if (event.amountCents() == null || event.amountCents() != plan.getMonthlyPriceCents()) {
            return "REJECTED_AMOUNT_MISMATCH";
        }

        if (isPending) {
            subscription.activatePending(now);
        } else {
            subscription.renew(now);
        }
        billingRecordRepository.save(new BillingRecord(
                subscription.getOrganizationId(), plan.getPlanKey(), plan.getMonthlyPriceCents(),
                currencyOf(event), true, "Subscription payment: " + plan.getName(), event.eventId()));
        auditService.record(subscription.getOrganizationId(), null, AuditAction.PAYMENT_SUCCEEDED,
                AuditResourceType.SUBSCRIPTION, subscription.getOrganizationId(), Map.of("plan", plan.getPlanKey()));
        announce(subscription, isPending ? "PLAN_ACTIVATED" : "RENEWED");
        return isPending ? "PLAN_ACTIVATED" : "RENEWED";
    }

    private String onPaymentFailed(PaymentWebhookEvent event, Subscription subscription, boolean isPending, Instant now) {
        String planKey = isPending ? subscription.getPendingPlanKey() : subscription.getPlanKey();
        Plan plan = planRepository.findById(planKey).orElseThrow(() -> new PlanNotFoundException(planKey));

        if (isPending) {
            subscription.startCheckout(null, null); // the first payment failed: the plan never changed
        } else {
            subscription.markPastDue(now); // grace period starts; the paid plan still works for now
        }
        billingRecordRepository.save(new BillingRecord(
                subscription.getOrganizationId(), plan.getPlanKey(), plan.getMonthlyPriceCents(),
                currencyOf(event), false, "Payment failed: " + plan.getName(), event.eventId()));
        auditService.record(subscription.getOrganizationId(), null, AuditAction.PAYMENT_FAILED,
                AuditResourceType.SUBSCRIPTION, subscription.getOrganizationId(), Map.of("plan", plan.getPlanKey()));
        announce(subscription, isPending ? "CHECKOUT_FAILED" : "PAST_DUE");
        return isPending ? "CHECKOUT_FAILED" : "PAST_DUE";
    }

    private String onSubscriptionCanceled(Subscription subscription, boolean isPending) {
        if (isPending) {
            subscription.startCheckout(null, null);
            return "CHECKOUT_CANCELED";
        }
        subscription.cancelNow();
        auditService.record(subscription.getOrganizationId(), null, AuditAction.SUBSCRIPTION_CANCELED,
                AuditResourceType.SUBSCRIPTION, subscription.getOrganizationId(), Map.of("by", "provider"));
        announce(subscription, "CANCELED");
        return "CANCELED";
    }

    // ------------------------------------------------------------------------------------------

    private void requireBillingPermission(UUID organizationId, UUID callerUserId) {
        OrganizationMember membership = organizationService.requireMembership(organizationId, callerUserId);
        permissionChecker.requirePermission(membership.getRole(), MANAGE_BILLING);
    }

    private Plan currentPlan(UUID organizationId) {
        return subscriptionRepository
                .findByOrganizationId(organizationId)
                .map(this::effectivePlan)
                .orElseGet(() -> planRepository.findById(Subscription.FREE).orElseThrow());
    }

    private Plan effectivePlan(Subscription subscription) {
        String key = subscription.effectivePlanKey(Instant.now());
        return planRepository.findById(key).orElseThrow(() -> new PlanNotFoundException(key));
    }

    private SubscriptionResponse view(Subscription subscription) {
        if (subscription == null) {
            return new SubscriptionResponse(
                    PlanResponse.from(planRepository.findById(Subscription.FREE).orElseThrow()),
                    SubscriptionStatus.ACTIVE, null, null, false, null);
        }
        return new SubscriptionResponse(
                PlanResponse.from(effectivePlan(subscription)),
                subscription.getStatus(),
                subscription.getCurrentPeriodEnd(),
                subscription.getGraceEndsAt(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getPendingPlanKey());
    }

    private void announce(Subscription subscription, String change) {
        outboxService.record(
                DomainEventType.SUBSCRIPTION_UPDATED,
                "SUBSCRIPTION",
                subscription.getOrganizationId(),
                subscription.getOrganizationId(),
                Map.of("change", change, "plan", subscription.getPlanKey(), "status", subscription.getStatus().name()));
    }

    private static String currencyOf(PaymentWebhookEvent event) {
        String currency = event.currency();
        return currency == null || currency.length() != 3 ? DEFAULT_CURRENCY : currency.toUpperCase();
    }

    /** Parses the raw webhook body; called by the controller after the signature was verified. */
    PaymentWebhookEvent parse(String rawBody) throws JsonProcessingException {
        PaymentWebhookEvent event = objectMapper.readValue(rawBody, PaymentWebhookEvent.class);
        if (event.eventId() == null || event.eventId().isBlank() || event.eventId().length() > 100
                || event.paymentReference() == null || event.type() == null) {
            throw new IllegalArgumentException("Webhook body is missing required fields");
        }
        return event;
    }
}
