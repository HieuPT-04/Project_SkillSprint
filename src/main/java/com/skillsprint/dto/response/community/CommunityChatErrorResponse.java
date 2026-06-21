package com.skillsprint.dto.response.community;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CommunityChatErrorResponse {

    private UUID roomId;
    private String message;
    private Instant timestamp;
}
