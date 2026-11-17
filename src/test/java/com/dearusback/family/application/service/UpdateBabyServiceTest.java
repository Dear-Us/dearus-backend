package com.dearusback.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearusback.family.application.model.BabyImageUpload;
import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.model.UpdateBabyCommand;
import com.dearusback.family.application.port.out.BabyProfileImageStoragePort;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.domain.exception.FamilyErrorCode;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.domain.repository.BabyRepository;
import com.dearusback.identity.application.model.BabyRegistrationMember;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import com.dearusback.support.domain.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UpdateBabyServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-15T15:30:00Z"), ZoneOffset.UTC);
    private final BabyRegistrationMemberAccess members = mock(BabyRegistrationMemberAccess.class);
    private final BabyQueryPort babies = mock(BabyQueryPort.class);
    private final BabyOwnerGuard owners = new BabyOwnerGuard(babies, mock(BabyRepository.class));
    private final BabyProfileImageStoragePort storage = mock(BabyProfileImageStoragePort.class);
    private final UpdateBabyTransactionService transaction = mock(UpdateBabyTransactionService.class);
    private final UpdateBabyService service = new UpdateBabyService(members, owners, storage, transaction, CLOCK);

    @Test
    void acceptsMembersLocalTodayAcrossUtcMidnight() {
        when(members.requireActiveMember(1L)).thenReturn(new BabyRegistrationMember(1L, "Asia/Seoul"));
        MyBabySummary baby = summary(BabyMemberRole.OWNER);
        when(babies.findAccessibleBaby(1L, 2L)).thenReturn(Optional.of(baby));
        MyBabySummary updated = new MyBabySummary(2L, "도윤", LocalDate.of(2026, 8, 16),
                BabyGender.MALE, null, BabyMemberRole.OWNER);
        when(transaction.update(1L, 2L, null, LocalDate.of(2026, 8, 16), null, false, null))
                .thenReturn(new UpdateBabyTransactionService.UpdatedBaby(updated, null));

        assertThat(service.update(new UpdateBabyCommand(1L, 2L, null, "2026-08-16", null, false, null)))
                .isEqualTo(updated);
        verify(transaction).update(1L, 2L, null, LocalDate.of(2026, 8, 16), null, false, null);
    }

    @Test
    void rejectsFutureDateForUtcMemberBeforeStorageOrTransaction() {
        when(members.requireActiveMember(1L)).thenReturn(new BabyRegistrationMember(1L, "UTC"));
        when(babies.findAccessibleBaby(1L, 2L)).thenReturn(Optional.of(summary(BabyMemberRole.OWNER)));
        assertThatThrownBy(() -> service.update(new UpdateBabyCommand(1L, 2L, null,
                "2026-08-16", null, false, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(FamilyErrorCode.INVALID_BIRTH_DATE));
        verify(storage, never()).store(any());
        verify(transaction, never()).update(any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void caregiverIsRejectedBeforeImageUpload() {
        when(members.requireActiveMember(1L)).thenReturn(new BabyRegistrationMember(1L, "Asia/Seoul"));
        when(babies.findAccessibleBaby(1L, 2L)).thenReturn(Optional.of(summary(BabyMemberRole.CAREGIVER)));
        BabyImageUpload upload = BabyImageUpload.validate(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                "image/jpeg");
        assertThatThrownBy(() -> service.update(new UpdateBabyCommand(1L, 2L, null, null, null, false, upload)))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).errorCode())
                        .isEqualTo(FamilyErrorCode.BABY_UPDATE_FORBIDDEN));
        verify(storage, never()).store(any());
    }

    @Test
    void externalUrlIsPassedToTransactionWithoutUploading() {
        when(members.requireActiveMember(1L)).thenReturn(new BabyRegistrationMember(1L, "Asia/Seoul"));
        when(babies.findAccessibleBaby(1L, 2L)).thenReturn(Optional.of(summary(BabyMemberRole.OWNER)));
        String url = "https://cdn.example.com/baby.png";
        MyBabySummary updated = new MyBabySummary(2L, "도윤", LocalDate.of(2020, 1, 1),
                BabyGender.MALE, url, BabyMemberRole.OWNER);
        when(transaction.update(1L, 2L, null, null, null, false, url))
                .thenReturn(new UpdateBabyTransactionService.UpdatedBaby(updated, null));

        assertThat(service.update(new UpdateBabyCommand(1L, 2L, null, null, null, false, null, url)))
                .isEqualTo(updated);
        verify(storage, never()).store(any());
    }

    private MyBabySummary summary(BabyMemberRole role) {
        return new MyBabySummary(2L, "도윤", LocalDate.of(2020, 1, 1), BabyGender.MALE, null, role);
    }
}
