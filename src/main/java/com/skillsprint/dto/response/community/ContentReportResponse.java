package com.skillsprint.dto.response.community;

import com.skillsprint.enums.community.ContentReportStatus;
import com.skillsprint.enums.community.ContentReportTargetType;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContentReportResponse {

    private UUID reportId;
    private ContentReportTargetType targetType;
    private UUID targetId;
    private CommunityAuthorResponse reporter;
    private String reason;
    private ContentReportStatus status;
    private String adminNote;
    private Instant reviewedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
