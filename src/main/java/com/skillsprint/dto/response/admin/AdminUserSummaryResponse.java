package com.skillsprint.dto.response.admin;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserSummaryResponse {

    long totalUsers;
    long activeUsers;
    long learnerUsers;
    long adminUsers;
}
