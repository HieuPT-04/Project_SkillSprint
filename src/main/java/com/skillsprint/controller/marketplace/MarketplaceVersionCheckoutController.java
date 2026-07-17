package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.PurchaseMarketplacePackVersionRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceVersionPurchaseResponse;
import com.skillsprint.service.marketplace.MarketplaceVersionCheckoutService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/versions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceVersionCheckoutController {

    MarketplaceVersionCheckoutService marketplaceVersionCheckoutService;

    @PostMapping("/{versionId}/purchase/coins")
    public ResponseEntity<ApiResponse<MarketplaceVersionPurchaseResponse>> purchaseWithCoins(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId,
            @Valid @RequestBody PurchaseMarketplacePackVersionRequest request
    ) {
        MarketplaceVersionPurchaseResponse response = marketplaceVersionCheckoutService
                .purchaseWithCoins(jwt.getSubject(), versionId, request);
        return ResponseEntity.ok(ApiResponse.success("Mua phiên bản Quiz Pack thành công", response));
    }

    @PostMapping("/{versionId}/upgrade/coins")
    public ResponseEntity<ApiResponse<MarketplaceVersionPurchaseResponse>> upgradeWithCoins(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID versionId,
            @Valid @RequestBody PurchaseMarketplacePackVersionRequest request
    ) {
        MarketplaceVersionPurchaseResponse response = marketplaceVersionCheckoutService
                .upgradeWithCoins(jwt.getSubject(), versionId, request);
        return ResponseEntity.ok(ApiResponse.success("Nâng cấp phiên bản Quiz Pack thành công", response));
    }
}
