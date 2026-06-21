package com.skillsprint.dto.response.community;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityChatMessageResponse {

    private UUID messageId;
    private UUID roomId;
    private CommunityAuthorResponse sender;
    private String content;
    private String rawContent;
    private boolean hidden;
    private int reportCount;
    private String adminNote;
    private Instant sentAt;
}
