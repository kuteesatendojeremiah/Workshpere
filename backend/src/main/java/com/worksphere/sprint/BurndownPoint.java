package com.worksphere.sprint;

import java.time.LocalDate;

public record BurndownPoint(LocalDate date, int remainingPoints) {
}
