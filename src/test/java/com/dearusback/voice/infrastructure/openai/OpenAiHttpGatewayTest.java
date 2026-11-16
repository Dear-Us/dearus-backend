package com.dearusback.voice.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiHttpGatewayTest {
    private HttpServer server;
    private OpenAiHttpGateway gateway;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        gateway = new OpenAiHttpGateway(HttpClient.newHttpClient(), new ObjectMapper(), "test-only-key",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/"));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsM4aWithModelAndReadsTranscript() {
        AtomicReference<String> received = new AtomicReference<>();
        server.createContext("/v1/audio/transcriptions", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization"))
                    .isEqualTo("Bearer test-only-key");
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type"))
                    .startsWith("multipart/form-data; boundary=");
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = "{\"text\":\"아기 목욕했어\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        String transcript = gateway.transcribe(new byte[]{0, 0, 0, 12, 'f', 't', 'y', 'p'},
                "audio/mp4", "gpt-4o-mini-transcribe", 3000);
        assertThat(transcript).isEqualTo("아기 목욕했어");
        assertThat(received.get()).contains("name=\"model\"", "gpt-4o-mini-transcribe",
                "filename=\"audio.m4a\"", "name=\"language\"", "ko");
    }

    @Test
    void sendsPrivateJsonRequestAndReadsOnlyCompletedOutput() {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/v1/responses", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = ("{\"status\":\"completed\",\"output\":[{\"type\":\"message\","
                    + "\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ambiguous\\\":true,"
                    + "\\\"multipleRecords\\\":false}\"}]}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        assertThat(gateway.generate("gpt-4o-mini", "JSON only", "아기 목욕", 3000))
                .contains("ambiguous");
        var request = new ObjectMapper().readTree(body.get());
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.path("text").path("format").path("type").asText()).isEqualTo("json_object");
    }

    @Test
    void hidesProviderErrorBody() {
        server.createContext("/v1/responses", exchange -> {
            byte[] response = "private provider details".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        assertThatThrownBy(() -> gateway.generate("gpt-4o-mini", "JSON only", "test", 3000))
                .isInstanceOf(OpenAiProviderException.class)
                .hasMessage("OPENAI REQUEST_FAILED")
                .hasNoCause();
    }
}
