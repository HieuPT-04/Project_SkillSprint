package com.skillsprint.repository;

import com.skillsprint.entity.CommunityChatMessage;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityChatMessageRepository extends JpaRepository<CommunityChatMessage, UUID> {

    Page<CommunityChatMessage> findByRoomRoomIdAndHiddenFalse(UUID roomId, Pageable pageable);

    Page<CommunityChatMessage> findByRoomRoomId(UUID roomId, Pageable pageable);
}
