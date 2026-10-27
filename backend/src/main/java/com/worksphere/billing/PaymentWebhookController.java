package com.worksphere.billing;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    public PaymentWebhookController(WebhookSignatureVerifier signatureVerifier, BillingService billingService) {
        this.signatureVerifier = signatureVerifier;
        this.billingService = billingService;
    }

    @PostMapping("/api/v1/webhooks/payment")
    public Map<String, String> receive(
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        signatureVerifier.verify(signature, rawBody);

        PaymentWebhookEvent event;
        try {
            event = billingService.parse(rawBody);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new MalformedWebhookException();
        }
        // 200 for duplicates and for events we ignore: the provider must stop retrying them.
        return Map.of("status", billingService.handlePaymentEvent(event, rawBody));
    }
}
