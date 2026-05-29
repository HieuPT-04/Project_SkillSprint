package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.subscription.UpdateSubscriptionPlanRequest;
import com.skillsprint.dto.response.subscription.CurrentSubscriptionResponse;
import com.skillsprint.service.subscription.SubscriptionService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminSubscriptionController {

    SubscriptionService subscriptionService;

    @PatchMapping("/{userId}/subscription")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CurrentSubscriptionResponse>> updateUserSubscription(
            @PathVariable String userId,
            @Valid @RequestBody UpdateSubscriptionPlanRequest request
    ) {
        CurrentSubscriptionResponse response = subscriptionService.activatePlan(userId, request.getPlanType());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật gói sử dụng thành công", response));
    }
}