package com.worksphere.billing;

import com.worksphere.common.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/api/v1/plans")
    public List<PlanResponse> plans() {
        return billingService.listPlans().stream().map(PlanResponse::from).toList();
    }

    @GetMapping("/api/v1/organizations/{organizationId}/subscription")
    public SubscriptionResponse subscription(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerUserId) {
        return billingService.getSubscription(organizationId, callerUserId);
    }

    @PostMapping("/api/v1/organizations/{organizationId}/subscription")
    public CheckoutResponse changePlan(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody ChangePlanRequest request) {
        return billingService.changePlan(organizationId, callerUserId, request.planKey());
    }

    @PostMapping("/api/v1/organizations/{organizationId}/subscription/cancel")
    public SubscriptionResponse cancel(@PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerUserId) {
        return billingService.cancel(organizationId, callerUserId);
    }

    @GetMapping("/api/v1/organizations/{organizationId}/billing-history")
    public PageResponse<BillingRecordResponse> history(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(
                billingService.history(organizationId, callerUserId, pageable).map(BillingRecordResponse::from));
    }
}
