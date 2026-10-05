package com.dearusback.family.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.dearusback.family.application.model.BabyMembershipView;
import com.dearusback.family.application.model.MemberSummary;
import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.application.port.out.BabyQueryPort;
import com.dearusback.family.application.port.out.MemberSummaryPort;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.identity.application.port.in.BabyRegistrationMemberAccess;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GetBabyMembersServiceTest {

    @Test
    void loadsAllIdentitySummariesInOneBatch() {
        BabyRegistrationMemberAccess requester = mock(BabyRegistrationMemberAccess.class);
        BabyQueryPort babies = mock(BabyQueryPort.class);
        MemberSummaryPort summaries = mock(MemberSummaryPort.class);
        when(babies.findAccessibleBaby(1L, 10L)).thenReturn(Optional.of(new MyBabySummary(
                10L, "아기", LocalDate.of(2020, 1, 1), BabyGender.MALE, null, BabyMemberRole.OWNER)));
        Instant joined = Instant.parse("2020-01-01T00:00:00Z");
        when(babies.findActiveMembers(10L)).thenReturn(List.of(
                new BabyMembershipView(3L, BabyMemberRole.CAREGIVER, joined),
                new BabyMembershipView(1L, BabyMemberRole.OWNER, joined),
                new BabyMembershipView(2L, BabyMemberRole.CAREGIVER, joined)));
        when(summaries.findActiveSummaries(Set.of(1L, 2L, 3L))).thenReturn(List.of(
                new MemberSummary(3L, "셋", null),
                new MemberSummary(1L, "하나", null),
                new MemberSummary(2L, "둘", null)));

        var result = new GetBabyMembersService(requester, babies, summaries).getMembers(1L, 10L);

        assertThat(result).extracting(summary -> summary.memberId()).containsExactly(1L, 2L, 3L);
        verify(requester).requireActiveMember(1L);
        verify(summaries).findActiveSummaries(Set.of(1L, 2L, 3L));
        verifyNoMoreInteractions(summaries);
    }
}
