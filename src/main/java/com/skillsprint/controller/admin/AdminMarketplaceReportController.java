package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.UpdateMarketplaceContentReportStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceContentReportResponse;
import com.skillsprint.enums.marketplace.MarketplaceReportCategory;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportTargetType;
import com.skillsprint.service.marketplace.MarketplaceContentReportService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/marketplace/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminMarketplaceReportController {

    MarketplaceContentReportService reportService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<MarketplaceContentReportResponse>>> list(
            @RequestParam(required = false) MarketplaceReportStatus status,
            @RequestParam(required = false) MarketplaceReportTargetType targetType,
            @RequestParam(required = false) MarketplaceReportCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportService.getAdminReports(status, targetType, category, page, size)
        ));
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketplaceContentReportResponse>> detail(
            @PathVariable UUID reportId
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getAdminReport(reportId)));
    }

    @PatchMapping("/{reportId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketplaceContentReportResponse>> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportId,
            @Valid @RequestBody UpdateMarketplaceContentReportStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cập nhật trạng thái báo cáo",
                reportService.updateStatus(jwt.getSubject(), reportId, request)
        ));
    }
}
