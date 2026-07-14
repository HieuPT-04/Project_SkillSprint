package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.AdjustWalletRequest;
import com.skillsprint.dto.response.marketplace.WalletBalanceResponse;
import com.skillsprint.service.marketplace.MarketplaceWalletService;
import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/wallet") @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketplaceWalletController {
    MarketplaceWalletService marketplaceWalletService;
    @PostMapping("/{userId}/adjust")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> adjust(@PathVariable String userId, @Valid @RequestBody AdjustWalletRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Điều chỉnh ví thành công", marketplaceWalletService.adjust(userId, request)));
    }
}
