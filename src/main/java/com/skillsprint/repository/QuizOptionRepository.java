package com.skillsprint.repository;

import com.skillsprint.entity.QuizOption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, UUID> {

    List<QuizOption> findByQuestionQuizQuizIdOrderByQuestionSequenceNoAscSequenceNoAsc(UUID quizId);
}
