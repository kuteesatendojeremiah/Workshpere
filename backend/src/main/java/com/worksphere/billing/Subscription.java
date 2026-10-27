package com.worksphere.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The billing state of one organization. Which plan is actually in force is NOT stored; it is
 * computed from this row and the clock by {@link #effectivePlanKey(Instant)}, so a grace period or a
 * cancellation takes effect at the right moment without any scheduled job.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    static final Duration BILLING_PERIOD = Duration.ofDays(30);
    static final Duration GRACE_PERIOD = Duration.ofDays(7);
    static final String FREE = "FREE";

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "plan_key", nullable = false)
    private String planKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "pending_plan_key")
    private String pendingPlanKey;

    @Column(name = "pending_payment_ref")
    private String pendingPaymentRef;

    @Column(name = "payment_ref")
    private String paymentRef;

    @Version
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
    }

    public Subscription(UUID organizationId) {
        this.organizationId = organizationId;
        this.planKey = FREE;
        this.status = SubscriptionStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** The plan in force at {@code now}: a lapsed grace period, an ended cancellation or a CANCELED row means Free. */
    public String effectivePlanKey(Instant now) {
        if (FREE.equals(planKey) || status == SubscriptionStatus.CANCELED) {
            return FREE;
        }
        if (status == SubscriptionStatus.PAST_DUE && (graceEndsAt == null || now.isAfter(graceEndsAt))) {
            return FREE;
        }
        if (cancelAtPeriodEnd && currentPeriodEnd != null && now.isAfter(currentPeriodEnd)) {
            return FREE;
        }
        return planKey;
    }

    /** The customer picked a paid plan; it takes effect only when the matching payment succeeds. */
    public void startCheckout(String newPlanKey, String paymentReference) {
        this.pendingPlanKey = newPlanKey;
        this.pendingPaymentRef = paymentReference;
        touch();
    }

    /** Moves straight to the Free plan (no payment needed). */
    public void switchToFree() {
        this.planKey = FREE;
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodEnd = null;
        this.graceEndsAt = null;
        this.cancelAtPeriodEnd = false;
        this.paymentRef = null;
        this.pendingPlanKey = null;
        this.pendingPaymentRef = null;
        touch();
    }

    /** A payment for the pending plan succeeded: that plan becomes active for a new period. */
    public void activatePending(Instant now) {
        this.planKey = pendingPlanKey;
        this.paymentRef = pendingPaymentRef;
        this.pendingPlanKey = null;
        this.pendingPaymentRef = null;
        renew(now);
    }

    /** A renewal payment succeeded: extend the period and clear any past-due state. */
    public void renew(Instant now) {
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodEnd = now.plus(BILLING_PERIOD);
        this.graceEndsAt = null;
        this.cancelAtPeriodEnd = false;
        touch();
    }

    public void markPastDue(Instant now) {
        if (status == SubscriptionStatus.ACTIVE && !FREE.equals(planKey)) {
            this.status = SubscriptionStatus.PAST_DUE;
            this.graceEndsAt = now.plus(GRACE_PERIOD);
            touch();
        }
    }

    public void cancelNow() {
        this.status = SubscriptionStatus.CANCELED;
        this.graceEndsAt = null;
        touch();
    }

    public void cancelAtPeriodEnd() {
        this.cancelAtPeriodEnd = true;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getPlanKey() {
        return planKey;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getGraceEndsAt() {
        return graceEndsAt;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public String getPendingPlanKey() {
        return pendingPlanKey;
    }

    public String getPendingPaymentRef() {
        return pendingPaymentRef;
    }

    public String getPaymentRef() {
        return paymentRef;
    }
}
