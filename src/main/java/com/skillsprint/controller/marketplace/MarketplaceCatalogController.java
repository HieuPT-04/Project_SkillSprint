package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceCatalogItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemDetailResponse;
import com.skillsprint.service.marketplace.MarketplaceCatalogService;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/items")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceCatalogController {

    MarketplaceCatalogService marketplaceCatalogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MarketplaceCatalogItemResponse>>> getPublishedItems(
            @RequestParam(required = false) String subject
    ) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceCatalogService.getPublishedItems(subject)));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<ApiResponse<MarketplaceItemDetailResponse>> getPublishedItem(
            @PathVariable UUID itemId
    ) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceCatalogService.getPublishedItem(itemId)));
    }
}
