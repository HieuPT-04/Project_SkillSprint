package com.skillsprint.dto.response.quiz;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QuizAttemptResponse {

    UUID attemptId;
    UUID quizId;
    Integer score;
    boolean passed;
    Integer correctAnswers;
    Integer totalQuestions;
    boolean canCompleteStep;
    String feedback;
    Instant submittedAt;
    List<QuizAnswerResultResponse> results;
}
