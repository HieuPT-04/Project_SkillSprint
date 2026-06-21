package com.skillsprint.repository;

import com.skillsprint.entity.CommunityPinnedItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPinnedItemRepository extends JpaRepository<CommunityPinnedItem, UUID> {

    List<CommunityPinnedItem> findByRoomRoomIdOrderByDisplayOrderAscCreatedAtDesc(UUID roomId);

    long countByRoomRoomId(UUID roomId);
}
