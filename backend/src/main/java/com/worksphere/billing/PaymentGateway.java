package com.worksphere.billing;

import java.util.UUID;

/**
 * The payment provider, behind an interface so billing logic never depends on one vendor. A real
 * provider (Stripe, Paddle, ...) would create a hosted checkout here and later call our webhook; the
 * only things the rest of billing needs from it are a reference to the payment and, later, events that
 * quote the same reference.
 */
public interface PaymentGateway {

    /** Starts a payment for a plan and returns the provider's reference for it. */
    String createPayment(UUID organizationId, Plan plan);
}
