package com.dearusback.identity.application.model;

public record UpdateMyProfileCommand(
        Long memberId,
        String memberName,
        boolean removeProfileImage,
        ProfileImageUpload profileImage,
        String profileImageUrl
) {
    public UpdateMyProfileCommand(Long memberId, String memberName, boolean removeProfileImage,
                                  ProfileImageUpload profileImage) {
        this(memberId, memberName, removeProfileImage, profileImage, null);
    }
}
