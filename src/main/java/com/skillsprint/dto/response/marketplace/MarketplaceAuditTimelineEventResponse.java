package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.log.BusinessActionType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarketplaceAuditTimelineEventResponse {
    UUID logId;
    BusinessActionType actionType;
    String title;
    String description;
    String actorUserId;
    String actorName;
    Instant occurredAt;
}
