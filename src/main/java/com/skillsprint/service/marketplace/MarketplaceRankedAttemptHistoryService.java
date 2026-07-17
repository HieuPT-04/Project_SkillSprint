package com.skillsprint.service.marketplace;

import com.skillsprint.dto.response.marketplace.MarketplaceLeaderboardEntryResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceRankedAttemptHistoryResponse;
import com.skillsprint.entity.MarketplaceRankedAttempt;
import com.skillsprint.enums.marketplace.MarketplaceRankedAttemptStatus;
import com.skillsprint.repository.MarketplaceRankedAttemptRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceRankedAttemptHistoryService {

    MarketplaceRankedAttemptRepository attemptRepository;
    MarketplaceRankedQuizAccessService rankedQuizAccessService;

    @Transactional(readOnly = true)
    public List<MarketplaceLeaderboardEntryResponse> leaderboard(String buyerId, UUID versionId) {
        rankedQuizAccessService.requireRankedAccess(buyerId, versionId);
        List<MarketplaceRankedAttempt> attempts = attemptRepository
                .findTop10ByPackVersionVersionIdAndStatusAndSuspiciousFalseAndLeaderboardEligibleTrueOrderByScoreDescDurationSecondsAscCompletedAtAsc(
                        versionId, MarketplaceRankedAttemptStatus.COMPLETED);

        return IntStream.range(0, attempts.size())
                .mapToObj(index -> leaderboardEntry(attempts.get(index), index + 1))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MarketplaceRankedAttemptHistoryResponse> history(String buyerId, UUID versionId) {
        rankedQuizAccessService.requireRankedAccess(buyerId, versionId);
        return attemptRepository.findTop50ByBuyerUserIdAndPackVersionVersionIdOrderByStartedAtDesc(buyerId, versionId)
                .stream()
                .map(this::historyResponse)
                .toList();
    }

    private MarketplaceLeaderboardEntryResponse leaderboardEntry(MarketplaceRankedAttempt attempt, int rank) {
        return MarketplaceLeaderboardEntryResponse.builder()
                .rank(rank)
                .userName(attempt.getBuyer().getFullName())
                .score(attempt.getScore())
                .durationSeconds(attempt.getDurationSeconds())
                .completedAt(attempt.getCompletedAt())
                .build();
    }

    private MarketplaceRankedAttemptHistoryResponse historyResponse(MarketplaceRankedAttempt attempt) {
        return MarketplaceRankedAttemptHistoryResponse.builder()
                .attemptId(attempt.getAttemptId())
                .attemptDate(attempt.getAttemptDate())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus())
                .score(attempt.getScore())
                .correctCount(attempt.getCorrectCount())
                .questionCount(attempt.getDefinition().getTotalQuestionCount())
                .durationSeconds(attempt.getDurationSeconds())
                .startedAt(attempt.getStartedAt())
                .expiresAt(attempt.getExpiresAt())
                .completedAt(attempt.getCompletedAt())
                .leaderboardEligible(attempt.isLeaderboardEligible())
                .build();
    }
}
