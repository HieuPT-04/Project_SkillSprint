package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceDisputeRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceDisputeEligibilityResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceDisputeResponse;
import com.skillsprint.service.marketplace.MarketplaceDisputeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceDisputeController {

    MarketplaceDisputeService disputeService;

    @GetMapping("/sales/{saleId}/dispute-eligibility")
    public ResponseEntity<ApiResponse<MarketplaceDisputeEligibilityResponse>> eligibility(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID saleId
    ) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getEligibility(jwt.getSubject(), saleId)));
    }

    @PostMapping("/disputes")
    public ResponseEntity<ApiResponse<MarketplaceDisputeResponse>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMarketplaceDisputeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                "Đã gửi yêu cầu hoàn tiền. Đội ngũ vận hành sẽ xem xét.",
                disputeService.createDispute(jwt.getSubject(), request)
        ));
    }

    @GetMapping("/disputes/me")
    public ResponseEntity<ApiResponse<List<MarketplaceDisputeResponse>>> getMine(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(disputeService.getMyDisputes(jwt.getSubject())));
    }
}
