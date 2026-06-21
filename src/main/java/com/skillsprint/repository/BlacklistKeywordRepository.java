package com.skillsprint.repository;

import com.skillsprint.entity.BlacklistKeyword;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistKeywordRepository extends JpaRepository<BlacklistKeyword, Long> {

    boolean existsByKeyword(String keyword);

    Optional<BlacklistKeyword> findByKeyword(String keyword);
}
