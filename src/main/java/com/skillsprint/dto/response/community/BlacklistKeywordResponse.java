package com.skillsprint.dto.response.community;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BlacklistKeywordResponse {

    private Long wordId;
    private String keyword;
    private CommunityAuthorResponse createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
