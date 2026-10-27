package com.worksphere.billing;

import jakarta.validation.constraints.NotBlank;

public record ChangePlanRequest(@NotBlank(message = "planKey must not be blank") String planKey) {
}
