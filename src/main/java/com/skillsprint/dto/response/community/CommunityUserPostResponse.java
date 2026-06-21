package com.skillsprint.dto.response.community;

import com.skillsprint.enums.community.CommunityPostStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityUserPostResponse {

    private UUID postId;
    private CommunityAuthorResponse author;
    private String content;
    private List<String> hashtags;
    private CommunityPostStatus status;
    private int likeCount;
    private int commentCount;
    private boolean likedByMe;
    private Instant createdAt;
    private Instant updatedAt;
}
