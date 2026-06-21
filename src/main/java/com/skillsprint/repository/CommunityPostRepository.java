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
              and (:search is null or lower(post.content) like lower(concat('%', :search, '%')))
              and (:hashtag is null or lower(post.hashtags) like lower(concat('%', :hashtag, '%')))
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
              and (:status is null or post.status = :status)
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
            where (:status is null or post.status = :status)
              and (
                    :search is null
                    or lower(post.content) like lower(concat('%', :search, '%'))
                    or lower(post.author.email) like lower(concat('%', :search, '%'))
                    or lower(post.author.fullName) like lower(concat('%', :search, '%'))
              )
            """)
    @EntityGraph(attributePaths = "author")
    Page<CommunityPost> searchAdmin(
            @Param("status") CommunityPostStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
