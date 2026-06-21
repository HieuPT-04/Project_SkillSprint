package com.skillsprint.dto.response.community;

import com.skillsprint.enums.community.CommunityRoomRole;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityRoomMemberResponse {

    private UUID memberId;
    private UUID roomId;
    private CommunityAuthorResponse user;
    private CommunityRoomRole role;
    private Instant muteUntil;
    private boolean banned;
    private Instant joinedAt;
    private Instant updatedAt;
}
