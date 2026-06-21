package com.skillsprint.dto.response.community;

import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.enums.community.CommunityRoomRole;
import com.skillsprint.enums.community.CommunityRoomStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityRoomResponse {

    private UUID roomId;
    private String name;
    private String description;
    private CommunityRoomMode mode;
    private CommunityRoomStatus status;
    private CommunityAuthorResponse owner;
    private int maxMembers;
    private int memberCount;
    private int reportCount;
    private CommunityRoomRole myRole;
    private boolean joined;
    private boolean banned;
    private String adminNote;
    private Instant createdAt;
    private Instant updatedAt;
}
