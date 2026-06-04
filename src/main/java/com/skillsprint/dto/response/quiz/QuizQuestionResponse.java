package com.skillsprint.dto.response.quiz;

import com.skillsprint.enums.quiz.QuizQuestionType;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizQuestionResponse {

    UUID questionId;
    QuizQuestionType type;
    String question;
    Integer sequenceNo;
    List<QuizOptionResponse> options;
}
