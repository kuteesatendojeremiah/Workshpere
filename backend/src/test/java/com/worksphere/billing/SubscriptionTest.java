package com.worksphere.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Which plan is in force is derived from state and time; these tests walk through the lifecycle. */
class SubscriptionTest {

    private final Instant now = Instant.parse("2026-06-01T00:00:00Z");

    private Subscription paidSubscription() {
        Subscription subscription = new Subscription(UUID.randomUUID());
        subscription.startCheckout("PROFESSIONAL", "pay_1");
        subscription.activatePending(now);
        return subscription;
    }

    @Test
    void newSubscriptionsAreOnTheFreePlan() {
        assertThat(new Subscription(UUID.randomUUID()).effectivePlanKey(now)).isEqualTo("FREE");
    }

    @Test
    void aChosenPlanIsNotInForceUntilItIsPaidFor() {
        Subscription subscription = new Subscription(UUID.randomUUID());

        subscription.startCheckout("PROFESSIONAL", "pay_1");

        assertThat(subscription.effectivePlanKey(now)).isEqualTo("FREE");
        assertThat(subscription.getPendingPlanKey()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void paymentActivatesThePendingPlanForOneBillingPeriod() {
        Subscription subscription = paidSubscription();

        assertThat(subscription.effectivePlanKey(now)).isEqualTo("PROFESSIONAL");
        assertThat(subscription.getPendingPlanKey()).isNull();
        assertThat(subscription.getPaymentRef()).isEqualTo("pay_1");
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(now.plus(Subscription.BILLING_PERIOD));
    }

    @Test
    void aFailedPaymentStartsAGracePeriodDuringWhichThePaidPlanKeepsWorking() {
        Subscription subscription = paidSubscription();

        subscription.markPastDue(now);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.effectivePlanKey(now.plus(Duration.ofDays(6)))).isEqualTo("PROFESSIONAL");
        assertThat(subscription.effectivePlanKey(now.plus(Duration.ofDays(8)))).as("grace is over").isEqualTo("FREE");
    }

    @Test
    void aSuccessfulRenewalClearsPastDue() {
        Subscription subscription = paidSubscription();
        subscription.markPastDue(now);

        subscription.renew(now.plus(Duration.ofDays(2)));

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getGraceEndsAt()).isNull();
        assertThat(subscription.effectivePlanKey(now.plus(Duration.ofDays(20)))).isEqualTo("PROFESSIONAL");
    }

    @Test
    void cancellingAtPeriodEndKeepsThePlanUntilThePeriodIsOver() {
        Subscription subscription = paidSubscription();

        subscription.cancelAtPeriodEnd();

        assertThat(subscription.effectivePlanKey(now.plus(Duration.ofDays(29)))).isEqualTo("PROFESSIONAL");
        assertThat(subscription.effectivePlanKey(now.plus(Duration.ofDays(31)))).isEqualTo("FREE");
    }

    @Test
    void anImmediateCancellationDropsToFree() {
        Subscription subscription = paidSubscription();

        subscription.cancelNow();

        assertThat(subscription.effectivePlanKey(now)).isEqualTo("FREE");
    }

    @Test
    void theFreePlanCanNeverBecomePastDue() {
        Subscription subscription = new Subscription(UUID.randomUUID());

        subscription.markPastDue(now);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void switchingToFreeClearsEverythingPaid() {
        Subscription subscription = paidSubscription();

        subscription.switchToFree();

        assertThat(subscription.effectivePlanKey(now)).isEqualTo("FREE");
        assertThat(subscription.getPaymentRef()).isNull();
        assertThat(subscription.getCurrentPeriodEnd()).isNull();
    }
}
