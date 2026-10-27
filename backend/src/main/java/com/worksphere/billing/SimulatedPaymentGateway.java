package com.worksphere.billing;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A stand-in provider for development and learning: it just invents a payment reference. "Paying"
 * is simulated by sending a signed webhook to {@code POST /api/v1/webhooks/payment} yourself (see
 * docs/api/billing.md), which is exactly what a real provider would do after the customer pays.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public String createPayment(UUID organizationId, Plan plan) {
        return "pay_" + UUID.randomUUID().toString().replace("-", "");
    }
}
