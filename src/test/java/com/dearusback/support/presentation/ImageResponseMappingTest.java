package com.dearusback.support.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearusback.care.application.model.CareHomeSnapshot;
import com.dearusback.care.application.model.CareTypeCounts;
import com.dearusback.family.application.model.BabyMemberSummary;
import com.dearusback.family.application.model.CreateBabyResult;
import com.dearusback.family.application.model.InvitationPreview;
import com.dearusback.family.application.model.MyBabySummary;
import com.dearusback.family.domain.model.BabyGender;
import com.dearusback.family.domain.model.BabyInvitationStatus;
import com.dearusback.family.domain.model.BabyMemberRole;
import com.dearusback.family.presentation.dto.BabyMemberResponse;
import com.dearusback.family.presentation.dto.BabyResponse;
import com.dearusback.family.presentation.dto.InvitationPreviewResponse;
import com.dearusback.identity.application.model.MyProfileResult;
import com.dearusback.identity.application.model.SocialLoginResult;
import com.dearusback.identity.presentation.dto.MyProfileResponse;
import com.dearusback.identity.presentation.dto.SocialLoginResponse;
import com.dearusback.insight.application.model.HomeResult;
import com.dearusback.insight.presentation.dto.HomeResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageResponseMappingTest {

    private static final String STORED = "https://bucket.s3.ap-northeast-2.amazonaws.com/image.png";
    private static final String SIGNED = "https://bucket.s3.ap-northeast-2.amazonaws.com/image.png?signature=new";

    @Test
    void memberResponsesUseReadUrlWithoutChangingFields() {
        var login = new SocialLoginResult("access", "refresh", "Bearer", 1800, 3600, false,
                new SocialLoginResult.MemberResult(7L, "member", STORED, null));
        var profile = new MyProfileResult(7L, "member@example.com", "member", STORED, "Asia/Seoul", null,
                List.of());

        assertThat(SocialLoginResponse.from(login, (memberId, url) -> signedFor(memberId, url))
                .member().profileImageUrl()).isEqualTo(SIGNED);
        assertThat(MyProfileResponse.from(profile, (memberId, url) -> signedFor(memberId, url))
                .profileImageUrl()).isEqualTo(SIGNED);
    }

    @Test
    void babyHomeMemberAndInvitationResponsesUseReadUrls() {
        LocalDate birthDate = LocalDate.of(2026, 1, 1);
        var created = new CreateBabyResult(3L, "baby", birthDate, BabyGender.FEMALE, STORED,
                BabyMemberRole.OWNER);
        var baby = new MyBabySummary(3L, "baby", birthDate, BabyGender.FEMALE, STORED,
                BabyMemberRole.OWNER);
        var member = new BabyMemberSummary(7L, "member", STORED, BabyMemberRole.OWNER);
        var invitation = new InvitationPreview("baby", STORED, BabyMemberRole.CAREGIVER,
                BabyInvitationStatus.PENDING, OffsetDateTime.now());
        var home = new HomeResult(baby, LocalDate.now(),
                new CareHomeSnapshot(null, new CareTypeCounts(0, 0, 0, 0, 0, 0), List.of()));

        assertThat(BabyResponse.from(created, url -> SIGNED).profileImageUrl()).isEqualTo(SIGNED);
        assertThat(BabyResponse.from(baby, url -> SIGNED).profileImageUrl()).isEqualTo(SIGNED);
        assertThat(BabyMemberResponse.from(member, (memberId, url) -> signedFor(memberId, url))
                .profileImageUrl()).isEqualTo(SIGNED);
        assertThat(InvitationPreviewResponse.from(invitation, url -> SIGNED).babyProfileImageUrl())
                .isEqualTo(SIGNED);
        assertThat(HomeResponse.from(home, url -> SIGNED).baby().profileImageUrl()).isEqualTo(SIGNED);
    }

    private static String signedFor(Long memberId, String url) {
        assertThat(memberId).isEqualTo(7L);
        assertThat(url).isEqualTo(STORED);
        return SIGNED;
    }
}
