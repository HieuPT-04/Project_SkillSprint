package com.skillsprint.repository;

import com.skillsprint.entity.CommunityRoomInvite;
import com.skillsprint.enums.community.CommunityRoomInviteStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityRoomInviteRepository extends JpaRepository<CommunityRoomInvite, UUID> {

    Optional<CommunityRoomInvite> findByRoomRoomIdAndInviteeUserIdAndStatus(
            UUID roomId,
            String inviteeUserId,
            CommunityRoomInviteStatus status
    );

    Page<CommunityRoomInvite> findByInviteeUserIdAndStatusAndExpiresAtAfter(
            String inviteeUserId,
            CommunityRoomInviteStatus status,
            Instant now,
            Pageable pageable
    );
}
