package com.skillsprint.controller;

import com.skillsprint.common.ApiResponse;
import com.skillsprint.dto.response.common.SystemStatusResponse;
import com.skillsprint.service.system.SystemMaintenanceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemStatusController {

    SystemMaintenanceService maintenanceService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SystemStatusResponse>> getStatus() {
        SystemStatusResponse response = maintenanceService.getSystemStatus();
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái hệ thống thành công", response));
    }
}
