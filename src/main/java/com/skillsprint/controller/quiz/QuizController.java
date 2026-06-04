package com.skillsprint.controller.quiz;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.quiz.SubmitQuizRequest;
import com.skillsprint.dto.response.quiz.QuizAttemptResponse;
import com.skillsprint.dto.response.quiz.QuizResponse;
import com.skillsprint.service.quiz.QuizService;
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
public class QuizController {

    QuizService quizService;

    @PostMapping("/roadmap-steps/{stepId}/quiz/generate")
    public ResponseEntity<ApiResponse<QuizResponse>> generate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stepId
    ) {
        QuizResponse response = quizService.generate(jwt.getSubject(), stepId);
        return ResponseEntity.ok(ApiResponse.success("Tạo quiz thành công", response));
    }

    @GetMapping("/roadmap-steps/{stepId}/quiz/current")
    public ResponseEntity<ApiResponse<QuizResponse>> getCurrent(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID stepId
    ) {
        QuizResponse response = quizService.getCurrent(jwt.getSubject(), stepId);
        return ResponseEntity.ok(ApiResponse.success("Lấy quiz thành công", response));
    }

    @PostMapping("/quizzes/{quizId}/submit")
    public ResponseEntity<ApiResponse<QuizAttemptResponse>> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID quizId,
            @Valid @RequestBody SubmitQuizRequest request
    ) {
        QuizAttemptResponse response = quizService.submit(jwt.getSubject(), quizId, request);
        return ResponseEntity.ok(ApiResponse.success("Nộp quiz thành công", response));
    }

    @GetMapping("/quizzes/{quizId}/attempts/latest")
    public ResponseEntity<ApiResponse<QuizAttemptResponse>> getLatestAttempt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID quizId
    ) {
        QuizAttemptResponse response = quizService.getLatestAttempt(jwt.getSubject(), quizId);
        return ResponseEntity.ok(ApiResponse.success("Lấy kết quả quiz thành công", response));
    }
}
