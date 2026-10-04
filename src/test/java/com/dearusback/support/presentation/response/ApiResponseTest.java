package com.dearusback.support.presentation.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearusback.support.application.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void createsSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("data");
        assertThat(response.message()).isNull();
        assertThat(response.errorCode()).isNull();
    }

    @Test
    void createsFailureResponseFromErrorCode() {
        ApiResponse<Void> response = ApiResponse.failure(CommonErrorCode.INVALID_REQUEST);

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo(CommonErrorCode.INVALID_REQUEST.message());
        assertThat(response.errorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST.code());
    }
}
