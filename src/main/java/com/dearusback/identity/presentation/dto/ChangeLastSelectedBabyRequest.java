package com.dearusback.identity.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChangeLastSelectedBabyRequest(@NotNull @Positive Long babyId) {
}
