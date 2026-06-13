package com.skillsprint.dto.response.admin;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaintenanceResponse {

    UUID maintenanceId;
    boolean enabled;
    boolean active;
    String message;
    Instant startAt;
    Instant endAt;
    String updatedBy;
    Instant updatedAt;
}
