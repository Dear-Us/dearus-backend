package com.dearusback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;

class LocalConfigurationImportTest {

    @Test
    void loadsEnvTemplateAsSpringPropertiesWithoutStartingDatabase() {
        SpringApplication application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);

        try (var context = application.run("--spring.config.import=file:.env.example[.properties]")) {
            assertEquals("jdbc:mariadb://{RDS_ENDPOINT}:3306/dearus?sslMode=verify-full",
                    context.getEnvironment().getProperty("DB_URL"));
            assertEquals("{NUMERIC_KAKAO_APP_ID}", context.getEnvironment().getProperty("KAKAO_APP_ID"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {
    }
}
