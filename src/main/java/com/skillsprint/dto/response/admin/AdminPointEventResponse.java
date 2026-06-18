package com.skillsprint.dto.response.admin;

import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminPointEventResponse {

    PointEventType eventType;
    PointSourceType sourceType;
    String sourceId;
    int points;
    String description;
    UUID workspaceId;
    String workspaceName;
    LocalDate eventDate;
    Instant createdAt;
}
