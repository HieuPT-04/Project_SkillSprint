package com.skillsprint.repository;

import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.Role;
import com.skillsprint.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByRoleName(RoleName roleName);

    boolean existsByRoleName(RoleName roleName);
}
