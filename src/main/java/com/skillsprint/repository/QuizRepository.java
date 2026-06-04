package com.skillsprint.repository;

import com.skillsprint.entity.Quiz;
import com.skillsprint.enums.quiz.QuizStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findFirstByRoadmapStepStepIdAndUserUserIdAndStatus(
            UUID stepId,
            String userId,
            QuizStatus status
    );

    Optional<Quiz> findByQuizIdAndUserUserId(UUID quizId, String userId);
}
