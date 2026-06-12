package com.skillsprint.dto.response.admin;

import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminAuditLogResponse {

    UUID logId;
    String adminUserId;
    String adminEmail;
    BusinessEntityType entityType;
    UUID entityId;
    BusinessActionType actionType;
    String title;
    String description;
    String oldValue;
    String newValue;
    String metadata;
    Instant createdAt;
}
