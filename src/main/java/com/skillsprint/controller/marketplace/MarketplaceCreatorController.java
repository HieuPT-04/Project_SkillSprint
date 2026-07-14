package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceItemRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.service.marketplace.MarketplaceCreatorService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/items")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceCreatorController {

    MarketplaceCreatorService marketplaceCreatorService;

    @PostMapping
    public ResponseEntity<ApiResponse<MarketplaceItemResponse>> createDraft(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMarketplaceItemRequest request
    ) {
        MarketplaceItemResponse response = marketplaceCreatorService.createDraft(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Tạo nháp Quiz Pack thành công", response));
    }
}
