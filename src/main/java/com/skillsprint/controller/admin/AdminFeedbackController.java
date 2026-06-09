package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.feedback.UpdateFeedbackStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.feedback.FeedbackAdminResponse;
import com.skillsprint.enums.feedback.FeedbackStatus;
import com.skillsprint.enums.feedback.FeedbackType;
import com.skillsprint.service.feedback.FeedbackService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/feedback")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminFeedbackController {

    FeedbackService feedbackService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackAdminResponse>>> getFeedback(
            @RequestParam(required = false) FeedbackType type,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<FeedbackAdminResponse> response = feedbackService.getAdminFeedback(type, status, search, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{feedbackId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FeedbackAdminResponse>> getFeedbackDetail(@PathVariable UUID feedbackId) {
        FeedbackAdminResponse response = feedbackService.getFeedback(feedbackId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{feedbackId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FeedbackAdminResponse>> updateFeedbackStatus(
            @PathVariable UUID feedbackId,
            @Valid @RequestBody UpdateFeedbackStatusRequest request
    ) {
        FeedbackAdminResponse response = feedbackService.updateFeedbackStatus(feedbackId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật feedback thành công", response));
    }
}
