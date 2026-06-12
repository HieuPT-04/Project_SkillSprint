package com.skillsprint.controller.subscription;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.dto.response.subscription.QuotaStatusResponse;
import com.skillsprint.dto.response.subscription.UserServicePlanResponse;
import com.skillsprint.service.subscription.QuotaService;
import com.skillsprint.service.subscription.SubscriptionService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SubscriptionController {

    SubscriptionService subscriptionService;
    QuotaService quotaService;

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<UserServicePlanResponse>>> getPlans() {
        List<UserServicePlanResponse> response = subscriptionService.getActivePlans();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gói thành công", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentSubscriptionResponse>> getMySubscription(
            @AuthenticationPrincipal Jwt jwt
    ) {
        CurrentSubscriptionResponse response = subscriptionService.getCurrentSubscription(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Lấy gói hiện tại thành công", response));
    }

    @GetMapping("/me/quota")
    public ResponseEntity<ApiResponse<QuotaStatusResponse>> getMyQuota(
            @AuthenticationPrincipal Jwt jwt
    ) {
        QuotaStatusResponse response = quotaService.getQuotaStatus(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Lấy giới hạn sử dụng thành công", response));
    }
}
