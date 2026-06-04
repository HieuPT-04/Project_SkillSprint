package com.skillsprint.dto.response.quiz;

import com.skillsprint.enums.quiz.QuizStatus;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizResponse {

    UUID quizId;
    UUID stepId;
    String title;
    Integer passingScore;
    Integer questionCount;
    QuizStatus status;
    QuizAttemptResponse latestAttempt;
    List<QuizQuestionResponse> questions;
}
