package com.skillsprint.dto.response.subscription;

import com.skillsprint.enums.plan.SubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CurrentSubscriptionResponse {

    UUID subscriptionId;
    UserServicePlanResponse plan;
    LocalDate startDate;
    LocalDate endDate;
    Instant startAt;
    Instant endAt;
    SubscriptionStatus status;
    Instant createdAt;
}
