package com.worksphere.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The provider-neutral webhook body. {@code eventId} is the provider's unique id for the delivery. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentWebhookEvent(
        String eventId, String type, String paymentReference, Integer amountCents, String currency) {
}
