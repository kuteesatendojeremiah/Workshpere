package com.worksphere.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A purchasable plan. Seeded by migration; read-only to the application. {@code null} limit = unlimited. */
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @Column(name = "plan_key")
    private String planKey;

    @Column(nullable = false)
    private String name;

    @Column(name = "monthly_price_cents", nullable = false)
    private int monthlyPriceCents;

    @Column(name = "max_projects")
    private Integer maxProjects;

    @Column(name = "max_members")
    private Integer maxMembers;

    protected Plan() {
    }

    public String getPlanKey() {
        return planKey;
    }

    public String getName() {
        return name;
    }

    public int getMonthlyPriceCents() {
        return monthlyPriceCents;
    }

    public Integer getMaxProjects() {
        return maxProjects;
    }

    public Integer getMaxMembers() {
        return maxMembers;
    }

    public boolean isFree() {
        return monthlyPriceCents == 0;
    }
}
