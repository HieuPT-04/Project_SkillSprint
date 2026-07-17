package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.CreateCreatorPayoutQrUploadUrlRequest;
import com.skillsprint.dto.request.marketplace.CreateCreatorPayoutRequest;
import com.skillsprint.dto.request.marketplace.UpsertCreatorPayoutDestinationRequest;
import com.skillsprint.dto.response.marketplace.CreatorEarningsResponse;
import com.skillsprint.dto.response.marketplace.CreatorPayoutDestinationResponse;
import com.skillsprint.dto.response.marketplace.CreatorPayoutQrUploadUrlResponse;
import com.skillsprint.dto.response.marketplace.CreatorPayoutResponse;
import com.skillsprint.service.marketplace.CreatorPayoutService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace/creator")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceCreatorPayoutController {

    CreatorPayoutService creatorPayoutService;

    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<CreatorEarningsResponse>> getEarnings(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(creatorPayoutService.getEarnings(jwt.getSubject())));
    }

    @GetMapping("/payout-destination")
    public ResponseEntity<ApiResponse<CreatorPayoutDestinationResponse>> getDestination(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(creatorPayoutService.getDestination(jwt.getSubject())));
    }

    @PostMapping("/payout-destination/qr-upload-url")
    public ResponseEntity<ApiResponse<CreatorPayoutQrUploadUrlResponse>> createQrUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCreatorPayoutQrUploadUrlRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                creatorPayoutService.createQrUploadUrl(jwt.getSubject(), request)));
    }

    @PostMapping("/payout-destination")
    public ResponseEntity<ApiResponse<CreatorPayoutDestinationResponse>> upsertDestination(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpsertCreatorPayoutDestinationRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã lưu thông tin nhận tiền Creator",
                creatorPayoutService.upsertDestination(jwt.getSubject(), request)));
    }

    @PostMapping("/payouts")
    public ResponseEntity<ApiResponse<CreatorPayoutResponse>> createPayout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCreatorPayoutRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Đã tạo yêu cầu rút tiền",
                creatorPayoutService.requestPayout(jwt.getSubject(), request)));
    }

    @GetMapping("/payouts")
    public ResponseEntity<ApiResponse<List<CreatorPayoutResponse>>> getPayouts(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(creatorPayoutService.getPayouts(jwt.getSubject())));
    }
}
