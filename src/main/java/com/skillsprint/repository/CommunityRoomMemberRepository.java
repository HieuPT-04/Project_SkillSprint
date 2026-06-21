package com.skillsprint.repository;

import com.skillsprint.entity.CommunityRoomMember;
import com.skillsprint.enums.community.CommunityRoomStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityRoomMemberRepository extends JpaRepository<CommunityRoomMember, UUID> {

    Optional<CommunityRoomMember> findByRoomRoomIdAndUserUserId(UUID roomId, String userId);

    boolean existsByRoomRoomIdAndUserUserIdAndBannedFalse(UUID roomId, String userId);

    boolean existsByRoomRoomIdAndUserUserIdAndBannedFalseAndRoomStatus(
            UUID roomId,
            String userId,
            CommunityRoomStatus status
    );

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    List<CommunityRoomMember> findByRoomRoomIdInAndUserUserId(Collection<UUID> roomIds, String userId);

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    Page<CommunityRoomMember> findByRoomRoomIdAndBannedFalse(UUID roomId, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    List<CommunityRoomMember> findByRoomRoomIdAndBannedFalse(UUID roomId);

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    Page<CommunityRoomMember> findByUserUserIdAndBannedFalse(String userId, Pageable pageable);
}
