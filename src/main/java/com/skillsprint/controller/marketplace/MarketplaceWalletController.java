package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.CreateCoinTopUpRequest;
import com.skillsprint.dto.response.marketplace.*;
import com.skillsprint.service.marketplace.MarketplaceWalletService;
import com.skillsprint.service.payment.CoinTopUpService;
import com.skillsprint.service.ratelimit.RateLimitService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/marketplace/wallet")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceWalletController {

    MarketplaceWalletService marketplaceWalletService;
    CoinTopUpService coinTopUpService;
    RateLimitService rateLimitService;

    @GetMapping
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> balance(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceWalletService.getBalance(jwt.getSubject())));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> transactions(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceWalletService.getTransactions(jwt.getSubject())));
    }

    @GetMapping("/top-ups/packages")
    public ResponseEntity<ApiResponse<List<CoinPackageResponse>>> topUpPackages() {
        return ResponseEntity.ok(ApiResponse.success(coinTopUpService.getAvailablePackages()));
    }

    /**
     * The buyer is resolved from the JWT subject only. The request may name a package
     * key; it can never supply a user, a VND amount, or a Coin amount.
     */
    @PostMapping("/top-ups/sepay")
    public ResponseEntity<ApiResponse<CoinTopUpPaymentResponse>> createTopUp(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCoinTopUpRequest request
    ) {
        rateLimitService.checkPaymentCreate(jwt.getSubject());
        CoinTopUpPaymentResponse response = coinTopUpService.createTopUpPayment(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Tạo lệnh nạp Coin thành công", response));
    }
}
