package com.skillsprint.dto.response.points;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MyPointsResponse {

    int totalPoints;
    int weeklyPoints;
    int monthlyPoints;
    int streakDays;
    LocalDate lastPointDate;
    Integer weeklyRank;
    Integer monthlyRank;
    Integer allTimeRank;
}
