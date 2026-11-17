package com.dearusback.family.presentation.dto;

import com.dearusback.family.application.model.CreateBabyResult;
import com.dearusback.family.application.model.MyBabySummary;
import java.time.LocalDate;
import java.util.function.Function;

public record BabyResponse(Long babyId, String babyName, LocalDate birthDate, String gender,
                           String profileImageUrl, String myRole) {

    public static BabyResponse from(CreateBabyResult result, Function<String, String> imageUrlReader) {
        return new BabyResponse(result.babyId(), result.babyName(), result.birthDate(),
                result.gender().name(), imageUrlReader.apply(result.profileImageUrl()), result.myRole().name());
    }

    public static BabyResponse from(MyBabySummary summary, Function<String, String> imageUrlReader) {
        return new BabyResponse(summary.babyId(), summary.babyName(), summary.birthDate(),
                summary.gender().name(), imageUrlReader.apply(summary.profileImageUrl()), summary.myRole().name());
    }
}
