package com.skillsprint.repository;

import com.skillsprint.entity.QuizAttemptAnswer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, UUID> {

    List<QuizAttemptAnswer> findByAttemptAttemptId(UUID attemptId);
}
