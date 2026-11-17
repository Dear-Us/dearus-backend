package com.dearusback.voice.infrastructure.openai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class OpenAiHttpGateway implements OpenAiGateway {
    private static final URI API_BASE = URI.create("https://api.openai.com/v1/");
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final URI apiBase;

    OpenAiHttpGateway(HttpClient client, ObjectMapper mapper, String apiKey) {
        this(client, mapper, apiKey, API_BASE);
    }

    OpenAiHttpGateway(HttpClient client, ObjectMapper mapper, String apiKey, URI apiBase) {
        this.client = client;
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
    }

    @Override
    public String transcribe(byte[] audio, String contentType, String model, int timeoutMs) {
        String extension = contentType != null && contentType.contains("wav") ? "wav" : "m4a";
        String boundary = "dearus-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"
                + model + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\n"
                + "ko\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio."
                + extension + "\"\r\nContent-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + audio.length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(audio, 0, body, prefix.length, audio.length);
        System.arraycopy(suffix, 0, body, prefix.length + audio.length, suffix.length);
        HttpRequest request = request(apiBase.resolve("audio/transcriptions"), timeoutMs)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        JsonNode response = send(request);
        String text = response.path("text").asText();
        if (text == null || text.isBlank()) throw new OpenAiProviderException("INVALID_TRANSCRIPTION");
        return text.trim();
    }

    @Override
    public String generate(String model, String instructions, String prompt, int timeoutMs) {
        String body = mapper.writeValueAsString(Map.of(
                "model", model, "instructions", instructions,
                "input", prompt + "\nReturn one json object only.",
                "text", Map.of("format", Map.of("type", "json_object")), "store", false));
        HttpRequest request = request(apiBase.resolve("responses"), timeoutMs)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        JsonNode response = send(request);
        if (!"completed".equals(response.path("status").asText())) {
            throw new OpenAiProviderException("INCOMPLETE_RESPONSE");
        }
        JsonNode output = response.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode content : item.path("content")) {
                    if ("output_text".equals(content.path("type").asText())
                            && content.path("text").isTextual()) {
                        return content.path("text").asText();
                    }
                }
            }
        }
        throw new OpenAiProviderException("INVALID_RESPONSE");
    }

    private HttpRequest.Builder request(URI uri, int timeoutMs) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + apiKey);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OpenAiProviderException("REQUEST_FAILED");
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenAiProviderException("REQUEST_INTERRUPTED");
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof OpenAiProviderException provider) throw provider;
            throw new OpenAiProviderException("REQUEST_FAILED");
        }
    }
}
