package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.ReviewMarketplaceItemRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceAdminItemDetailResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceQualityJobResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceAdminService {

    MarketplaceItemRepository marketplaceItemRepository;
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    MarketplacePackVersionService packVersionService;
    MarketplaceQualityService qualityService;
    UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MarketplaceItemResponse> getItems(MarketplaceItemStatus status) {
        MarketplaceItemStatus effectiveStatus = status == null ? MarketplaceItemStatus.PENDING_REVIEW : status;
        List<MarketplaceItem> items = marketplaceItemRepository
                .findByStatusOrderByPublishedAtDesc(effectiveStatus);
        Map<UUID, MarketplaceQualityService.Summary> qualitySummaries = qualityService
                .summariesByLegacyItemIds(items.stream().map(MarketplaceItem::getItemId).toList());
        return items.stream()
                .map(item -> toResponse(item, qualitySummaries.getOrDefault(
                        item.getItemId(), MarketplaceQualityService.Summary.EMPTY)))
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceAdminItemDetailResponse getItemDetail(UUID itemId) {
        MarketplaceItem item = findItem(itemId);
        MarketplaceQuizPackSnapshot snapshot = findSnapshot(itemId);
        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        var version = packVersionService.findByItemId(itemId);
        var qualityJobHistory = version
                .map(qualityService::findRecentForAdmin)
                .orElse(List.of());
        var qualityJob = qualityJobHistory.stream().findFirst().orElse(null);

        com.fasterxml.jackson.databind.JsonNode content = snapshot.getContent();
        int chapterCount = snapshot.getChapterCount() != null ? snapshot.getChapterCount() : 0;
        int quizCount = snapshot.getQuizCount() != null ? snapshot.getQuizCount() : 0;
        int questionCount = snapshot.getQuestionCount() != null ? snapshot.getQuestionCount() : 0;

        if (content != null && content.has("chapters") && content.get("chapters").isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode chapters = (com.fasterxml.jackson.databind.node.ArrayNode) content.get("chapters");
            if (!chapters.isEmpty()) {
                chapterCount = chapters.size();
                int derivedQuizzes = 0;
                int derivedQuestions = 0;
                for (com.fasterxml.jackson.databind.JsonNode ch : chapters) {
                    if (ch.has("quizzes") && ch.get("quizzes").isArray()) {
                        com.fasterxml.jackson.databind.node.ArrayNode quizzes = (com.fasterxml.jackson.databind.node.ArrayNode) ch.get("quizzes");
                        derivedQuizzes += quizzes.size();
                        for (com.fasterxml.jackson.databind.JsonNode qz : quizzes) {
                            if (qz.has("questions") && qz.get("questions").isArray()) {
                                derivedQuestions += qz.get("questions").size();
                            } else if (qz.has("questionCount")) {
                                derivedQuestions += qz.get("questionCount").asInt();
                            }
                        }
                    } else if (ch.has("quizCount")) {
                        derivedQuizzes += ch.get("quizCount").asInt();
                    }
                }
                if (derivedQuizzes > 0) quizCount = derivedQuizzes;
                if (derivedQuestions > 0) questionCount = derivedQuestions;
            }
        }

        return MarketplaceAdminItemDetailResponse.builder()
                .itemId(item.getItemId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .creatorId(item.getCreator().getUserId())
                .creatorName(item.getCreator().getFullName())
                .title(item.getTitle())
                .description(item.getDescription())
                .subject(item.getSubject())
                .priceCoins(item.getPriceCoins())
                .chapterCount(chapterCount)
                .quizCount(quizCount)
                .questionCount(questionCount)
                .status(item.getStatus().name())
                .creatorValidationScore(item.getCreatorValidationScore())
                .qualityJob(qualityJob)
                .qualityJobHistory(qualityJobHistory.stream().skip(1).toList())
                .reviewNote(item.getReviewNote())
                .createdAt(item.getCreatedAt())
                .content(snapshot.getContent())
                .build();
    }

    @Transactional
    public MarketplaceQualityJobResponse queueQuality(UUID itemId) {
        MarketplaceItem item = findItem(itemId);
        if (item.getStatus() != MarketplaceItemStatus.PENDING_REVIEW) {
            throw new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
        }
        return qualityService.queueForAdmin(packVersionService.requireByItemId(itemId).getVersionId());
    }

    @Transactional
    public MarketplaceItemResponse review(String adminUserId, UUID itemId, ReviewMarketplaceItemRequest request) {
        MarketplaceItem item = findItem(itemId);
        validateTransition(item.getStatus(), request.getStatus());
        if (request.getStatus() == MarketplaceItemStatus.PUBLISHED) {
            qualityService.requireCurrentPass(packVersionService.requireByItemId(itemId));
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        // A rejected pack returns to DRAFT so the Creator can fix it, refresh the
        // snapshot, re-validate, and submit again; the review note stays visible.
        item.setStatus(request.getStatus() == MarketplaceItemStatus.REJECTED
                ? MarketplaceItemStatus.DRAFT
                : request.getStatus());
        if (request.getStatus() == MarketplaceItemStatus.REJECTED) {
            item.setCreatorValidationScore(null);
        }
        item.setReviewedBy(admin);
        item.setReviewNote(request.getReviewNote());
        item.setReviewedAt(Instant.now());
        item.setPublishedAt(request.getStatus() == MarketplaceItemStatus.PUBLISHED ? Instant.now() : null);
        item = marketplaceItemRepository.save(item);
        // Mirrors the moderation outcome onto Version 1, including the saleable
        // marker: publishing makes it saleable, suspending or rejecting clears it.
        packVersionService.syncFromLegacyItem(item, findSnapshot(itemId));
        MarketplaceQualityService.Summary qualitySummary = packVersionService.findByItemId(itemId)
                .map(qualityService::summary)
                .orElse(MarketplaceQualityService.Summary.EMPTY);
        return toResponse(item, qualitySummary);
    }

    private void validateTransition(MarketplaceItemStatus current, MarketplaceItemStatus target) {
        boolean valid = (current == MarketplaceItemStatus.PENDING_REVIEW
                && (target == MarketplaceItemStatus.PUBLISHED || target == MarketplaceItemStatus.REJECTED))
                || (current == MarketplaceItemStatus.PUBLISHED && target == MarketplaceItemStatus.SUSPENDED);
        if (!valid) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Trạng thái kiểm duyệt không hợp lệ");
        }
    }

    private MarketplaceItem findItem(UUID itemId) {
        return marketplaceItemRepository.findById(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
    }

    private MarketplaceQuizPackSnapshot findSnapshot(UUID itemId) {
        return snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
    }

    private MarketplaceItemResponse toResponse(
            MarketplaceItem item,
            MarketplaceQualityService.Summary qualitySummary
    ) {
        MarketplaceQuizPackSnapshot snapshot = findSnapshot(item.getItemId());
        MarketplacePackVersionIdentity identity = packVersionService.identityOf(item.getItemId());
        return MarketplaceItemResponse.builder()
                .itemId(item.getItemId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .sourceWorkspaceId(item.getSourceWorkspace().getWorkspaceId())
                .title(item.getTitle())
                .description(item.getDescription())
                .subject(item.getSubject())
                .priceCoins(item.getPriceCoins())
                .status(item.getStatus())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .creatorValidationScore(item.getCreatorValidationScore())
                .qualityStatus(qualitySummary.status())
                .qualityScore(qualitySummary.score())
                .qualityCurrent(qualitySummary.current())
                .reviewNote(item.getReviewNote())
                .createdAt(item.getCreatedAt())
                .publishedAt(item.getPublishedAt())
                .build();
    }
}
