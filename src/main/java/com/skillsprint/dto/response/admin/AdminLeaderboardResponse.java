package com.skillsprint.dto.response.admin;

import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.enums.points.LeaderboardPeriod;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminLeaderboardResponse {

    LeaderboardPeriod period;
    LocalDate periodStart;
    LocalDate periodEnd;
    PageResponse<AdminLeaderboardEntryResponse> entries;
}
