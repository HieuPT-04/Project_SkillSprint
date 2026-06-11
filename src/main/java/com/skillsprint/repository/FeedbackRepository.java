package com.skillsprint.repository;

import com.skillsprint.entity.Feedback;
import com.skillsprint.enums.feedback.FeedbackStatus;
import com.skillsprint.enums.feedback.FeedbackType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    @Query("""
            select feedback
            from Feedback feedback
            join feedback.user user
            where (:type is null or feedback.type = :type)
              and (:status is null or feedback.status = :status)
              and (
                    :searchPattern is null
                    or lower(user.email) like :searchPattern
                    or lower(user.fullName) like :searchPattern
                    or lower(feedback.title) like :searchPattern
              )
            """)
    Page<Feedback> searchAdminFeedback(
            @Param("type") FeedbackType type,
            @Param("status") FeedbackStatus status,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}
