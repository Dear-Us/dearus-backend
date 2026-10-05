package com.dearusback.insight.presentation.rest;

import com.dearusback.insight.application.port.in.GetHomeUseCase;
import com.dearusback.insight.presentation.dto.HomeResponse;
import com.dearusback.support.application.exception.CommonErrorCode;
import com.dearusback.support.domain.exception.BusinessException;
import com.dearusback.support.presentation.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final GetHomeUseCase homes;

    public HomeController(GetHomeUseCase homes) {
        this.homes = homes;
    }

    @GetMapping("/api/v1/babies/{babyId}/home")
    public ResponseEntity<ApiResponse<HomeResponse>> get(@AuthenticationPrincipal Long memberId,
                                                         @PathVariable Long babyId) {
        if (babyId == null || babyId <= 0) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        return ResponseEntity.ok(ApiResponse.success(HomeResponse.from(homes.get(memberId, babyId))));
    }
}
