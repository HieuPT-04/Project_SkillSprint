package com.skillsprint.repository;

import java.util.Optional;

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

    @Query("""
            select user
            from User user
            where (:search is null
                or lower(user.email) like lower(concat('%', :search, '%'))
                or lower(user.fullName) like lower(concat('%', :search, '%')))
            """)
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
}
