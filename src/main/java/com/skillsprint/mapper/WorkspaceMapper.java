package com.skillsprint.mapper;

import com.skillsprint.dto.response.workspace.WorkspaceResponse;
import com.skillsprint.entity.StudyWorkspace;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceMapper {

    public WorkspaceResponse toWorkspaceResponse(StudyWorkspace workspace) {
        return WorkspaceResponse.builder()
                .workspaceId(workspace.getWorkspaceId())
                .name(workspace.getName())
                .description(workspace.getDescription())
                .status(workspace.getStatus())
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }
}
