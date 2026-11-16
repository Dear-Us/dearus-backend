package com.dearusback.support.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI dearUsOpenApi(@Value("${dearus.openapi.server-url:}") String configuredServerUrl) {
        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url("/").description("현재 접속한 서버"));

        if (configuredServerUrl != null && !configuredServerUrl.isBlank()) {
            servers.add(new Server()
                    .url(configuredServerUrl)
                    .description("설정된 배포 서버"));
        }

        return new OpenAPI()
                .info(new Info()
                        .title("DearUs API")
                        .version("v1")
                        .description("DearUs는 아기의 일상과 성장 기록을 보호자가 함께 관리하는 육아 기록 서비스입니다."))
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("AUTH-01/02에서 받은 DearUs Access Token을 입력하세요.")
                ))
                .servers(servers)
                .tags(List.of(
                        tag("Auth", "소셜 로그인과 세션"),
                        tag("Member", "회원 정보"),
                        tag("Baby", "아기"),
                        tag("Baby Member", "공동 보호자"),
                        tag("Invitation", "초대"),
                        tag("Care Record", "육아 기록"),
                        tag("Home", "홈"),
                        tag("Event", "SSE"),
                        tag("Statistics", "통계"),
                        tag("Growth", "성장 기록"),
                        tag("Voice", "음성 육아 기록")
                ));
    }

    private Tag tag(String name, String description) {
        return new Tag().name(name).description(description);
    }
}
