package com.dearusback.support.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI dearUsOpenApi(@Value("${dearus.openapi.server-url:}") String configuredServerUrl) {
        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title("DearUs API")
                        .version("v1")
                        .description("""
                                DearUs 육아 기록 서비스 Backend API. 실제 구현된 API 경로는 `/api/v1`이며,
                                보호된 경로에는 `Authorization: Bearer {JWT}`가 필요합니다.

                                REST 성공: `{\"success\":true,\"data\":...,\"message\":null}`;
                                실패: `{\"success\":false,\"data\":null,\"message\":\"...\"}`.
                                외부 errorCode 필드는 없습니다. 시간은 offset이 포함된 ISO 8601,
                                날짜는 `YYYY-MM-DD`, 회원 timezone은 `Asia/Seoul` 같은 IANA ID입니다.
                                Member/Baby/Care/Growth 삭제는 soft delete이며 Care/Growth 수정·삭제는
                                `version` 기반 낙관적 충돌(409)을 사용합니다.

                                EVENT-01은 `text/event-stream`의 best-effort 알림입니다. 재연결 후 HOME을
                                다시 조회하십시오. 계획된 Voice 흐름은 모바일 오디오 → Backend → Google STT
                                → LLM 구조화 파싱 → 검증 → Care 반영입니다. VOICE-01~04와 STT/LLM 어댑터는
                                현재 dev에 구현되어 있지 않아 실행 가능한 API로 노출하지 않습니다.
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("AUTH-01/02에서 받은 DearUs Access Token을 입력하세요.")))
                .servers(List.of(new Server().url("/").description("현재 접속한 서버")))
                .tags(List.of(
                        tag("Auth", "소셜 로그인과 세션"), tag("Member", "회원 정보"),
                        tag("Baby", "아기"), tag("Baby Member", "공동 보호자"),
                        tag("Invitation", "초대"), tag("Care Record", "육아 기록"),
                        tag("Home", "홈"), tag("Event", "SSE"),
                        tag("Statistics", "통계"), tag("Growth", "성장 기록")));
        if (!configuredServerUrl.isBlank()) {
            api.addServersItem(new Server().url(configuredServerUrl).description("설정된 배포 서버"));
        }
        return api;
    }

    private Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
