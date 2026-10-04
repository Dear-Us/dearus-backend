package com.dearusback.identity.presentation.rest;

import com.dearusback.identity.application.port.in.GetMyProfileQuery;
import com.dearusback.identity.presentation.dto.MyProfileResponse;
import com.dearusback.support.presentation.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final GetMyProfileQuery getMyProfileQuery;

    public MemberController(GetMyProfileQuery getMyProfileQuery) {
        this.getMyProfileQuery = getMyProfileQuery;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyProfileResponse>> me(@AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.success(MyProfileResponse.from(
                getMyProfileQuery.getMyProfile(memberId)
        )));
    }
}
