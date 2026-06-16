package com.skillsprint.repository;

import com.skillsprint.entity.Feedback;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    List<Feedback> findByUserUserIdOrderByCreatedAtDesc(String userId);

    Optional<Feedback> findByFeedbackIdAndUserUserId(UUID feedbackId, String userId);

    @Query(
            value = """
                    select feedback.*
                    from feedbacks feedback
                    join users app_user on app_user.user_id = feedback.user_id
                    where (cast(:type as text) is null or feedback.type = cast(:type as text))
                      and (cast(:status as text) is null or feedback.status = cast(:status as text))
                      and (
                            cast(:searchPattern as text) is null
                            or app_user.email ilike cast(:searchPattern as text)
                            or app_user.full_name ilike cast(:searchPattern as text)
                            or feedback.title ilike cast(:searchPattern as text)
                            or feedback.content ilike cast(:searchPattern as text)
                      )
                    """,
            countQuery = """
                    select count(*)
                    from feedbacks feedback
                    join users app_user on app_user.user_id = feedback.user_id
                    where (cast(:type as text) is null or feedback.type = cast(:type as text))
                      and (cast(:status as text) is null or feedback.status = cast(:status as text))
                      and (
                            cast(:searchPattern as text) is null
                            or app_user.email ilike cast(:searchPattern as text)
                            or app_user.full_name ilike cast(:searchPattern as text)
                            or feedback.title ilike cast(:searchPattern as text)
                            or feedback.content ilike cast(:searchPattern as text)
                      )
                    """,
            nativeQuery = true
    )
    Page<Feedback> searchAdminFeedback(
            @Param("type") String type,
            @Param("status") String status,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}
