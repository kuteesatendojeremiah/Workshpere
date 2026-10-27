package com.worksphere.billing;

/** {@code null} limits mean unlimited. */
public record PlanResponse(String key, String name, int monthlyPriceCents, Integer maxProjects, Integer maxMembers) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getPlanKey(), plan.getName(), plan.getMonthlyPriceCents(), plan.getMaxProjects(), plan.getMaxMembers());
    }
}
