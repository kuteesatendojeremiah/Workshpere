package com.worksphere.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives the payment provider's events. It is the only endpoint (besides auth) that needs no user
 * token: the provider is not a user. Its protection is the HMAC signature, checked on the RAW body
 * before anything is parsed.
 */
@RestController
public class PaymentWebhookController {

    private final WebhookSignatureVerifier signatureVerifier;
    private final BillingService billingService;
    private final MeterRegistry meterRegistry;

    public PaymentWebhookController(
            WebhookSignatureVerifier signatureVerifier, BillingService billingService, MeterRegistry meterRegistry) {
        this.signatureVerifier = signatureVerifier;
        this.billingService = billingService;
        this.meterRegistry = meterRegistry;
    }

    @PostMapping("/api/v1/webhooks/payment")
    public Map<String, String> receive(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        try {
            signatureVerifier.verify(signature, rawBody);
        } catch (InvalidWebhookSignatureException e) {
            // A burst of these means someone is probing the endpoint (or the secret was rotated).
            meterRegistry.counter("worksphere.billing.webhook", "outcome", "SIGNATURE_REJECTED").increment();
            throw e;
        }

        PaymentWebhookEvent event;
        try {
            event = billingService.parse(rawBody);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new MalformedWebhookException();
        }
        // 200 for duplicates and for events we ignore: the provider must stop retrying them.
        String outcome = billingService.handlePaymentEvent(event, rawBody);
        meterRegistry.counter("worksphere.billing.webhook", "outcome", outcome).increment();
        return Map.of("status", outcome);
    }
}
