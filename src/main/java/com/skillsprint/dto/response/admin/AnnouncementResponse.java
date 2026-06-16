package com.skillsprint.dto.response.admin;

import com.skillsprint.enums.system.AnnouncementType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AnnouncementResponse {

    UUID announcementId;
    boolean enabled;
    boolean active;
    String title;
    String message;
    AnnouncementType type;
    Instant startAt;
    Instant endAt;
    String updatedBy;
    Instant updatedAt;
}
