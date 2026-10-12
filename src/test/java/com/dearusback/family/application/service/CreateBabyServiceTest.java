package com.dearusback.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearusback.family.application.model.CreateBabyCommand;
import com.dearusback.family.application.model.CreateBabyResult;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import com.dearusback.support.domain.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CreateBabyServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T15:30:00Z"), ZoneOffset.UTC);
    private final BabyRegistrationMemberAccess members = mock(BabyRegistrationMemberAccess.class);
    private final BabyProfileImageStoragePort storage = mock(BabyProfileImageStoragePort.class);
    private final CreateBabyTransactionService transaction = mock(CreateBabyTransactionService.class);
    private final CreateBabyService service = new CreateBabyService(members, storage, transaction, CLOCK);

    @Test
    void acceptsTodayInMembersTimezoneEvenWhenUtcDateIsYesterday() {
        when(members.requireActiveMember(1L)).thenReturn(new BabyRegistrationMember(1L, "Asia/Seoul"));
        CreateBabyResult expected = new CreateBabyResult(7L, "아기", LocalDate.of(2026, 8, 16),
                BabyGender.MALE, null, BabyMemberRole.OWNER);
        when(transaction.create(1L, "아기", LocalDate.of(2026, 8, 16), BabyGender.MALE, null))
                .thenReturn(expected);

        assertThat(service.create(new CreateBabyCommand(1L, " 아기 ", "2026-08-16", "MALE", null)))
                .isEqualTo(expected);
        verify(transaction).create(1L, "아기", LocalDate.of(2026, 8, 16), BabyGender.MALE, null);
        verify(storage, never()).store(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sameDateIsFutureForUtcMemberAndNeverUploads() {
        when(members.requireActiveMember(1L)).thenReturn(new BabyRegistrationMember(1L, "UTC"));
        assertThatThrownBy(() -> service.create(new CreateBabyCommand(1L, "아기", "2026-08-16", "MALE", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(FamilyErrorCode.INVALID_BIRTH_DATE));
        verify(storage, never()).store(org.mockito.ArgumentMatchers.any());
    }
}
