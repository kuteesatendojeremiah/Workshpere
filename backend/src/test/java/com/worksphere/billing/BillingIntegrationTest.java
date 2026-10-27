package com.worksphere.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.AbstractIntegrationTest;
import com.worksphere.identity.JwtService;
import com.worksphere.identity.User;
import com.worksphere.identity.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Plans, limits, the signed payment webhook and its idempotency, against real PostgreSQL. */
@AutoConfigureMockMvc
class BillingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private WebhookSignatureVerifier verifier;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private record Org(User owner, UUID id) {
    }

    private Org newOrg(String tag) throws Exception {
        User owner = userRepository.save(new User(
                "billing-" + tag + "-" + UUID.randomUUID() + "@example.com", passwordEncoder.encode("password123"), "Owner"));
        UUID id = idOf(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Billing " + tag + "\"}"));
        return new Org(owner, id);
    }

    /** Starts checkout for PROFESSIONAL and returns the payment reference. */
    private String startCheckout(Org org, String planKey) throws Exception {
        String body = mockMvc.perform(post("/api/v1/organizations/{o}/subscription", org.id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planKey\": \"" + planKey + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("paymentReference").asText();
    }

    private ResultActions webhook(String eventId, String type, String reference, Integer amount) throws Exception {
        String body = "{\"eventId\":\"" + eventId + "\",\"type\":\"" + type + "\",\"paymentReference\":\"" + reference
                + "\",\"amountCents\":" + amount + ",\"currency\":\"USD\"}";
        return signedWebhook(body, Instant.now().getEpochSecond());
    }

    private ResultActions signedWebhook(String body, long timestamp) throws Exception {
        return mockMvc.perform(post("/api/v1/webhooks/payment")
                .header("X-Webhook-Signature", "t=" + timestamp + ",v1=" + verifier.sign(timestamp, body))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String planOf(Org org) throws Exception {
        String body = mockMvc.perform(get("/api/v1/organizations/{o}/subscription", org.id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("plan").get("key").asText();
    }

    private ResultActions createProject(Org org, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/organizations/{o}/projects", org.id)
                .header(HttpHeaders.AUTHORIZATION, bearer(org.owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"}"));
    }

    // ---------------------------------------------------------------------------------------

    @Test
    void thereAreThreePlansAndEveryNewOrganizationStartsOnFree() throws Exception {
        Org org = newOrg("plans");

        mockMvc.perform(get("/api/v1/plans").header(HttpHeaders.AUTHORIZATION, bearer(org.owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", equalTo(3)))
                .andExpect(jsonPath("$[0].key", equalTo("FREE")))
                .andExpect(jsonPath("$[2].key", equalTo("ENTERPRISE")))
                .andExpect(jsonPath("$[2].maxProjects", nullValue()));
        assertThat(planOf(org)).isEqualTo("FREE");
    }

    @Test
    void theFreePlanLimitsProjectsAndAPaidPlanLiftsTheLimit() throws Exception {
        Org org = newOrg("limits");
        for (int i = 1; i <= 3; i++) {
            createProject(org, "P" + i).andExpect(status().isCreated());
        }
        createProject(org, "P4")
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code", equalTo("PLAN_LIMIT_REACHED")));

        String reference = startCheckout(org, "PROFESSIONAL");
        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", reference, 1500)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("PLAN_ACTIVATED")));

        createProject(org, "P4").andExpect(status().isCreated());
    }

    @Test
    void theFreePlanLimitsMembersIncludingOpenInvitations() throws Exception {
        Org org = newOrg("members");

        // The owner is member 1; four open invitations make 5, which is the Free limit.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/v1/organizations/{o}/invitations", org.id)
                            .header(HttpHeaders.AUTHORIZATION, bearer(org.owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\": \"invitee" + i + "-" + UUID.randomUUID() + "@example.com\"}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/v1/organizations/{o}/invitations", org.id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"one-too-many@example.com\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code", equalTo("PLAN_LIMIT_REACHED")));
    }

    @Test
    void choosingAPaidPlanDoesNotChangeItUntilThePaymentWebhookArrives() throws Exception {
        Org org = newOrg("checkout");

        String reference = startCheckout(org, "PROFESSIONAL");

        assertThat(reference).startsWith("pay_");
        assertThat(planOf(org)).as("not paid yet").isEqualTo("FREE");

        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", reference, 1500).andExpect(status().isOk());

        assertThat(planOf(org)).isEqualTo("PROFESSIONAL");
        mockMvc.perform(get("/api/v1/organizations/{o}/billing-history", org.id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", equalTo(1)))
                .andExpect(jsonPath("$.content[0].status", equalTo("PAID")))
                .andExpect(jsonPath("$.content[0].amountCents", equalTo(1500)));
    }

    @Test
    void aDuplicateWebhookDoesNotDuplicateTheSubscriptionStateOrTheBillingHistory() throws Exception {
        Org org = newOrg("dup");
        String reference = startCheckout(org, "PROFESSIONAL");
        String eventId = "evt_" + UUID.randomUUID();

        webhook(eventId, "payment.succeeded", reference, 1500).andExpect(jsonPath("$.status", equalTo("PLAN_ACTIVATED")));
        Instant periodEnd = jdbc.queryForObject(
                "SELECT current_period_end FROM subscriptions WHERE organization_id = ?", Instant.class, org.id);

        // The provider retries the same event three more times.
        for (int i = 0; i < 3; i++) {
            webhook(eventId, "payment.succeeded", reference, 1500)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", equalTo("DUPLICATE")));
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_history WHERE organization_id = ?", Integer.class, org.id))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_events WHERE provider_event_id = ?", Integer.class, eventId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT current_period_end FROM subscriptions WHERE organization_id = ?", Instant.class, org.id))
                .as("a duplicate must not extend the period")
                .isEqualTo(periodEnd);
    }

    @Test
    void aForgedTamperedOrReplayedWebhookIsRejectedAndChangesNothing() throws Exception {
        Org org = newOrg("forged");
        String reference = startCheckout(org, "PROFESSIONAL");
        String body = "{\"eventId\":\"evt_forged\",\"type\":\"payment.succeeded\",\"paymentReference\":\"" + reference
                + "\",\"amountCents\":1500,\"currency\":\"USD\"}";
        long now = Instant.now().getEpochSecond();

        // No signature at all.
        mockMvc.perform(post("/api/v1/webhooks/payment").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("WEBHOOK_SIGNATURE_INVALID")));
        // A signature made with the wrong secret.
        mockMvc.perform(post("/api/v1/webhooks/payment")
                        .header("X-Webhook-Signature", "t=" + now + ",v1=" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // A genuine signature, but for a different body (tampered in transit).
        mockMvc.perform(post("/api/v1/webhooks/payment")
                        .header("X-Webhook-Signature", "t=" + now + ",v1=" + verifier.sign(now, body.replace("1500", "100")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // A genuine old request replayed an hour later.
        signedWebhook(body, now - 3600).andExpect(status().isUnauthorized());

        assertThat(planOf(org)).isEqualTo("FREE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_events WHERE provider_event_id = 'evt_forged'", Integer.class))
                .isZero();
    }

    @Test
    void aPaymentForTheWrongAmountDoesNotUnlockThePlan() throws Exception {
        Org org = newOrg("amount");
        String reference = startCheckout(org, "PROFESSIONAL");

        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", reference, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("REJECTED_AMOUNT_MISMATCH")));

        assertThat(planOf(org)).isEqualTo("FREE");
    }

    @Test
    void anEventForAnUnknownPaymentIsAcknowledgedAndIgnored() throws Exception {
        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", "pay_does_not_exist", 1500)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("IGNORED_UNKNOWN_REFERENCE")));
    }

    @Test
    void aMalformedBodyWithAValidSignatureIsABadRequest() throws Exception {
        signedWebhook("this is not json", Instant.now().getEpochSecond())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void aFailedRenewalStartsAGracePeriodAndWhenItEndsTheOrganizationIsBackOnFree() throws Exception {
        Org org = newOrg("grace");
        String reference = startCheckout(org, "PROFESSIONAL");
        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", reference, 1500).andExpect(status().isOk());

        webhook("evt_" + UUID.randomUUID(), "payment.failed", reference, 1500)
                .andExpect(jsonPath("$.status", equalTo("PAST_DUE")));

        mockMvc.perform(get("/api/v1/organizations/{o}/subscription", org.id).header(HttpHeaders.AUTHORIZATION, bearer(org.owner)))
                .andExpect(jsonPath("$.status", equalTo("PAST_DUE")))
                .andExpect(jsonPath("$.plan.key", equalTo("PROFESSIONAL")));

        // Time passes: the grace period ends without a successful payment.
        jdbc.update("UPDATE subscriptions SET grace_ends_at = now() - interval '1 hour' WHERE organization_id = ?", org.id);
        assertThat(planOf(org)).isEqualTo("FREE");

        // The customer finally pays: everything is restored.
        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", reference, 1500)
                .andExpect(jsonPath("$.status", equalTo("RENEWED")));
        assertThat(planOf(org)).isEqualTo("PROFESSIONAL");
        assertThat(jdbc.queryForList("SELECT status FROM billing_history WHERE organization_id = ? ORDER BY created_at",
                        String.class, org.id))
                .containsExactly("PAID", "FAILED", "PAID");
    }

    @Test
    void cancellingKeepsThePaidPlanUntilTheEndOfThePeriod() throws Exception {
        Org org = newOrg("cancel");
        String reference = startCheckout(org, "PROFESSIONAL");
        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", reference, 1500).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/organizations/{o}/subscription/cancel", org.id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelAtPeriodEnd", equalTo(true)))
                .andExpect(jsonPath("$.plan.key", equalTo("PROFESSIONAL")));

        jdbc.update("UPDATE subscriptions SET current_period_end = now() - interval '1 day' WHERE organization_id = ?", org.id);
        assertThat(planOf(org)).isEqualTo("FREE");
    }

    @Test
    void anotherOrganizationsOwnerCanNeitherSeeNorChangeBilling() throws Exception {
        Org victim = newOrg("victim");
        Org attacker = newOrg("attacker");
        String attackerAuth = bearer(attacker.owner);

        mockMvc.perform(get("/api/v1/organizations/{o}/subscription", victim.id).header(HttpHeaders.AUTHORIZATION, attackerAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/organizations/{o}/subscription", victim.id)
                        .header(HttpHeaders.AUTHORIZATION, attackerAuth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planKey\": \"ENTERPRISE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/organizations/{o}/subscription/cancel", victim.id).header(HttpHeaders.AUTHORIZATION, attackerAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/organizations/{o}/billing-history", victim.id).header(HttpHeaders.AUTHORIZATION, attackerAuth))
                .andExpect(status().isNotFound());

        assertThat(planOf(victim)).isEqualTo("FREE");
        // And billing needs a signed-in user.
        mockMvc.perform(get("/api/v1/organizations/{o}/subscription", victim.id)).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownPlanIsRejectedAndSubscriptionChangesAreAudited() throws Exception {
        Org org = newOrg("audit");

        mockMvc.perform(post("/api/v1/organizations/{o}/subscription", org.id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planKey\": \"PLATINUM\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PLAN_NOT_FOUND")));
        mockMvc.perform(post("/api/v1/organizations/{o}/subscription", org.id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"planKey\": \"\"}"))
                .andExpect(status().isBadRequest());

        String reference = startCheckout(org, "PROFESSIONAL");
        webhook("evt_" + UUID.randomUUID(), "payment.succeeded", reference, 1500).andExpect(status().isOk());

        assertThat(jdbc.queryForList(
                        "SELECT action FROM audit_logs WHERE organization_id = ? AND action IN ('SUBSCRIPTION_CHANGED','PAYMENT_SUCCEEDED') "
                                + "ORDER BY created_at",
                        String.class, org.id))
                .containsExactly("SUBSCRIPTION_CHANGED", "PAYMENT_SUCCEEDED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_events WHERE organization_id = ? AND event_type = 'SUBSCRIPTION_UPDATED'",
                        Integer.class, org.id))
                .isEqualTo(1);
    }

    private UUID idOf(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId());
    }
}
