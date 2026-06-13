package com.skillsprint.service.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.admin.UpdateMaintenanceRequest;
import com.skillsprint.dto.response.admin.MaintenanceResponse;
import com.skillsprint.dto.response.common.SystemStatusResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.SystemMaintenance;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.SystemMaintenanceRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SystemMaintenanceService {

    static final String DEFAULT_MESSAGE = "SkillSprint đang bảo trì. Vui lòng quay lại sau.";

    SystemMaintenanceRepository maintenanceRepository;
    UserRepository userRepository;
    BusinessActivityLogRepository activityLogRepository;
    ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MaintenanceResponse getMaintenance() {
        return toMaintenanceResponse(currentOrDefault());
    }

    @Transactional(readOnly = true)
    public SystemStatusResponse getSystemStatus() {
        SystemMaintenance maintenance = currentOrDefault();
        return SystemStatusResponse.builder()
                .maintenance(isActive(maintenance, Instant.now()))
                .message(resolveMessage(maintenance))
                .startAt(maintenance.getStartAt())
                .endAt(maintenance.getEndAt())
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isMaintenanceActive() {
        return maintenanceRepository.findTopByOrderByUpdatedAtDesc()
                .map(maintenance -> isActive(maintenance, Instant.now()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public String getActiveMessage() {
        return maintenanceRepository.findTopByOrderByUpdatedAtDesc()
                .map(this::resolveMessage)
                .orElse(DEFAULT_MESSAGE);
    }

    @Transactional
    public MaintenanceResponse updateMaintenance(String adminUserId, UpdateMaintenanceRequest request) {
        SystemMaintenance maintenance = maintenanceRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(SystemMaintenance::new);
        Map<String, Object> oldValue = snapshot(maintenance);

        if (request.getEnabled() != null) {
            maintenance.setEnabled(request.getEnabled());
        }
        if (request.getMessage() != null) {
            maintenance.setMessage(normalizeNullableText(request.getMessage()));
        }
        if (Boolean.TRUE.equals(request.getClearSchedule())) {
            maintenance.setStartAt(null);
            maintenance.setEndAt(null);
        }
        if (request.getStartAt() != null) {
            maintenance.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            maintenance.setEndAt(request.getEndAt());
        }

        validateTimeRange(maintenance);

        if (adminUserId != null && !adminUserId.isBlank()) {
            userRepository.findById(adminUserId).ifPresent(maintenance::setUpdatedBy);
        }

        SystemMaintenance savedMaintenance = maintenanceRepository.save(maintenance);
        logActivity(adminUserId, savedMaintenance, oldValue, snapshot(savedMaintenance));
        return toMaintenanceResponse(savedMaintenance);
    }

    private SystemMaintenance currentOrDefault() {
        return maintenanceRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(SystemMaintenance::new);
    }

    private MaintenanceResponse toMaintenanceResponse(SystemMaintenance maintenance) {
        User updatedBy = maintenance.getUpdatedBy();
        return MaintenanceResponse.builder()
                .maintenanceId(maintenance.getMaintenanceId())
                .enabled(maintenance.isEnabled())
                .active(isActive(maintenance, Instant.now()))
                .message(resolveMessage(maintenance))
                .startAt(maintenance.getStartAt())
                .endAt(maintenance.getEndAt())
                .updatedBy(updatedBy == null ? null : updatedBy.getUserId())
                .updatedAt(maintenance.getUpdatedAt())
                .build();
    }

    private boolean isActive(SystemMaintenance maintenance, Instant now) {
        if (!maintenance.isEnabled()) {
            return false;
        }
        if (maintenance.getStartAt() != null && now.isBefore(maintenance.getStartAt())) {
            return false;
        }
        return maintenance.getEndAt() == null || !now.isAfter(maintenance.getEndAt());
    }

    private String resolveMessage(SystemMaintenance maintenance) {
        if (maintenance.getMessage() == null || maintenance.getMessage().isBlank()) {
            return DEFAULT_MESSAGE;
        }
        return maintenance.getMessage();
    }

    private String normalizeNullableText(String value) {
        if (value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateTimeRange(SystemMaintenance maintenance) {
        if (maintenance.getStartAt() != null
                && maintenance.getEndAt() != null
                && !maintenance.getEndAt().isAfter(maintenance.getStartAt())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thời gian kết thúc bảo trì phải sau thời gian bắt đầu");
        }
    }

    private Map<String, Object> snapshot(SystemMaintenance maintenance) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("maintenanceId", maintenance.getMaintenanceId());
        values.put("enabled", maintenance.isEnabled());
        values.put("message", maintenance.getMessage());
        values.put("startAt", maintenance.getStartAt());
        values.put("endAt", maintenance.getEndAt());
        return values;
    }

    private void logActivity(
            String adminUserId,
            SystemMaintenance maintenance,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        BusinessActivityLog log = new BusinessActivityLog();
        if (adminUserId != null && !adminUserId.isBlank()) {
            userRepository.findById(adminUserId).ifPresent(log::setUser);
        }
        log.setEntityType(BusinessEntityType.SYSTEM_MAINTENANCE);
        log.setEntityId(maintenance.getMaintenanceId());
        log.setActionType(resolveActionType(oldValue, newValue));
        log.setTitle("Cập nhật chế độ bảo trì");
        log.setDescription("Admin cập nhật trạng thái bảo trì hệ thống");
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));
        activityLogRepository.save(log);
    }

    private BusinessActionType resolveActionType(Map<String, Object> oldValue, Map<String, Object> newValue) {
        boolean wasEnabled = Boolean.TRUE.equals(oldValue.get("enabled"));
        boolean enabled = Boolean.TRUE.equals(newValue.get("enabled"));
        if (!wasEnabled && enabled) {
            return BusinessActionType.MAINTENANCE_ENABLED;
        }
        if (wasEnabled && !enabled) {
            return BusinessActionType.MAINTENANCE_DISABLED;
        }
        return BusinessActionType.MAINTENANCE_UPDATED;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
