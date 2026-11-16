package com.dearusback.voice.infrastructure.google;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class GoogleProviderEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return environment.getProperty("dearus.voice.enabled", Boolean.class, false)
                && (environment.getProperty("dearus.voice.stt.enabled", Boolean.class, false)
                    && "GOOGLE_SPEECH".equalsIgnoreCase(environment.getProperty(
                        "dearus.voice.stt.provider", "GOOGLE_SPEECH"))
                || environment.getProperty("dearus.voice.llm.enabled", Boolean.class, false)
                    && "VERTEX_GEMINI".equalsIgnoreCase(environment.getProperty(
                        "dearus.voice.llm.provider", "VERTEX_GEMINI")));
    }
}
