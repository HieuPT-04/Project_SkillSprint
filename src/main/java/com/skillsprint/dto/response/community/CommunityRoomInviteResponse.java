package com.skillsprint.dto.response.community;

import com.skillsprint.enums.community.CommunityRoomInviteStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityRoomInviteResponse {

    private UUID inviteId;
    private UUID roomId;
    private String roomName;
    private CommunityAuthorResponse inviter;
    private CommunityAuthorResponse invitee;
    private CommunityRoomInviteStatus status;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
