package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionMetricsResponse;
import com.skillsprint.service.marketplace.MarketplaceVersionMetricsService;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/marketplace")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminMarketplaceMetricsController {

    MarketplaceVersionMetricsService metricsService;

    @GetMapping("/versions/{versionId}/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketplaceVersionMetricsResponse>> metrics(@PathVariable UUID versionId) {
        return ResponseEntity.ok(ApiResponse.success(metricsService.getMetrics(versionId)));
    }
}
