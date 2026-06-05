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
                    :search is null
                    or lower(user.email) like lower(concat('%', :search, '%'))
                    or lower(user.fullName) like lower(concat('%', :search, '%'))
                    or lower(feedback.title) like lower(concat('%', :search, '%'))
              )
            """)
    Page<Feedback> searchAdminFeedback(
            @Param("type") FeedbackType type,
            @Param("status") FeedbackStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
