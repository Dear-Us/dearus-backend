package com.dearusback.voice.infrastructure.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class VoiceProviderConditions {
    private VoiceProviderConditions() { }

    private static boolean selected(ConditionContext context, String feature, String provider,
                                    String defaultProvider, String expected) {
        var env = context.getEnvironment();
        return env.getProperty("dearus.voice.enabled", Boolean.class, false)
                && env.getProperty("dearus.voice." + feature + ".enabled", Boolean.class, false)
                && expected.equalsIgnoreCase(env.getProperty(
                        "dearus.voice." + provider + ".provider", defaultProvider));
    }

    public static final class GoogleStt implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return selected(context, "stt", "stt", "GOOGLE_SPEECH", "GOOGLE_SPEECH");
        }
    }

    public static final class OpenAiStt implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return selected(context, "stt", "stt", "GOOGLE_SPEECH", "OPENAI");
        }
    }

    public static final class GoogleLlm implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return selected(context, "llm", "llm", "VERTEX_GEMINI", "VERTEX_GEMINI");
        }
    }

    public static final class OpenAiLlm implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return selected(context, "llm", "llm", "VERTEX_GEMINI", "OPENAI");
        }
    }

    public static final class AnyOpenAi implements Condition {
        @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return new OpenAiStt().matches(context, metadata) || new OpenAiLlm().matches(context, metadata);
        }
    }
}
