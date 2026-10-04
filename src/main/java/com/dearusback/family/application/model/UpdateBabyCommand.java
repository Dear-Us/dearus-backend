package com.dearusback.family.application.model;

public record UpdateBabyCommand(Long memberId, Long babyId, String babyName, String birthDate,
                                String gender, boolean removeProfileImage, BabyImageUpload profileImage) {
}
