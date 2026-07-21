package com.skillsprint.controller.session;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.session.FinishStudySessionRequest;
import com.skillsprint.dto.request.session.StartStudySessionRequest;
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
            @PathVariable UUID taskId,
            @Valid @RequestBody(required = false) StartStudySessionRequest request
    ) {
        StudySessionResponse response = studySessionService.startSession(jwt.getSubject(), taskId, request);
        return ResponseEntity.ok(ApiResponse.success("Bắt đầu phiên học thành công", response));
    }

    @GetMapping("/study-sessions/{sessionId}")
    public ResponseEntity<ApiResponse<StudySessionDetailResponse>> getSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        StudySessionDetailResponse response = studySessionService.getSessionDetail(jwt.getSubject(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết phiên học thành công", response));
    }

    @PostMapping("/study-sessions/{sessionId}/pomodoro/pause")
    public ResponseEntity<ApiResponse<StudySessionResponse>> pausePomodoro(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        StudySessionResponse response = studySessionService.pausePomodoro(jwt.getSubject(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Tạm dừng Pomodoro thành công", response));
    }

    @PostMapping("/study-sessions/{sessionId}/pomodoro/resume")
    public ResponseEntity<ApiResponse<StudySessionResponse>> resumePomodoro(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        StudySessionResponse response = studySessionService.resumePomodoro(jwt.getSubject(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Tiếp tục Pomodoro thành công", response));
    }

    @PostMapping("/study-sessions/{sessionId}/pomodoro/next-phase")
    public ResponseEntity<ApiResponse<StudySessionResponse>> nextPomodoroPhase(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        StudySessionResponse response = studySessionService.nextPomodoroPhase(jwt.getSubject(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Chuyển Pomodoro phase thành công", response));
    }

    @PostMapping("/study-sessions/{sessionId}/pomodoro/skip")
    public ResponseEntity<ApiResponse<StudySessionResponse>> skipPomodoroPhase(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        StudySessionResponse response = studySessionService.skipPomodoroPhase(jwt.getSubject(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Đã bỏ qua phase Pomodoro (không tính thời gian tập trung đã bỏ qua)", response));
    }

    @PostMapping("/study-sessions/{sessionId}/pomodoro/finish")
    public ResponseEntity<ApiResponse<StudySessionResponse>> finishPomodoro(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId
    ) {
        StudySessionResponse response = studySessionService.finishPomodoro(jwt.getSubject(), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Kết thúc Pomodoro thành công", response));
    }

    @PostMapping("/study-sessions/{sessionId}/finish")
    public ResponseEntity<ApiResponse<StudySessionResponse>> finishSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId,
            @Valid @RequestBody(required = false) FinishStudySessionRequest request
    ) {
        StudySessionResponse response = studySessionService.finishSession(jwt.getSubject(), sessionId, request);
        String message = Boolean.TRUE.equals(response.getTaskCompleted())
                ? "Hoàn thành buổi học thành công"
                : "Phiên học đã kết thúc, task chưa hoàn thành vì thời gian học chưa đủ";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }
}
