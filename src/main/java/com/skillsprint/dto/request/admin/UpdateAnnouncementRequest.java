package com.skillsprint.dto.request.admin;

import com.skillsprint.enums.system.AnnouncementType;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateAnnouncementRequest {

    Boolean enabled;
    String title;
    String message;
    AnnouncementType type;
    Boolean clearSchedule;
    Instant startAt;
    Instant endAt;
}
