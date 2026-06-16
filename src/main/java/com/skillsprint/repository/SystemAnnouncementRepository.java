package com.skillsprint.repository;

import com.skillsprint.entity.SystemAnnouncement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemAnnouncementRepository extends JpaRepository<SystemAnnouncement, UUID> {

    Optional<SystemAnnouncement> findTopByOrderByUpdatedAtDesc();
}
