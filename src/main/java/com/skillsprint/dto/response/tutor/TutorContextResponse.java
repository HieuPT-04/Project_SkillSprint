package com.skillsprint.dto.response.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TutorContextResponse {

    String scope;
    UUID workspaceId;
    String workspaceName;
    UUID matchedStepId;
    String matchedStepTitle;
}
