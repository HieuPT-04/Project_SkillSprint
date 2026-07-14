package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceItemRequest;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceQuizRequest;
import com.skillsprint.dto.response.marketplace.CreatorValidationPackResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceQuizAttemptResponse;
import com.skillsprint.service.marketplace.MarketplaceCreatorService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/marketplace/items")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceCreatorController {

    MarketplaceCreatorService marketplaceCreatorService;

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<MarketplaceItemResponse>>> getMyItems(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceCreatorService.getMyItems(jwt.getSubject())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MarketplaceItemResponse>> createDraft(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMarketplaceItemRequest request
    ) {
        MarketplaceItemResponse response = marketplaceCreatorService.createDraft(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Tạo nháp Quiz Pack thành công", response));
    }

    @GetMapping("/{itemId}/creator-validation")
    public ResponseEntity<ApiResponse<CreatorValidationPackResponse>> getCreatorValidationPack(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId
    ) {
        CreatorValidationPackResponse response = marketplaceCreatorService
                .getCreatorValidationPack(jwt.getSubject(), itemId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{itemId}/refresh-snapshot")
    public ResponseEntity<ApiResponse<MarketplaceItemResponse>> refreshSnapshot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId
    ) {
        MarketplaceItemResponse response = marketplaceCreatorService.refreshSnapshot(jwt.getSubject(), itemId);
        return ResponseEntity.ok(ApiResponse.success("Làm mới Quiz Pack snapshot thành công", response));
    }

    @PostMapping("/{itemId}/creator-validation")
    public ResponseEntity<ApiResponse<MarketplaceQuizAttemptResponse>> validateFullPack(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId,
            @Valid @RequestBody SubmitMarketplaceQuizRequest request
    ) {
        MarketplaceQuizAttemptResponse response = marketplaceCreatorService
                .validateFullPack(jwt.getSubject(), itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Hoàn thành Full Pack Challenge", response));
    }

    @PostMapping("/{itemId}/submit-review")
    public ResponseEntity<ApiResponse<MarketplaceItemResponse>> submitForReview(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID itemId
    ) {
        MarketplaceItemResponse response = marketplaceCreatorService.submitForReview(jwt.getSubject(), itemId);
        return ResponseEntity.ok(ApiResponse.success("Gửi Quiz Pack chờ duyệt thành công", response));
    }
}
