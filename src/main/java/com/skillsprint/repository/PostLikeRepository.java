package com.skillsprint.repository;

import com.skillsprint.entity.PostLike;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    boolean existsByPostPostIdAndUserUserId(UUID postId, String userId);

    Optional<PostLike> findByPostPostIdAndUserUserId(UUID postId, String userId);
}
