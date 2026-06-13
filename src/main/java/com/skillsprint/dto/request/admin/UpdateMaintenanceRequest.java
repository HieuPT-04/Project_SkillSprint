package com.skillsprint.dto.request.admin;

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
public class UpdateMaintenanceRequest {

    Boolean enabled;
    String message;
    Boolean clearSchedule;
    Instant startAt;
    Instant endAt;
}
