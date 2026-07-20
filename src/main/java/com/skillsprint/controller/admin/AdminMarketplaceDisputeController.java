package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.DecideMarketplaceDisputeRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceDisputeResponse;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import com.skillsprint.service.marketplace.MarketplaceDisputeService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/marketplace")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminMarketplaceDisputeController {

    MarketplaceDisputeService disputeService;

    @GetMapping("/disputes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<MarketplaceDisputeResponse>>> list(
            @RequestParam(required = false) MarketplaceDisputeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getAdminDisputes(status, page, size)));
    }

    @GetMapping("/disputes/{disputeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketplaceDisputeResponse>> detail(@PathVariable UUID disputeId) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getAdminDispute(disputeId)));
    }

    @PatchMapping("/disputes/{disputeId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketplaceDisputeResponse>> decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID disputeId,
            @Valid @RequestBody DecideMarketplaceDisputeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cập nhật quyết định yêu cầu hoàn tiền",
                disputeService.decide(jwt.getSubject(), disputeId, request)
        ));
    }

    @PostMapping("/disputes/{disputeId}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MarketplaceDisputeResponse>> completeRefund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID disputeId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã hoàn tiền cho người mua",
                disputeService.completeRefund(jwt.getSubject(), disputeId)
        ));
    }
}
