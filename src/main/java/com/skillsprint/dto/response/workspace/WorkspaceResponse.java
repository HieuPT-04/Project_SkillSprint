package com.skillsprint.dto.response.workspace;

import com.skillsprint.enums.workspace.WorkspaceStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkspaceResponse {

    UUID workspaceId;
    String name;
    String description;
    WorkspaceStatus status;
    Instant createdAt;
    Instant updatedAt;
}
