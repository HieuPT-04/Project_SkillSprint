package com.skillsprint.repository;

import com.skillsprint.entity.PostComment;
import com.skillsprint.enums.community.PostCommentStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    @Query("""
            select comment
            from PostComment comment
            where comment.post.postId = :postId
              and comment.status = :status
            """)
    Page<PostComment> findByPostAndStatus(
            @Param("postId") UUID postId,
            @Param("status") PostCommentStatus status,
            Pageable pageable
    );

    @Query("""
            select comment
            from PostComment comment
            where (:status is null or comment.status = :status)
              and (
                    cast(:search as text) is null
                    or lower(comment.content) like lower(concat('%', cast(:search as text), '%'))
                    or lower(comment.author.email) like lower(concat('%', cast(:search as text), '%'))
                    or lower(comment.author.fullName) like lower(concat('%', cast(:search as text), '%'))
              )
            """)
    Page<PostComment> searchAdmin(
            @Param("status") PostCommentStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
