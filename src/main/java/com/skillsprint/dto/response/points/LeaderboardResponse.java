package com.skillsprint.dto.response.points;

import com.skillsprint.enums.points.LeaderboardPeriod;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaderboardResponse {

    LeaderboardPeriod period;
    LocalDate periodStart;
    LocalDate periodEnd;
    List<LeaderboardEntryResponse> entries;
}
