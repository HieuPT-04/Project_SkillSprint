package com.skillsprint.repository;

import java.util.Optional;
import java.util.UUID;

import com.skillsprint.entity.OnboardingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingProfileRepository extends JpaRepository<OnboardingProfile, UUID> {

    Optional<OnboardingProfile> findByWorkspaceWorkspaceId(UUID workspaceId);

    boolean existsByWorkspaceWorkspaceId(UUID workspaceId);
}
