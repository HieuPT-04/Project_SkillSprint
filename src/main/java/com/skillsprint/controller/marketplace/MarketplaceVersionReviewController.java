package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewCollectionResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewContextResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.service.marketplace.MarketplaceReviewService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/versions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceVersionReviewController {

    MarketplaceReviewService marketplaceReviewService;

    @GetMapping("/{versionId}/reviews")
    public ResponseEntity<ApiResponse<MarketplaceReviewCollectionResponse>> getReviews(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceReviewService.getVersionReviews(jwt.getSubject(), versionId)
        ));
    }

    @GetMapping("/{versionId}/reviews/me")
    public ResponseEntity<ApiResponse<MarketplaceReviewContextResponse>> getMyReviewContext(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceReviewService.getReviewContext(jwt.getSubject(), versionId)
        ));
    }

    @PutMapping("/{versionId}/reviews/me")
    public ResponseEntity<ApiResponse<MarketplaceReviewResponse>> upsertMyReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId,
            @Valid @RequestBody UpsertMarketplaceReviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đánh giá phiên bản Quiz Pack thành công",
                marketplaceReviewService.upsertVersion(jwt.getSubject(), versionId, request)
        ));
    }
}
