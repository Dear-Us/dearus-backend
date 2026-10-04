package com.dearusback.family.application.model;

public record CreateBabyCommand(Long memberId, String babyName, String birthDate, String gender,
                                BabyImageUpload profileImage) {
}
