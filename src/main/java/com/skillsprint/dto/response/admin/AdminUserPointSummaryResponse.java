package com.skillsprint.dto.response.admin;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserPointSummaryResponse {

    String userId;
    String fullName;
    String email;
    String avatarObjectKey;
    int totalPoints;
    int weeklyPoints;
    int monthlyPoints;
    int streakDays;
    LocalDate lastPointDate;
    Integer weeklyRank;
    Integer monthlyRank;
    Integer allTimeRank;
}
