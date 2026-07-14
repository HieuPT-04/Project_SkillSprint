package com.skillsprint.repository;
import com.skillsprint.entity.MarketplaceChallengeSession;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MarketplaceChallengeSessionRepository extends JpaRepository<MarketplaceChallengeSession,UUID> { Optional<MarketplaceChallengeSession> findBySessionIdAndUserUserId(UUID sessionId,String userId); }
