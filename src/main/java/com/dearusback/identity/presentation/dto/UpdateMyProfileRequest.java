package com.dearusback.identity.presentation.dto;

public record UpdateMyProfileRequest(String memberName, Boolean removeProfileImage) {

    public boolean shouldRemoveProfileImage() {
        return Boolean.TRUE.equals(removeProfileImage);
    }
}
