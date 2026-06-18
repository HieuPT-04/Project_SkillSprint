package com.skillsprint.dto.response.points;

import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PointEventResponse {

    PointEventType eventType;
    PointSourceType sourceType;
    String sourceId;
    int points;
    String description;
    LocalDate eventDate;
    Instant createdAt;
}
