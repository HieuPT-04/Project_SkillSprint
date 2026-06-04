package com.skillsprint.repository;

import com.skillsprint.entity.QuizAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    Optional<QuizAttempt> findFirstByQuizQuizIdAndUserUserIdOrderBySubmittedAtDesc(UUID quizId, String userId);
}
