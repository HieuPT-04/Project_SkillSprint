package com.skillsprint.repository;

import com.skillsprint.entity.CommunityRoomMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityRoomMemberRepository extends JpaRepository<CommunityRoomMember, UUID> {

    Optional<CommunityRoomMember> findByRoomRoomIdAndUserUserId(UUID roomId, String userId);

    boolean existsByRoomRoomIdAndUserUserIdAndBannedFalse(UUID roomId, String userId);

    Page<CommunityRoomMember> findByRoomRoomIdAndBannedFalse(UUID roomId, Pageable pageable);

    List<CommunityRoomMember> findByRoomRoomIdAndBannedFalse(UUID roomId);

    Page<CommunityRoomMember> findByUserUserIdAndBannedFalse(String userId, Pageable pageable);
}
