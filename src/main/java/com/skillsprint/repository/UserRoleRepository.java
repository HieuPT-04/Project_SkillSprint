package com.skillsprint.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

import com.skillsprint.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserUserId(String userId);

    List<UserRole> findByUserUserIdAndWorkspaceIsNull(String userId);

    List<UserRole> findByUserUserIdInAndWorkspaceIsNull(Collection<String> userIds);

    List<UserRole> findByRoleRoleId(UUID roleId);

    List<UserRole> findByWorkspaceWorkspaceId(UUID workspaceId);

    boolean existsByUserUserIdAndRoleRoleIdAndWorkspaceWorkspaceId(String userId, UUID roleId, UUID workspaceId);

    boolean existsByUserUserIdAndRoleRoleIdAndWorkspaceIsNull(String userId, UUID roleId);

    void deleteByUserUserIdAndWorkspaceIsNull(String userId);
}
