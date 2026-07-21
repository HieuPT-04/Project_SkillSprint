package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.auth.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);

    long countByCreatedAtAfter(Instant createdAt);

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByEmailVerified(boolean emailVerified);

    @Query("""
            select count(user)
            from User user
            where lower(user.userId) like lower(concat('%', :search, '%'))
               or lower(user.email) like lower(concat('%', :search, '%'))
               or lower(user.fullName) like lower(concat('%', :search, '%'))
            """)
    long countAdminUsersBySearch(@Param("search") String search);

    @Query("""
            select count(user)
            from User user
            where user.status = :status
              and (
                    lower(user.userId) like lower(concat('%', :search, '%'))
                 or lower(user.email) like lower(concat('%', :search, '%'))
                 or lower(user.fullName) like lower(concat('%', :search, '%'))
              )
            """)
    long countAdminUsersByStatusAndSearch(
            @Param("status") UserStatus status,
            @Param("search") String search
    );

    List<User> findTop5ByOrderByCreatedAtDesc();

    Page<User> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email,
            String fullName,
            Pageable pageable
    );

    @Query("""
            select user
            from User user
            where lower(user.userId) like lower(concat('%', :search, '%'))
               or lower(user.email) like lower(concat('%', :search, '%'))
               or lower(user.fullName) like lower(concat('%', :search, '%'))
            """)
    Page<User> searchAdminUsers(@Param("search") String search, Pageable pageable);

    @Query("""
            select user
            from User user
            where exists (
                select 1
                from UserRole userRole
                where userRole.user = user
                  and userRole.workspace is null
                  and userRole.role.roleName = :role
            )
            """)
    Page<User> findAdminUsersByRole(@Param("role") RoleName role, Pageable pageable);

    @Query("""
            select user
            from User user
            where exists (
                select 1
                from UserRole userRole
                where userRole.user = user
                  and userRole.workspace is null
                  and userRole.role.roleName = :role
            )
              and (
                    lower(user.userId) like lower(concat('%', :search, '%'))
                 or lower(user.email) like lower(concat('%', :search, '%'))
                 or lower(user.fullName) like lower(concat('%', :search, '%'))
              )
            """)
    Page<User> searchAdminUsersByRole(
            @Param("search") String search,
            @Param("role") RoleName role,
            Pageable pageable
    );
}
