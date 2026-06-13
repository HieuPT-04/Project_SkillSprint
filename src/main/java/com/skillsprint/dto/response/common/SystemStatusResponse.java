package com.skillsprint.dto.response.common;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SystemStatusResponse {

    boolean maintenance;
    String message;
    Instant startAt;
    Instant endAt;
}
