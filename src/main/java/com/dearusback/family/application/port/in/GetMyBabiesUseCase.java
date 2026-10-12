package com.dearusback.family.application.port.in;

import com.dearusback.family.application.model.MyBabySummary;
import java.util.List;

public interface GetMyBabiesUseCase {
    List<MyBabySummary> getMyBabies(Long memberId);
}
