package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.StartMarketplacePracticeAttemptRequest;
import com.skillsprint.dto.request.marketplace.SubmitMarketplacePracticeAttemptRequest;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptHistoryResponse;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptResponse;
import com.skillsprint.dto.response.marketplace.MarketplacePracticeAttemptSubmissionResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionProgressResponse;
import com.skillsprint.service.marketplace.MarketplacePracticeAttemptService;
import com.skillsprint.service.marketplace.MarketplacePracticeAttemptSubmissionService;
import com.skillsprint.service.marketplace.MarketplaceVersionProgressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/marketplace/versions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePracticeAttemptController {

    MarketplacePracticeAttemptService attemptService;
    MarketplacePracticeAttemptSubmissionService submissionService;
    MarketplaceVersionProgressService progressService;

    @PostMapping("/{versionId}/practice-attempts")
    public ResponseEntity<ApiResponse<MarketplacePracticeAttemptResponse>> startOrResume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId,
            @Valid @RequestBody StartMarketplacePracticeAttemptRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Bắt đầu Quiz luyện tập",
                attemptService.startOrResume(jwt.getSubject(), versionId, request.getChapterSequenceNo())
        ));
    }

    @GetMapping("/{versionId}/practice-attempts/me/in-progress")
    public ResponseEntity<ApiResponse<MarketplacePracticeAttemptResponse>> getInProgress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId,
            @RequestParam @Min(1) int chapterSequenceNo
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                attemptService.getInProgress(jwt.getSubject(), versionId, chapterSequenceNo)
        ));
    }

    @GetMapping("/{versionId}/practice-attempts/me")
    public ResponseEntity<ApiResponse<List<MarketplacePracticeAttemptHistoryResponse>>> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                attemptService.history(jwt.getSubject(), versionId)
        ));
    }

    @GetMapping("/{versionId}/progress/me")
    public ResponseEntity<ApiResponse<MarketplaceVersionProgressResponse>> progress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                progressService.getProgress(jwt.getSubject(), versionId)
        ));
    }

    @PostMapping("/{versionId}/practice-attempts/{attemptId}/submit")
    public ResponseEntity<ApiResponse<MarketplacePracticeAttemptSubmissionResponse>> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody SubmitMarketplacePracticeAttemptRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Nộp Quiz luyện tập thành công",
                submissionService.submit(jwt.getSubject(), versionId, attemptId, request)
        ));
    }
}
