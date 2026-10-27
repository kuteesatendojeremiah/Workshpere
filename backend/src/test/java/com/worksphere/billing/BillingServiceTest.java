package com.worksphere.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.outbox.OutboxService;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.TestRoles;
import com.worksphere.identity.User;
import com.worksphere.organization.Organization;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingRecordRepository billingRecordRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private AuditService auditService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BillingService billingService;
    private final UUID organizationId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        billingService = new BillingService(
                planRepository, subscriptionRepository, billingRecordRepository, organizationService,
                new PermissionChecker(), paymentGateway, auditService, outboxService, jdbcTemplate, new ObjectMapper());
    }

    private Plan plan(String key, int price, Integer maxProjects, Integer maxMembers) {
        Plan plan = new Plan();
        ReflectionTestUtils.setField(plan, "planKey", key);
        ReflectionTestUtils.setField(plan, "name", key);
        ReflectionTestUtils.setField(plan, "monthlyPriceCents", price);
        ReflectionTestUtils.setField(plan, "maxProjects", maxProjects);
        ReflectionTestUtils.setField(plan, "maxMembers", maxMembers);
        return plan;
    }

    private void callerHasRole(String roleName, String... permissions) {
        when(organizationService.requireMembership(organizationId, callerId))
                .thenReturn(new OrganizationMember(
                        new Organization("Acme"), new User("c@example.com", "h", "C"),
                        TestRoles.role(roleName, permissions)));
    }

    @Test
    void membersWithoutManageBillingCannotChangeCancelOrReadBilling() {
        callerHasRole("EMPLOYEE_MEMBER");

        assertThatThrownBy(() -> billingService.changePlan(organizationId, callerId, "PROFESSIONAL"))
                .isInstanceOf(MissingPermissionException.class);
        assertThatThrownBy(() -> billingService.cancel(organizationId, callerId))
                .isInstanceOf(MissingPermissionException.class);
        assertThatThrownBy(() -> billingService.history(organizationId, callerId, PageRequest.of(0, 10)))
                .isInstanceOf(MissingPermissionException.class);

        verify(paymentGateway, never()).createPayment(any(), any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void choosingAPaidPlanStartsAPaymentButDoesNotChangeThePlan() {
        callerHasRole("ORGANIZATION_OWNER", "MANAGE_BILLING");
        Plan free = plan("FREE", 0, 3, 5);
        Plan pro = plan("PROFESSIONAL", 1500, 50, 50);
        Subscription subscription = new Subscription(organizationId);
        when(planRepository.findById("PROFESSIONAL")).thenReturn(Optional.of(pro));
        when(planRepository.findById("FREE")).thenReturn(Optional.of(free));
        when(subscriptionRepository.findByOrganizationId(organizationId)).thenReturn(Optional.of(subscription));
        when(paymentGateway.createPayment(organizationId, pro)).thenReturn("pay_123");

        CheckoutResponse checkout = billingService.changePlan(organizationId, callerId, "PROFESSIONAL");

        assertThat(checkout.paymentReference()).isEqualTo("pay_123");
        assertThat(checkout.amountCents()).isEqualTo(1500);
        assertThat(checkout.subscription().plan().key()).as("still Free until the webhook").isEqualTo("FREE");
        assertThat(checkout.subscription().pendingPlanKey()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void choosingThePlanYouAlreadyHaveIsRefused() {
        callerHasRole("ORGANIZATION_OWNER", "MANAGE_BILLING");
        when(planRepository.findById("FREE")).thenReturn(Optional.of(plan("FREE", 0, 3, 5)));
        when(subscriptionRepository.findByOrganizationId(organizationId))
                .thenReturn(Optional.of(new Subscription(organizationId)));

        assertThatThrownBy(() -> billingService.changePlan(organizationId, callerId, "FREE"))
                .isInstanceOf(AlreadyOnPlanException.class);
    }

    @Test
    void anUnknownPlanIsNotFound() {
        callerHasRole("ORGANIZATION_OWNER", "MANAGE_BILLING");
        when(planRepository.findById("PLATINUM")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.changePlan(organizationId, callerId, "PLATINUM"))
                .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void projectCapacityIsEnforcedFromThePlanLimit() {
        when(subscriptionRepository.findByOrganizationId(organizationId)).thenReturn(Optional.empty());
        when(planRepository.findById("FREE")).thenReturn(Optional.of(plan("FREE", 0, 3, 5)));

        billingService.requireProjectCapacity(organizationId, 2); // room for a third

        assertThatThrownBy(() -> billingService.requireProjectCapacity(organizationId, 3))
                .isInstanceOf(PlanLimitReachedException.class)
                .hasMessageContaining("at most 3");
    }

    @Test
    void anUnlimitedPlanNeverBlocks() {
        Subscription subscription = new Subscription(organizationId);
        subscription.startCheckout("ENTERPRISE", "pay_9");
        subscription.activatePending(java.time.Instant.now());
        when(subscriptionRepository.findByOrganizationId(organizationId)).thenReturn(Optional.of(subscription));
        when(planRepository.findById("ENTERPRISE")).thenReturn(Optional.of(plan("ENTERPRISE", 9900, null, null)));

        billingService.requireProjectCapacity(organizationId, 10_000);
        billingService.requireMemberCapacity(organizationId, 10_000);
    }

    @Test
    void memberCapacityCountsPendingInvitationsToo() {
        when(subscriptionRepository.findByOrganizationId(organizationId)).thenReturn(Optional.empty());
        when(planRepository.findById("FREE")).thenReturn(Optional.of(plan("FREE", 0, 3, 5)));

        assertThatThrownBy(() -> billingService.requireMemberCapacity(organizationId, 5))
                .isInstanceOf(PlanLimitReachedException.class);
    }
}
