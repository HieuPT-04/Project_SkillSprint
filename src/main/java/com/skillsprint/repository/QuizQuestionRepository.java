package com.skillsprint.repository;

import com.skillsprint.entity.QuizQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByQuizQuizIdOrderBySequenceNoAsc(UUID quizId);
}
