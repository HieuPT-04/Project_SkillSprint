package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.marketplace.MarketplacePurchaseResponse;
import com.skillsprint.service.marketplace.MarketplacePurchaseService;
import java.util.UUID;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/marketplace/items") @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePurchaseController {
    MarketplacePurchaseService marketplacePurchaseService;
    @PostMapping("/{itemId}/purchase/coins")
    public ResponseEntity<ApiResponse<MarketplacePurchaseResponse>> purchaseWithCoins(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.success("Mua Quiz Pack thành công", marketplacePurchaseService.purchaseWithCoins(jwt.getSubject(), itemId)));
    }
}
