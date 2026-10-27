package com.worksphere.billing;

import java.time.Instant;
import java.util.UUID;

public record BillingRecordResponse(
        UUID id, String planKey, int amountCents, String currency, String status, String description, Instant createdAt) {

    public static BillingRecordResponse from(BillingRecord record) {
        return new BillingRecordResponse(
                record.getId(), record.getPlanKey(), record.getAmountCents(), record.getCurrency(), record.getStatus(),
                record.getDescription(), record.getCreatedAt());
    }
}
