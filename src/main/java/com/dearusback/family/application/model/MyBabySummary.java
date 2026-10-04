package com.dearusback.family.application.model;

import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMemberRole;
import java.time.LocalDate;

public record MyBabySummary(Long babyId, String babyName, LocalDate birthDate, BabyGender gender,
                            String profileImageUrl, BabyMemberRole myRole) {
}
