package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.admin.UpdateUserRoleRequest;
import com.skillsprint.dto.request.admin.UpdateUserStatusRequest;
import com.skillsprint.dto.response.admin.AdminUserResponse;
import com.skillsprint.dto.response.admin.AdminUserSummaryResponse;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.enums.auth.RoleName;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.service.user.AdminUserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminUserController {

    AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) ServicePlanType planType,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection
    ) {
        PageResponse<AdminUserResponse> response = adminUserService.getUsers(
                search, page, size, role, planType, sortBy, sortDirection
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserSummaryResponse>> getUserSummary(
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getUserSummary(search)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable String userId) {
        AdminUserResponse response = adminUserService.getUser(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        AdminUserResponse response = adminUserService.updateUserStatus(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái người dùng thành công", response));
    }

    @PatchMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        AdminUserResponse response = adminUserService.updateUserRole(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò người dùng thành công", response));
    }
}
