package com.skillsprint.repository;

import com.skillsprint.entity.PostLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPostPostIdAndUserUserId(UUID postId, String userId);

    Optional<PostLike> findByPostPostIdAndUserUserId(UUID postId, String userId);

    @Query("""
            select like.post.postId
            from PostLike like
            where like.post.postId in :postIds
              and like.user.userId = :userId
            """)
    List<UUID> findLikedPostIds(
            @Param("postIds") Collection<UUID> postIds,
            @Param("userId") String userId
    );
}
