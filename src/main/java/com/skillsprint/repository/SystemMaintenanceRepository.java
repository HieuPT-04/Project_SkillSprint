package com.skillsprint.repository;

import com.skillsprint.entity.SystemMaintenance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemMaintenanceRepository extends JpaRepository<SystemMaintenance, UUID> {

    Optional<SystemMaintenance> findTopByOrderByUpdatedAtDesc();
}
