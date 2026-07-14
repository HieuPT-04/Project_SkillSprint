package com.skillsprint.controller.marketplace;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceQuizRequest;
import com.skillsprint.dto.response.marketplace.*;
import com.skillsprint.service.marketplace.MarketplaceChallengeService;
import jakarta.validation.Valid;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/marketplace/items") @RequiredArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceChallengeController {
    MarketplaceChallengeService marketplaceChallengeService;
    @PostMapping("/{itemId}/challenge/submit") public ResponseEntity<ApiResponse<MarketplaceQuizAttemptResponse>> submit(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID itemId,@Valid @RequestBody SubmitMarketplaceQuizRequest request){ return ResponseEntity.ok(ApiResponse.success("Nộp Full Pack Challenge thành công",marketplaceChallengeService.submit(jwt.getSubject(),itemId,request))); }
    @GetMapping("/{itemId}/leaderboard") public ResponseEntity<ApiResponse<List<MarketplaceLeaderboardEntryResponse>>> leaderboard(@PathVariable UUID itemId){ return ResponseEntity.ok(ApiResponse.success(marketplaceChallengeService.leaderboard(itemId))); }
}
