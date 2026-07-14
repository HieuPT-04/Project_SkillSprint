package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.ReviewMarketplaceItemRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceAdminItemDetailResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.service.marketplace.MarketplaceAdminService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/marketplace/items")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketplaceController {

    MarketplaceAdminService marketplaceAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MarketplaceItemResponse>>> getPendingItems() {
        return ResponseEntity.ok(ApiResponse.success(marketplaceAdminService.getPendingItems()));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<ApiResponse<MarketplaceAdminItemDetailResponse>> getItemDetail(
            @PathVariable UUID itemId
    ) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceAdminService.getItemDetail(itemId)));
    }

    @PatchMapping("/{itemId}/status")
    public ResponseEntity<ApiResponse<MarketplaceItemResponse>> review(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody ReviewMarketplaceItemRequest request
    ) {
        MarketplaceItemResponse response = marketplaceAdminService.review(jwt.getSubject(), itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái Quiz Pack thành công", response));
    }
}
