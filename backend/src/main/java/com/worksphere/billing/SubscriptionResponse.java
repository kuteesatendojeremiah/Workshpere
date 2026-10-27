package com.worksphere.billing;

import java.time.Instant;

/** The plan IN FORCE right now (after grace periods and cancellations) plus the raw billing state. */
public record SubscriptionResponse(
        PlanResponse plan,
        SubscriptionStatus status,
        Instant currentPeriodEnd,
        Instant graceEndsAt,
        boolean cancelAtPeriodEnd,
        String pendingPlanKey) {
}
