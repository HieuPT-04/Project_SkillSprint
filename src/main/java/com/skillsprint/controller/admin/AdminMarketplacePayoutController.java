package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.CompleteCreatorPayoutRequest;
import com.skillsprint.dto.request.marketplace.RejectCreatorPayoutRequest;
import com.skillsprint.dto.response.marketplace.CreatorPayoutResponse;
import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
import com.skillsprint.service.marketplace.CreatorPayoutService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/admin/marketplace/payouts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketplacePayoutController {

    CreatorPayoutService creatorPayoutService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CreatorPayoutResponse>>> getPayouts(
            @RequestParam(required = false) CreatorPayoutStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(creatorPayoutService.getAdminPayouts(status)));
    }

    @PatchMapping("/{payoutId}/approve")
    public ResponseEntity<ApiResponse<CreatorPayoutResponse>> approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID payoutId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã duyệt yêu cầu rút tiền",
                creatorPayoutService.approve(jwt.getSubject(), payoutId)));
    }

    @PatchMapping("/{payoutId}/processing")
    public ResponseEntity<ApiResponse<CreatorPayoutResponse>> startProcessing(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID payoutId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã bắt đầu chuyển khoản thủ công",
                creatorPayoutService.startProcessing(jwt.getSubject(), payoutId)));
    }

    @PatchMapping("/{payoutId}/complete")
    public ResponseEntity<ApiResponse<CreatorPayoutResponse>> complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID payoutId,
            @Valid @RequestBody CompleteCreatorPayoutRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã hoàn tất chuyển khoản",
                creatorPayoutService.complete(jwt.getSubject(), payoutId, request)));
    }

    @PatchMapping("/{payoutId}/reject")
    public ResponseEntity<ApiResponse<CreatorPayoutResponse>> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID payoutId,
            @Valid @RequestBody RejectCreatorPayoutRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối yêu cầu rút tiền",
                creatorPayoutService.reject(jwt.getSubject(), payoutId, request)));
    }

    @PatchMapping("/{payoutId}/fail")
    public ResponseEntity<ApiResponse<CreatorPayoutResponse>> fail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID payoutId,
            @Valid @RequestBody RejectCreatorPayoutRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận chuyển khoản thất bại",
                creatorPayoutService.fail(jwt.getSubject(), payoutId, request)));
    }
}
