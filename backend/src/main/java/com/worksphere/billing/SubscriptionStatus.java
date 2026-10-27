package com.worksphere.billing;

public enum SubscriptionStatus {
    /** Paid up (or on a free plan). */
    ACTIVE,
    /** The last payment failed. The paid plan still works until the grace period ends. */
    PAST_DUE,
    /** Ended. The organization is back on the Free plan. */
    CANCELED
}
