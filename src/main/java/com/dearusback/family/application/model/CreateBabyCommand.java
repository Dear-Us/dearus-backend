package com.dearusback.family.application.model;

public record CreateBabyCommand(Long memberId, String babyName, String birthDate, String gender,
                                BabyImageUpload profileImage, String profileImageUrl) {
    public CreateBabyCommand(Long memberId, String babyName, String birthDate, String gender,
                             BabyImageUpload profileImage) {
        this(memberId, babyName, birthDate, gender, profileImage, null);
    }
}
