package com.skillsprint.dto.response.points;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LeaderboardEntryResponse {

    int rank;
    String userId;
    String fullName;
    String avatarObjectKey;
    int points;
    int streakDays;
}
