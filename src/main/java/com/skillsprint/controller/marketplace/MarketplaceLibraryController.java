package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.marketplace.*;
import com.skillsprint.service.marketplace.MarketplaceLibraryService;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/marketplace/my-packs") @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceLibraryController {
    MarketplaceLibraryService marketplaceLibraryService;
    @GetMapping public ResponseEntity<ApiResponse<List<MarketplaceCatalogItemResponse>>> getMyPacks(@AuthenticationPrincipal Jwt jwt) { return ResponseEntity.ok(ApiResponse.success(marketplaceLibraryService.getMyPacks(jwt.getSubject()))); }
    @GetMapping("/{itemId}") public ResponseEntity<ApiResponse<PurchasedQuizPackResponse>> getMyPack(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) { return ResponseEntity.ok(ApiResponse.success(marketplaceLibraryService.getMyPack(jwt.getSubject(), itemId))); }
}
