package com.skillsprint.repository;

import com.skillsprint.entity.CommunityRoom;
import com.skillsprint.enums.community.CommunityRoomMode;
import com.skillsprint.enums.community.CommunityRoomStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityRoomRepository extends JpaRepository<CommunityRoom, UUID> {

    long countByOwnerUserIdAndStatusNot(String ownerId, CommunityRoomStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CommunityRoom room
            set room.memberCount =
                case when room.memberCount + :delta < 0 then 0 else room.memberCount + :delta end
            where room.roomId = :roomId
            """)
    int adjustMemberCount(@Param("roomId") UUID roomId, @Param("delta") int delta);

    @Query("""
            select room
            from CommunityRoom room
            where room.status = com.skillsprint.enums.community.CommunityRoomStatus.ACTIVE
              and room.mode <> com.skillsprint.enums.community.CommunityRoomMode.PRIVATE
              and (:mode is null or room.mode = :mode)
              and (
                    cast(:search as text) is null
                    or lower(room.name) like lower(concat('%', cast(:search as text), '%'))
                    or lower(room.description) like lower(concat('%', cast(:search as text), '%'))
              )
            """)
    @EntityGraph(attributePaths = "owner")
    Page<CommunityRoom> searchDiscoverableRooms(
            @Param("mode") CommunityRoomMode mode,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select room
            from CommunityRoom room
            where (:status is null or room.status = :status)
              and (:mode is null or room.mode = :mode)
              and (
                    cast(:search as text) is null
                    or lower(room.name) like lower(concat('%', cast(:search as text), '%'))
                    or lower(room.description) like lower(concat('%', cast(:search as text), '%'))
                    or lower(room.owner.email) like lower(concat('%', cast(:search as text), '%'))
                    or lower(room.owner.fullName) like lower(concat('%', cast(:search as text), '%'))
              )
            """)
    @EntityGraph(attributePaths = "owner")
    Page<CommunityRoom> searchAdminRooms(
            @Param("status") CommunityRoomStatus status,
            @Param("mode") CommunityRoomMode mode,
            @Param("search") String search,
            Pageable pageable
    );
}
