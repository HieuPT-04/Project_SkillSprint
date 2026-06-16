package com.skillsprint.dto.response.common;

import com.skillsprint.enums.system.AnnouncementType;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PublicAnnouncementResponse {

    boolean enabled;
    boolean active;
    String title;
    String message;
    AnnouncementType type;
    Instant startAt;
    Instant endAt;
}
