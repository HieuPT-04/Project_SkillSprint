package com.skillsprint.dto.response.community;

import com.skillsprint.enums.community.PostCommentStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostCommentResponse {

    private UUID commentId;
    private UUID postId;
    private CommunityAuthorResponse author;
    private String content;
    private PostCommentStatus status;
    private int reportCount;
    private String adminNote;
    private Instant createdAt;
    private Instant updatedAt;
}
