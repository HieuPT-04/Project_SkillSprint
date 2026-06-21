package com.skillsprint.repository;

import com.skillsprint.entity.CommunityRoomMember;
import com.skillsprint.enums.community.CommunityRoomMemberStatus;
import com.skillsprint.enums.community.CommunityRoomStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityRoomMemberRepository extends JpaRepository<CommunityRoomMember, UUID> {

    Optional<CommunityRoomMember> findByRoomRoomIdAndUserUserId(UUID roomId, String userId);

    @Query("""
            select case when count(member) > 0 then true else false end
            from CommunityRoomMember member
            where member.room.roomId = :roomId
              and member.user.userId = :userId
              and member.banned = false
              and (member.status is null or member.status = :activeStatus)
              and member.room.status = :roomStatus
            """)
    boolean existsActiveMembership(
            @Param("roomId") UUID roomId,
            @Param("userId") String userId,
            @Param("activeStatus") CommunityRoomMemberStatus activeStatus,
            @Param("roomStatus") CommunityRoomStatus roomStatus
    );

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    List<CommunityRoomMember> findByRoomRoomIdInAndUserUserId(Collection<UUID> roomIds, String userId);

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    @Query("""
            select member
            from CommunityRoomMember member
            where member.room.roomId = :roomId
              and member.banned = false
              and (member.status is null or member.status = :activeStatus)
            """)
    Page<CommunityRoomMember> findActiveByRoomId(
            @Param("roomId") UUID roomId,
            @Param("activeStatus") CommunityRoomMemberStatus activeStatus,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"room", "room.owner", "user"})
    @Query("""
            select member
            from CommunityRoomMember member
            where member.user.userId = :userId
              and member.banned = false
              and (member.status is null or member.status = :activeStatus)
            """)
    Page<CommunityRoomMember> findActiveByUserId(
            @Param("userId") String userId,
            @Param("activeStatus") CommunityRoomMemberStatus activeStatus,
            Pageable pageable
    );
}
