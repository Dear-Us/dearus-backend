package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.CreateBabyResult;
import java.time.LocalDate;

public record CreateBabyResponse(Long babyId, String babyName, LocalDate birthDate, String gender,
                                 String profileImageUrl, String myRole) {

    public static CreateBabyResponse from(CreateBabyResult result) {
        return new CreateBabyResponse(result.babyId(), result.babyName(), result.birthDate(),
                result.gender().name(), result.profileImageUrl(), result.myRole().name());
    }
}
