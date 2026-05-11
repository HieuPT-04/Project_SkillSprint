package com.skillsprint.repository;

import java.util.Optional;

import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByStatus(UserStatus status);
}
