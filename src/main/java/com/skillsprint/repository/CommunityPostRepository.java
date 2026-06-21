package com.skillsprint.repository;

import com.skillsprint.entity.CommunityPost;
import com.skillsprint.enums.community.CommunityPostStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    @Query("""
            select post
            from CommunityPost post
            where post.status = :status
              and (cast(:search as text) is null or lower(post.content) like lower(concat('%', cast(:search as text), '%')))
              and (cast(:hashtag as text) is null or lower(post.hashtags) like lower(concat('%', cast(:hashtag as text), '%')))
            """)
    @EntityGraph(attributePaths = "author")
    Page<CommunityPost> searchByStatus(
            @Param("status") CommunityPostStatus status,
            @Param("search") String search,
            @Param("hashtag") String hashtag,
            Pageable pageable
    );

    @Query("""
            select post
            from CommunityPost post
            where post.author.userId = :userId
              and post.status <> com.skillsprint.enums.community.CommunityPostStatus.DELETED
              and (cast(:status as text) is null or post.status = :status)
            """)
    @EntityGraph(attributePaths = "author")
    Page<CommunityPost> findMyPosts(
            @Param("userId") String userId,
            @Param("status") CommunityPostStatus status,
            Pageable pageable
    );

    @Query("""
            select post
            from CommunityPost post
            where (cast(:status as text) is null or post.status = :status)
              and (
                    cast(:search as text) is null
                    or lower(post.content) like lower(concat('%', cast(:search as text), '%'))
                    or lower(post.author.email) like lower(concat('%', cast(:search as text), '%'))
                    or lower(post.author.fullName) like lower(concat('%', cast(:search as text), '%'))
              )
            """)
    @EntityGraph(attributePaths = "author")
    Page<CommunityPost> searchAdmin(
            @Param("status") CommunityPostStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
