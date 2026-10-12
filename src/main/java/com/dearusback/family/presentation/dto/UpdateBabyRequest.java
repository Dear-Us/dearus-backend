package com.dearusback.family.presentation.dto;

public record UpdateBabyRequest(String babyName, String birthDate, String gender, Boolean removeProfileImage) {
    public boolean shouldRemoveProfileImage() {
        return Boolean.TRUE.equals(removeProfileImage);
    }
}
