package com.skillsprint.repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
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
}
