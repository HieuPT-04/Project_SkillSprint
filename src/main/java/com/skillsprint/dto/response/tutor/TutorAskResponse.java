package com.skillsprint.dto.response.tutor;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TutorAskResponse {

    String answer;
    List<String> suggestedQuestions;
    String confidence;
    TutorContextResponse context;
}
