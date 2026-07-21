package com.skillsprint.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

import com.skillsprint.entity.UserRole;
import com.skillsprint.enums.auth.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserUserId(String userId);

    List<UserRole> findByUserUserIdAndWorkspaceIsNull(String userId);

    List<UserRole> findByUserUserIdInAndWorkspaceIsNull(Collection<String> userIds);

    List<UserRole> findByRoleRoleId(UUID roleId);

    List<UserRole> findByWorkspaceWorkspaceId(UUID workspaceId);

    boolean existsByUserUserIdAndRoleRoleIdAndWorkspaceWorkspaceId(String userId, UUID roleId, UUID workspaceId);

    boolean existsByUserUserIdAndRoleRoleIdAndWorkspaceIsNull(String userId, UUID roleId);

    @Query("""
            select count(distinct userRole.user.userId)
            from UserRole userRole
            where userRole.role.roleName = :role
              and userRole.workspace is null
            """)
    long countGlobalUsersByRole(@Param("role") RoleName role);

    @Query("""
            select count(distinct userRole.user.userId)
            from UserRole userRole
            where userRole.role.roleName = :role
              and userRole.workspace is null
              and (
                    lower(userRole.user.userId) like lower(concat('%', :search, '%'))
                 or lower(userRole.user.email) like lower(concat('%', :search, '%'))
                 or lower(userRole.user.fullName) like lower(concat('%', :search, '%'))
              )
            """)
    long countGlobalUsersByRoleAndSearch(
            @Param("role") RoleName role,
            @Param("search") String search
    );

    void deleteByUserUserIdAndWorkspaceIsNull(String userId);
}
