package com.worksphere.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** One line of billing history: a payment that succeeded or failed. Append-only. */
@Entity
@Table(name = "billing_history")
public class BillingRecord {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "plan_key", nullable = false, updatable = false)
    private String planKey;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private int amountCents;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String status;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(name = "provider_event_id", nullable = false, updatable = false)
    private String providerEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BillingRecord() {
    }

    public BillingRecord(
            UUID organizationId, String planKey, int amountCents, String currency, boolean paid, String description,
            String providerEventId) {
        this.organizationId = organizationId;
        this.planKey = planKey;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = paid ? "PAID" : "FAILED";
        this.description = description;
        this.providerEventId = providerEventId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getPlanKey() {
        return planKey;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
