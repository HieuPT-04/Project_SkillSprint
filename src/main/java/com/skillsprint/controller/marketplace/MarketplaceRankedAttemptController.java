package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceRankedAttemptRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptSubmissionResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceLeaderboardEntryResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptHistoryResponse;
import com.skillsprint.service.marketplace.MarketplaceRankedAttemptService;
import com.skillsprint.service.marketplace.MarketplaceRankedAttemptHistoryService;
import com.skillsprint.service.marketplace.MarketplaceRankedAttemptSubmissionService;
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
@RequestMapping("/api/marketplace/versions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceRankedAttemptController {

    MarketplaceRankedAttemptService rankedAttemptService;
    MarketplaceRankedAttemptSubmissionService rankedAttemptSubmissionService;
    MarketplaceRankedAttemptHistoryService rankedAttemptHistoryService;

    @GetMapping("/{versionId}/leaderboard")
    public ResponseEntity<ApiResponse<List<MarketplaceLeaderboardEntryResponse>>> leaderboard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                rankedAttemptHistoryService.leaderboard(jwt.getSubject(), versionId)
        ));
    }

    @GetMapping("/{versionId}/ranked-attempts/me")
    public ResponseEntity<ApiResponse<List<MarketplaceRankedAttemptHistoryResponse>>> history(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                rankedAttemptHistoryService.history(jwt.getSubject(), versionId)
        ));
    }

    @PostMapping("/{versionId}/ranked-attempts")
    public ResponseEntity<ApiResponse<MarketplaceRankedAttemptResponse>> startOrResume(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Bắt đầu Quiz xếp hạng",
                rankedAttemptService.startOrResume(jwt.getSubject(), versionId)
        ));
    }

    @GetMapping("/{versionId}/ranked-attempts/me/in-progress")
    public ResponseEntity<ApiResponse<MarketplaceRankedAttemptResponse>> getInProgress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                rankedAttemptService.getInProgress(jwt.getSubject(), versionId)
        ));
    }

    @PostMapping("/{versionId}/ranked-attempts/{attemptId}/submit")
    public ResponseEntity<ApiResponse<MarketplaceRankedAttemptSubmissionResponse>> submit(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId,
            @PathVariable UUID attemptId,
            @Valid @RequestBody SubmitMarketplaceRankedAttemptRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Nộp Quiz xếp hạng thành công",
                rankedAttemptSubmissionService.submit(jwt.getSubject(), versionId, attemptId, request)
        ));
    }
}
