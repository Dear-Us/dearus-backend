package com.dearusback.voice.presentation.rest;

import com.dearusback.support.presentation.response.ApiResponse;
import com.dearusback.voice.application.service.VoiceWorkflowService;
import com.dearusback.voice.domain.exception.VoiceErrorCode;
import com.dearusback.voice.domain.exception.VoiceException;
import com.dearusback.voice.domain.model.VoiceAction;
import com.dearusback.voice.presentation.dto.VoiceCommandResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/babies/{babyId}/voice-commands")
@Tag(name = "Voice", description = "음성 육아 기록 요청")
@SecurityRequirement(name = "bearerAuth")
public class VoiceCommandController {
    private final VoiceWorkflowService workflow;
    private final ObjectMapper mapper;

    public VoiceCommandController(VoiceWorkflowService workflow, ObjectMapper mapper) {
        this.workflow = workflow; this.mapper = mapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadVoiceCommand", summary = "음성 기록 업로드", tags = "Voice",
            description = "M4A/MP4 AAC 또는 WAV 음성 파일을 접수하고 비동기로 처리한다. Idempotency-Key는 UUID이며 같은 키와 다른 파일은 409. 기능 비활성화 시 503.")
    public ResponseEntity<ApiResponse<VoiceCommandResponse>> upload(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @Parameter(description = "재시도 중복 방지 UUID", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestPart("audio") MultipartFile audio) {
        try {
            var result = workflow.upload(memberId, babyId, idempotencyKey, audio.getBytes(), audio.getContentType());
            URI location = URI.create("/api/v1/babies/" + babyId + "/voice-commands/" + result.id());
            return ResponseEntity.status(HttpStatus.ACCEPTED).location(location)
                    .body(ApiResponse.success(VoiceCommandResponse.from(result, mapper)));
        } catch (IOException exception) {
            throw new VoiceException(VoiceErrorCode.INVALID_AUDIO);
        }
    }

    @GetMapping("/{voiceRequestId}")
    @Operation(operationId = "getVoiceCommand", summary = "음성 요청 결과 조회", tags = "Voice",
            description = "요청자 본인과 ACTIVE 아기 보호자만 처리 상태와 결과를 조회한다. 다른 회원의 요청은 404.")
    public ResponseEntity<ApiResponse<VoiceCommandResponse>> get(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long voiceRequestId) {
        return ResponseEntity.ok(ApiResponse.success(VoiceCommandResponse.from(
                workflow.get(memberId, babyId, voiceRequestId), mapper)));
    }

    @PostMapping(value = "/{voiceRequestId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "confirmVoiceCommand", summary = "음성 기록 확인", tags = "Voice",
            description = "확인이 필요한 초안을 Care 기록으로 한 번만 반영한다. REVIEW_REQUIRED는 action/draft를 함께 수정해 보낼 수 있고 CONFIRMATION_REQUIRED는 빈 객체를 보낸다.")
    public ResponseEntity<ApiResponse<VoiceCommandResponse>> confirm(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long voiceRequestId, @RequestBody(required = false) JsonNode body) {
        VoiceAction action = null;
        String draft = null;
        if (body != null) {
            if (!body.isObject() || body.size() != 0 && (body.size() != 2 || !body.has("action")
                    || !body.has("draft") || !body.path("action").isTextual()
                    || !body.path("draft").isObject())) {
                throw new VoiceException(VoiceErrorCode.INVALID_REQUEST);
            }
            if (body.size() == 2) {
                try {
                    action = VoiceAction.valueOf(body.path("action").asText());
                } catch (IllegalArgumentException exception) {
                    throw new VoiceException(VoiceErrorCode.INVALID_REQUEST);
                }
                draft = body.path("draft").toString();
            }
        }
        return ResponseEntity.ok(ApiResponse.success(VoiceCommandResponse.from(
                workflow.confirm(memberId, babyId, voiceRequestId, action, draft), mapper)));
    }

    @PostMapping("/{voiceRequestId}/cancel")
    @Operation(operationId = "cancelVoiceCommand", summary = "음성 요청 취소", tags = "Voice",
            description = "기록 반영 전 요청만 취소한다. 반복 취소는 같은 CANCELED 결과를 반환한다.")
    public ResponseEntity<ApiResponse<VoiceCommandResponse>> cancel(
            @AuthenticationPrincipal Long memberId, @PathVariable Long babyId,
            @PathVariable Long voiceRequestId) {
        return ResponseEntity.ok(ApiResponse.success(VoiceCommandResponse.from(
                workflow.cancel(memberId, babyId, voiceRequestId), mapper)));
    }
}
