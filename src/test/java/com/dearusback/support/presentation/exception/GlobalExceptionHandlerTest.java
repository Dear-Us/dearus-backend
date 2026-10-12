package com.dearusback.support.presentation.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearusback.support.presentation.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    @Test
    void databaseLockConflictUsesCommonConflictEnvelope() {
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleConcurrentRequest(new CannotAcquireLockException("deadlock"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().message()).isNotBlank();
    }
}
