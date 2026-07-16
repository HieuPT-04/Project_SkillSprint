package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceQuizRequest;
import com.skillsprint.dto.request.marketplace.SubmitMarketplaceChallengeRequest;
import com.skillsprint.dto.response.marketplace.*;
import com.skillsprint.entity.*;
import com.skillsprint.enums.marketplace.*;
import com.skillsprint.exception.*;
import com.skillsprint.repository.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceChallengeService {
    static final long SUSPICIOUS_DURATION_SECONDS = 5;
    MarketplacePurchaseRepository purchaseRepository;
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    MarketplaceQuizAttemptRepository attemptRepository;
    UserRepository userRepository;
    MarketplaceChallengeSessionRepository sessionRepository;
    MarketplacePackVersionService packVersionService;

    @Transactional
    public MarketplaceChallengeSessionResponse start(String userId, UUID itemId) {
        if (!purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(userId, itemId,
                MarketplacePurchaseStatus.ACTIVE))
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn chưa mua Quiz Pack này");
        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        Instant now = Instant.now();
        MarketplaceChallengeSession session = new MarketplaceChallengeSession();
        session.setItem(snapshot.getItem());
        session.setPackVersion(packVersionService.findByItemId(itemId).orElse(null));
        session.setUser(
                userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND)));
        session.setStartedAt(now);
        session.setExpiresAt(now.plusSeconds(3600));
        session = sessionRepository.save(session);
        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        return MarketplaceChallengeSessionResponse.builder().sessionId(session.getSessionId())
                .packId(identity.packId()).versionId(identity.versionId()).versionNo(identity.versionNo())
                .startedAt(now)
                .expiresAt(session.getExpiresAt()).build();
    }

    @Transactional
    public MarketplaceQuizAttemptResponse submit(String userId, UUID itemId,
            SubmitMarketplaceChallengeRequest request) {
        if (!purchaseRepository.existsByUserUserIdAndItemItemIdAndStatus(userId, itemId,
                MarketplacePurchaseStatus.ACTIVE))
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn chưa mua Quiz Pack này");
        MarketplaceChallengeSession session = sessionRepository
                .findBySessionIdAndUserUserId(request.getSessionId(), userId)
                .filter(value -> value.getItem().getItemId().equals(itemId) && value.getCompletedAt() == null
                        && value.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR,
                        "Phiên thử thách không hợp lệ hoặc đã hết hạn"));
        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        Map<UUID, UUID> answers = new HashMap<>();
        for (var a : request.getAnswers()) {
            if (answers.put(a.getQuestionId(), a.getSelectedOptionId()) != null)
                throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER);
        }
        Map<UUID, UUID> correct = new HashMap<>();
        for (JsonNode chapter : snapshot.getContent().path("chapters"))
            for (JsonNode q : chapter.path("quiz").path("questions")) {
                UUID qid = UUID.fromString(q.path("questionId").asText());
                UUID oid = null;
                for (JsonNode o : q.path("options"))
                    if (o.path("correct").asBoolean()) {
                        if (oid != null)
                            throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER);
                        oid = UUID.fromString(o.path("optionId").asText());
                    }
                if (oid == null)
                    throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER);
                correct.put(qid, oid);
            }
        if (!answers.keySet().equals(correct.keySet()))
            throw new AppException(ErrorCode.QUIZ_INVALID_ANSWER, "Cần trả lời toàn bộ Quiz Pack");
        int hit = 0;
        for (var e : answers.entrySet())
            if (e.getValue().equals(correct.get(e.getKey())))
                hit++;
        long duration = java.time.Duration.between(session.getStartedAt(), Instant.now()).getSeconds();
        MarketplaceQuizAttempt attempt = new MarketplaceQuizAttempt();
        attempt.setItem(snapshot.getItem());
        attempt.setPackVersion(session.getPackVersion());
        attempt.setUser(
                userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND)));
        attempt.setAttemptType(MarketplaceQuizAttemptType.RANKED);
        attempt.setCorrectCount(hit);
        attempt.setQuestionCount(correct.size());
        attempt.setScore((int) Math.round(hit * 100.0 / correct.size()));
        attempt.setDurationSeconds(duration);
        attempt.setSuspicious(duration < SUSPICIOUS_DURATION_SECONDS);
        attempt.setCompletedAt(Instant.now());
        attempt = attemptRepository.save(attempt);
        session.setCompletedAt(attempt.getCompletedAt());
        sessionRepository.save(session);
        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        return MarketplaceQuizAttemptResponse.builder().attemptId(attempt.getAttemptId()).itemId(itemId)
                .packId(identity.packId()).versionId(identity.versionId()).versionNo(identity.versionNo())
                .score(attempt.getScore()).correctCount(hit).questionCount(correct.size())
                .durationSeconds(attempt.getDurationSeconds()).completedAt(attempt.getCompletedAt()).build();
    }

    @Transactional(readOnly = true)
    public List<MarketplaceLeaderboardEntryResponse> leaderboard(UUID itemId) {
        Map<String, MarketplaceQuizAttempt> best = new LinkedHashMap<>();
        for (MarketplaceQuizAttempt a : attemptRepository
                .findByItemItemIdAndAttemptTypeAndSuspiciousFalseOrderByScoreDescDurationSecondsAscCompletedAtAsc(
                        itemId, MarketplaceQuizAttemptType.RANKED))
            best.putIfAbsent(a.getUser().getUserId(), a);
        List<MarketplaceLeaderboardEntryResponse> out = new ArrayList<>();
        int rank = 1;
        for (MarketplaceQuizAttempt a : best.values()) {
            if (rank > 10)
                break;
            out.add(MarketplaceLeaderboardEntryResponse.builder().rank(rank++).userName(a.getUser().getFullName())
                    .score(a.getScore()).durationSeconds(a.getDurationSeconds()).completedAt(a.getCompletedAt())
                    .build());
        }
        return out;
    }
}
