package com.worksphere.billing;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Proves a webhook really comes from the payment provider (spec section 20: webhook verification).
 *
 * <p>The provider sends {@code X-Webhook-Signature: t=<unix seconds>,v1=<hex>} where {@code v1} is
 * HMAC-SHA256 of {@code "<t>.<raw body>"} with a secret only the provider and we know.
 *
 * <ul>
 *   <li>Without the secret nobody can forge a payment event.
 *   <li>The timestamp is part of what is signed and must be recent, so a captured genuine request
 *       cannot be replayed later (spec section 29: replay).
 *   <li>The comparison is constant-time, so the signature cannot be guessed byte by byte.
 *   <li>The raw body is what is signed, so it must be verified BEFORE it is parsed.
 * </ul>
 */
@Component
public class WebhookSignatureVerifier {

    private final byte[] secret;
    private final long toleranceSeconds;

    public WebhookSignatureVerifier(
            @Value("${app.billing.webhook-secret}") String secret,
            @Value("${app.billing.webhook-tolerance-seconds:300}") long toleranceSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.toleranceSeconds = toleranceSeconds;
    }

    /** @throws InvalidWebhookSignatureException for anything wrong, without saying what (no oracle for attackers). */
    public void verify(String header, String rawBody) {
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookSignatureException();
        }
        String timestamp = null;
        String signature = null;
        for (String part : header.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals("t")) {
                timestamp = keyValue[1];
            } else if (keyValue.length == 2 && keyValue[0].equals("v1")) {
                signature = keyValue[1];
            }
        }
        if (timestamp == null || signature == null) {
            throw new InvalidWebhookSignatureException();
        }

        long sentAt;
        try {
            sentAt = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new InvalidWebhookSignatureException();
        }
        if (Math.abs(Instant.now().getEpochSecond() - sentAt) > toleranceSeconds) {
            throw new InvalidWebhookSignatureException();
        }

        byte[] expected = sign(sentAt, rawBody).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookSignatureException();
        }
    }

    /** Also used by tests and by the developer simulator to produce a genuine signature. */
    public String sign(long timestampSeconds, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((timestampSeconds + "." + rawBody).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }
}
