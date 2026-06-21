package com.skillsprint.dto.response.community;

import com.skillsprint.enums.community.CommunityPinItemType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityPinResponse {

    private UUID pinId;
    private UUID roomId;
    private CommunityPinItemType itemType;
    private String title;
    private String content;
    private String linkUrl;
    private UUID messageId;
    private CommunityAuthorResponse pinnedBy;
    private int displayOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
