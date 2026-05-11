package com.skillsprint.repository;

import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByPermissionName(String permissionName);

    Optional<Permission> findByResourceAndAction(String resource, String action);

    boolean existsByPermissionName(String permissionName);
}
