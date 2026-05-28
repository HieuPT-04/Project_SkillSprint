package com.skillsprint.controller.session;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.session.FinishStudySessionRequest;
import com.skillsprint.dto.response.session.StudySessionDetailResponse;
import com.skillsprint.dto.response.session.StudySessionResponse;
import com.skillsprint.service.session.StudySessionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StudySessionController {

    StudySessionService studySessionService;

    @GetMapping("/calendar/tasks/{taskId}/study-session")
    public ResponseEntity<ApiResponse<StudySessionDetailResponse>> getStudySessionDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId
    ) {
        StudySessionDetailResponse response = studySessionService.getStudySessionDetail(jwt.getSubject(), taskId);
        return ResponseEntity.ok(ApiResponse.success("Lấy phiên học thành công", response));
    }

    @PostMapping("/calendar/tasks/{taskId}/sessions/start")
    public ResponseEntity<ApiResponse<StudySessionResponse>> startSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID taskId
    ) {
        StudySessionResponse response = studySessionService.startSession(jwt.getSubject(), taskId);
        return ResponseEntity.ok(ApiResponse.success("Bắt đầu phiên học thành công", response));
    }

    @PostMapping("/study-sessions/{sessionId}/finish")
    public ResponseEntity<ApiResponse<StudySessionResponse>> finishSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) FinishStudySessionRequest request
    ) {
        StudySessionResponse response = studySessionService.finishSession(jwt.getSubject(), sessionId, request);
        return ResponseEntity.ok(ApiResponse.success("Kết thúc phiên học thành công", response));
    }
}
