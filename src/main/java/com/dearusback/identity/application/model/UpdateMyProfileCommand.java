package com.dearusback.identity.application.model;

public record UpdateMyProfileCommand(
        Long memberId,
        String memberName,
        boolean removeProfileImage,
        ProfileImageUpload profileImage
) {
}
