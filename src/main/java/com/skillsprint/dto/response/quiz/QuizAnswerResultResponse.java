package com.skillsprint.dto.response.quiz;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizAnswerResultResponse {

    UUID questionId;
    UUID selectedOptionId;
    boolean correct;
    String explanation;
}
