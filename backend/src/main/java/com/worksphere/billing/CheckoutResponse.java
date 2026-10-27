package com.worksphere.billing;

/**
 * Returned when a paid plan is chosen. The plan does NOT change yet: it changes when the provider's
 * "payment succeeded" webhook for {@code paymentReference} arrives. For the Free plan
 * {@code paymentReference} is null and the change is immediate.
 */
public record CheckoutResponse(SubscriptionResponse subscription, String paymentReference, Integer amountCents) {
}
