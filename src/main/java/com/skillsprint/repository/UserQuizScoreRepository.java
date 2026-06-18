package com.skillsprint.repository;

import com.skillsprint.entity.UserQuizScore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserQuizScoreRepository extends JpaRepository<UserQuizScore, UUID> {

    Optional<UserQuizScore> findByUserUserIdAndQuizQuizId(String userId, UUID quizId);
}
