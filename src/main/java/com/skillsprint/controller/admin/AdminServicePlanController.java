package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.admin.CreateServicePlanRequest;
import com.skillsprint.dto.request.admin.UpdatePlanFeaturesRequest;
import com.skillsprint.dto.request.admin.UpdateServicePlanRequest;
import com.skillsprint.dto.request.admin.UpdateServicePlanStatusRequest;
import com.skillsprint.dto.response.admin.AdminAuditLogResponse;
import com.skillsprint.dto.response.subscription.ServicePlanFeatureResponse;
import com.skillsprint.dto.response.subscription.ServicePlanResponse;
import com.skillsprint.service.subscription.AdminServicePlanService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/subscription-plans")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminServicePlanController {

    AdminServicePlanService adminServicePlanService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ServicePlanResponse>>> getPlans() {
        List<ServicePlanResponse> response = adminServicePlanService.getPlans();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách gói thành công", response));
    }

    @GetMapping("/features")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ServicePlanFeatureResponse>>> getFeatures() {
        List<ServicePlanFeatureResponse> response = adminServicePlanService.getFeatures();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tính năng thành công", response));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminAuditLogResponse>>> getAuditLogs() {
        List<AdminAuditLogResponse> response = adminServicePlanService.getAuditLogs();
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử chỉnh sửa gói thành công", response));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServicePlanResponse>> createPlan(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateServicePlanRequest request
    ) {
        ServicePlanResponse response = adminServicePlanService.createPlan(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Tạo gói thành công", response));
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServicePlanResponse>> getPlan(@PathVariable UUID planId) {
        ServicePlanResponse response = adminServicePlanService.getPlan(planId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin gói thành công", response));
    }

    @PatchMapping("/{planId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServicePlanResponse>> updatePlan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateServicePlanRequest request
    ) {
        ServicePlanResponse response = adminServicePlanService.updatePlan(jwt.getSubject(), planId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật gói thành công", response));
    }

    @PatchMapping("/{planId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServicePlanResponse>> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateServicePlanStatusRequest request
    ) {
        ServicePlanResponse response = adminServicePlanService.updateStatus(jwt.getSubject(), planId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái gói thành công", response));
    }

    @PutMapping("/{planId}/features")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ServicePlanResponse>> updateFeatures(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID planId,
            @Valid @RequestBody UpdatePlanFeaturesRequest request
    ) {
        ServicePlanResponse response = adminServicePlanService.updateFeatures(jwt.getSubject(), planId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tính năng của gói thành công", response));
    }
}
