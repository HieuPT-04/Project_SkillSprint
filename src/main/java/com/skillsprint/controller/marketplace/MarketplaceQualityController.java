package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceQualityJobResponse;
import com.skillsprint.service.marketplace.MarketplaceQualityService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/creator/versions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceQualityController {

    MarketplaceQualityService qualityService;

    @PostMapping("/{versionId}/quality-jobs")
    public ResponseEntity<ApiResponse<MarketplaceQualityJobResponse>> queue(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xếp lịch kiểm định chất lượng Quiz Pack",
                qualityService.queueForCreator(jwt.getSubject(), versionId)
        ));
    }

    @GetMapping("/{versionId}/quality-jobs/latest")
    public ResponseEntity<ApiResponse<MarketplaceQualityJobResponse>> latest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                qualityService.getLatestForCreator(jwt.getSubject(), versionId)
        ));
    }
}
