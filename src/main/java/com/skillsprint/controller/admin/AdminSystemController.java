package com.skillsprint.controller.admin;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.request.admin.UpdateMaintenanceRequest;
import com.skillsprint.dto.response.admin.MaintenanceResponse;
import com.skillsprint.service.system.SystemMaintenanceService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminSystemController {

    SystemMaintenanceService maintenanceService;

    @GetMapping("/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> getMaintenance() {
        MaintenanceResponse response = maintenanceService.getMaintenance();
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái bảo trì thành công", response));
    }

    @PatchMapping("/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> updateMaintenance(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateMaintenanceRequest request
    ) {
        MaintenanceResponse response = maintenanceService.updateMaintenance(jwt.getSubject(), request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái bảo trì thành công", response));
    }
}
