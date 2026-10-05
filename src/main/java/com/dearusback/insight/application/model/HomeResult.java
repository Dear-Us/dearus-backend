package com.dearusback.insight.application.model;

import com.dearusback.care.application.model.CareHomeSnapshot;
import com.dearusback.family.application.model.MyBabySummary;
import java.time.LocalDate;

public record HomeResult(MyBabySummary baby, LocalDate date, CareHomeSnapshot care) {
}
