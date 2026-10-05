package com.dearusback.care.application.port.in;

import com.dearusback.care.application.model.CalendarDayCount;
import com.dearusback.care.application.model.CareRecordView;
import com.dearusback.care.domain.model.CareRecord.CareRecordType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public interface CareRecordQueryUseCase {
    List<CareRecordView> list(Long memberId, Long babyId, LocalDate date, CareRecordType type);

    List<CalendarDayCount> calendar(Long memberId, Long babyId, YearMonth month);

    CareRecordView detail(Long memberId, Long babyId, Long recordId);

}
