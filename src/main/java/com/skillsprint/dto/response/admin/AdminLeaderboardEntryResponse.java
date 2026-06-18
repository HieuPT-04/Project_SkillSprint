package com.skillsprint.dto.response.admin;

import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminLeaderboardEntryResponse {

    int rank;
    String userId;
    String fullName;
    String email;
    String avatarObjectKey;
    int points;
    int streakDays;
    LocalDate lastPointDate;
}
