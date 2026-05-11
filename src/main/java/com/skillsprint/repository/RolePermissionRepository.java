package com.skillsprint.repository;

import java.util.List;
import java.util.UUID;

import com.skillsprint.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByRoleRoleId(UUID roleId);

    List<RolePermission> findByPermissionPermissionId(UUID permissionId);

    boolean existsByRoleRoleIdAndPermissionPermissionId(UUID roleId, UUID permissionId);
}
