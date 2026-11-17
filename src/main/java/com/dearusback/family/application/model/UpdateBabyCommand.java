package com.dearusback.family.application.model;

public record UpdateBabyCommand(Long memberId, Long babyId, String babyName, String birthDate,
                                String gender, boolean removeProfileImage, BabyImageUpload profileImage,
                                String profileImageUrl) {
    public UpdateBabyCommand(Long memberId, Long babyId, String babyName, String birthDate,
                             String gender, boolean removeProfileImage, BabyImageUpload profileImage) {
        this(memberId, babyId, babyName, birthDate, gender, removeProfileImage, profileImage, null);
    }
}
