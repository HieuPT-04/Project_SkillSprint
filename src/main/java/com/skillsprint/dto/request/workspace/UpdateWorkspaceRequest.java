package com.skillsprint.dto.request.workspace;

import com.skillsprint.enums.workspace.WorkspaceStatus;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateWorkspaceRequest {

    @Size(max = 255)
    String name;

    @Size(max = 1000)
    String description;

    WorkspaceStatus status;
}
