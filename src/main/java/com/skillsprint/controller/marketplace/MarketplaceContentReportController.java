package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceContentReportRequest;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceReportEvidenceUploadUrlRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceContentReportResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReportEvidenceUploadUrlResponse;
import com.skillsprint.service.marketplace.MarketplaceContentReportService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceContentReportController {

    MarketplaceContentReportService reportService;

    @PostMapping
    public ResponseEntity<ApiResponse<MarketplaceContentReportResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMarketplaceContentReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                "Đã gửi báo cáo. Đội ngũ kiểm duyệt sẽ xem xét.",
                reportService.createReport(jwt.getSubject(), request)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<MarketplaceContentReportResponse>>> getMine(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getMyReports(jwt.getSubject())));
    }

    @PostMapping("/evidence-upload-url")
    public ResponseEntity<ApiResponse<MarketplaceReportEvidenceUploadUrlResponse>> createEvidenceUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMarketplaceReportEvidenceUploadUrlRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reportService.createEvidenceUploadUrl(jwt.getSubject(), request)
        ));
    }
}
