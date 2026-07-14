package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.UpsertMarketplaceReviewRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceReviewResponse;
import com.skillsprint.service.marketplace.MarketplaceReviewService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/marketplace/items") @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceReviewController {
    MarketplaceReviewService marketplaceReviewService;
    @GetMapping("/{itemId}/reviews") public ResponseEntity<ApiResponse<List<MarketplaceReviewResponse>>> get(@PathVariable UUID itemId){return ResponseEntity.ok(ApiResponse.success(marketplaceReviewService.getReviews(itemId)));}
    @PostMapping("/{itemId}/review") public ResponseEntity<ApiResponse<MarketplaceReviewResponse>> upsert(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID itemId,@Valid @RequestBody UpsertMarketplaceReviewRequest request){return ResponseEntity.ok(ApiResponse.success("Đánh giá Quiz Pack thành công",marketplaceReviewService.upsert(jwt.getSubject(),itemId,request)));}
}
