package com.skillsprint.controller.feedback;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.feedback.CreateFeedbackRequest;
import com.skillsprint.dto.response.feedback.FeedbackResponse;
import com.skillsprint.dto.response.feedback.FeedbackSubmitResponse;
import com.skillsprint.service.feedback.FeedbackService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeedbackController {

    FeedbackService feedbackService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> getMyFeedback(
            @AuthenticationPrincipal Jwt jwt
    ) {
        List<FeedbackResponse> response = feedbackService.getMyFeedback(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{feedbackId}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> getMyFeedbackDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID feedbackId
    ) {
        FeedbackResponse response = feedbackService.getMyFeedbackDetail(jwt.getSubject(), feedbackId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FeedbackSubmitResponse>> createFeedback(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateFeedbackRequest request
    ) {
        FeedbackSubmitResponse response = feedbackService.createFeedback(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Gửi feedback thành công", response));
    }
}
