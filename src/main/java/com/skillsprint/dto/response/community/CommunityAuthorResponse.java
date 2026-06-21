package com.skillsprint.dto.response.community;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityAuthorResponse {

    private String userId;
    private String email;
    private String fullName;
    private String avatarObjectKey;
    private String avatarUrl;
}
