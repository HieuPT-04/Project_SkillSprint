package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.marketplace.PlatformTreasuryEntryResponse;
import com.skillsprint.dto.response.marketplace.PlatformTreasurySummaryResponse;
import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import com.skillsprint.service.marketplace.PlatformTreasuryService;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/marketplace/treasury")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminMarketplaceTreasuryController {
    PlatformTreasuryService platformTreasuryService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PlatformTreasurySummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.success(platformTreasuryService.getSummary()));
    }

    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<PageResponse<PlatformTreasuryEntryResponse>>> entries(
            @RequestParam(required = false) PlatformTreasuryAsset asset,
            @RequestParam(required = false) PlatformTreasuryEntryType entryType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                platformTreasuryService.getEntries(asset, entryType, from, to, page, size)));
    }
}
