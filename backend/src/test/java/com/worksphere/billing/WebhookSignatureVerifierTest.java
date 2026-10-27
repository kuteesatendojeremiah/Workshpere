package com.worksphere.billing;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("test-secret", 300);
    private static final String BODY = "{\"eventId\":\"evt_1\",\"type\":\"payment.succeeded\"}";

    private String header(long timestamp, String body) {
        return "t=" + timestamp + ",v1=" + verifier.sign(timestamp, body);
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    @Test
    void acceptsAGenuineRecentSignature() {
        assertThatCode(() -> verifier.verify(header(now(), BODY), BODY)).doesNotThrowAnyException();
    }

    @Test
    void rejectsABodyThatWasChangedAfterSigning() {
        String header = header(now(), BODY);

        assertThatThrownBy(() -> verifier.verify(header, BODY.replace("evt_1", "evt_2")))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() {
        long t = now();
        String forged = "t=" + t + ",v1=" + new WebhookSignatureVerifier("attacker-secret", 300).sign(t, BODY);

        assertThatThrownBy(() -> verifier.verify(forged, BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsAReplayedOldRequestEvenWithAGenuineSignature() {
        String old = header(now() - 3600, BODY);

        assertThatThrownBy(() -> verifier.verify(old, BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsATimestampFromTheFuture() {
        String future = header(now() + 3600, BODY);

        assertThatThrownBy(() -> verifier.verify(future, BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        assertThatThrownBy(() -> verifier.verify(null, BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("", BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("garbage", BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("t=abc,v1=ff", BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("t=" + now(), BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
        assertThatThrownBy(() -> verifier.verify("v1=ff", BODY)).isInstanceOf(InvalidWebhookSignatureException.class);
    }
}
