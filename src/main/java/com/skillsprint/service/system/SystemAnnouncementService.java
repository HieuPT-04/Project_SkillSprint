package com.skillsprint.service.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.dto.request.admin.UpdateAnnouncementRequest;
import com.skillsprint.dto.response.admin.AnnouncementResponse;
import com.skillsprint.dto.response.common.PublicAnnouncementResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.SystemAnnouncement;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.enums.system.AnnouncementType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.BusinessActivityLogRepository;
import com.skillsprint.repository.SystemAnnouncementRepository;
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
public class SystemAnnouncementService {

    static final String DEFAULT_TITLE = "Thông báo hệ thống";

    SystemAnnouncementRepository announcementRepository;
    UserRepository userRepository;
    BusinessActivityLogRepository activityLogRepository;
    ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PublicAnnouncementResponse getActivePublicAnnouncement() {
        SystemAnnouncement announcement = currentOrDefault();
        if (!isActive(announcement, Instant.now())) {
            return PublicAnnouncementResponse.builder()
                    .enabled(false)
                    .active(false)
                    .type(resolveType(announcement))
                    .build();
        }

        return PublicAnnouncementResponse.builder()
                .enabled(true)
                .active(true)
                .title(resolveTitle(announcement))
                .message(announcement.getMessage())
                .type(resolveType(announcement))
                .startAt(announcement.getStartAt())
                .endAt(announcement.getEndAt())
                .build();
    }

    @Transactional(readOnly = true)
    public AnnouncementResponse getAnnouncement() {
        return toAnnouncementResponse(currentOrDefault());
    }

    @Transactional
    public AnnouncementResponse updateAnnouncement(String adminUserId, UpdateAnnouncementRequest request) {
        SystemAnnouncement announcement = announcementRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(SystemAnnouncement::new);
        Map<String, Object> oldValue = snapshot(announcement);

        if (request.getEnabled() != null) {
            announcement.setEnabled(request.getEnabled());
        }
        if (request.getTitle() != null) {
            announcement.setTitle(normalizeNullableText(request.getTitle()));
        }
        if (request.getMessage() != null) {
            announcement.setMessage(normalizeNullableText(request.getMessage()));
        }
        if (request.getType() != null) {
            announcement.setType(request.getType());
        }
        if (Boolean.TRUE.equals(request.getClearSchedule())) {
            announcement.setStartAt(null);
            announcement.setEndAt(null);
        }
        if (request.getStartAt() != null) {
            announcement.setStartAt(request.getStartAt());
        }
        if (request.getEndAt() != null) {
            announcement.setEndAt(request.getEndAt());
        }

        validateTimeRange(announcement);
        validateEnabledContent(announcement);

        if (adminUserId != null && !adminUserId.isBlank()) {
            userRepository.findById(adminUserId).ifPresent(announcement::setUpdatedBy);
        }

        SystemAnnouncement savedAnnouncement = announcementRepository.save(announcement);
        logActivity(adminUserId, savedAnnouncement, oldValue, snapshot(savedAnnouncement));
        return toAnnouncementResponse(savedAnnouncement);
    }

    private SystemAnnouncement currentOrDefault() {
        return announcementRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(SystemAnnouncement::new);
    }

    private AnnouncementResponse toAnnouncementResponse(SystemAnnouncement announcement) {
        User updatedBy = announcement.getUpdatedBy();
        return AnnouncementResponse.builder()
                .announcementId(announcement.getAnnouncementId())
                .enabled(announcement.isEnabled())
                .active(isActive(announcement, Instant.now()))
                .title(resolveTitle(announcement))
                .message(announcement.getMessage())
                .type(resolveType(announcement))
                .startAt(announcement.getStartAt())
                .endAt(announcement.getEndAt())
                .updatedBy(updatedBy == null ? null : updatedBy.getUserId())
                .updatedAt(announcement.getUpdatedAt())
                .build();
    }

    private boolean isActive(SystemAnnouncement announcement, Instant now) {
        if (!announcement.isEnabled()) {
            return false;
        }
        if (announcement.getMessage() == null || announcement.getMessage().isBlank()) {
            return false;
        }
        if (announcement.getStartAt() != null && now.isBefore(announcement.getStartAt())) {
            return false;
        }
        return announcement.getEndAt() == null || !now.isAfter(announcement.getEndAt());
    }

    private String resolveTitle(SystemAnnouncement announcement) {
        if (announcement.getTitle() == null || announcement.getTitle().isBlank()) {
            return DEFAULT_TITLE;
        }
        return announcement.getTitle();
    }

    private AnnouncementType resolveType(SystemAnnouncement announcement) {
        return announcement.getType() == null ? AnnouncementType.INFO : announcement.getType();
    }

    private String normalizeNullableText(String value) {
        if (value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateTimeRange(SystemAnnouncement announcement) {
        if (announcement.getStartAt() != null
                && announcement.getEndAt() != null
                && !announcement.getEndAt().isAfter(announcement.getStartAt())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thời gian kết thúc thông báo phải sau thời gian bắt đầu");
        }
    }

    private void validateEnabledContent(SystemAnnouncement announcement) {
        if (announcement.isEnabled()
                && (announcement.getMessage() == null || announcement.getMessage().isBlank())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Nội dung thông báo không được để trống khi bật thông báo");
        }
    }

    private Map<String, Object> snapshot(SystemAnnouncement announcement) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("announcementId", announcement.getAnnouncementId());
        values.put("enabled", announcement.isEnabled());
        values.put("title", announcement.getTitle());
        values.put("message", announcement.getMessage());
        values.put("type", resolveType(announcement));
        values.put("startAt", announcement.getStartAt());
        values.put("endAt", announcement.getEndAt());
        return values;
    }

    private void logActivity(
            String adminUserId,
            SystemAnnouncement announcement,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        BusinessActivityLog log = new BusinessActivityLog();
        if (adminUserId != null && !adminUserId.isBlank()) {
            userRepository.findById(adminUserId).ifPresent(log::setUser);
        }
        log.setEntityType(BusinessEntityType.SYSTEM_ANNOUNCEMENT);
        log.setEntityId(announcement.getAnnouncementId());
        log.setActionType(resolveActionType(oldValue, newValue));
        log.setTitle("Cập nhật thông báo hệ thống");
        log.setDescription("Admin cập nhật thông báo công khai trên website");
        log.setOldValue(toJson(oldValue));
        log.setNewValue(toJson(newValue));
        activityLogRepository.save(log);
    }

    private BusinessActionType resolveActionType(Map<String, Object> oldValue, Map<String, Object> newValue) {
        boolean wasEnabled = Boolean.TRUE.equals(oldValue.get("enabled"));
        boolean enabled = Boolean.TRUE.equals(newValue.get("enabled"));
        if (!wasEnabled && enabled) {
            return BusinessActionType.ANNOUNCEMENT_ENABLED;
        }
        if (wasEnabled && !enabled) {
            return BusinessActionType.ANNOUNCEMENT_DISABLED;
        }
        return BusinessActionType.ANNOUNCEMENT_UPDATED;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
