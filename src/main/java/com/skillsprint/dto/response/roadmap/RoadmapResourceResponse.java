package com.skillsprint.dto.response.roadmap;

import com.skillsprint.enums.roadmap.ResourcePlatform;
import com.skillsprint.enums.roadmap.ResourceType;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoadmapResourceResponse {

    UUID resourceId;
    String title;
    ResourcePlatform platform;
    ResourceType resourceType;
    String searchQuery;
    String content;
    String url;
    String reason;
    boolean aiRecommended;
    Integer sequenceNo;
    Instant createdAt;
}
