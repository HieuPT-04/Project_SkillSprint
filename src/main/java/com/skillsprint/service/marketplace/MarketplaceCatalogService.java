package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.dto.response.marketplace.MarketplaceCatalogItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemDetailResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceItemRepository;
import com.skillsprint.repository.MarketplaceQuizPackSnapshotRepository;
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceCatalogService {

    static final int PREVIEW_QUESTION_LIMIT = 3;

    MarketplaceItemRepository marketplaceItemRepository;
    MarketplaceQuizPackSnapshotRepository snapshotRepository;
    MarketplaceReviewRepository reviewRepository;
    MarketplacePackVersionService packVersionService;
    S3PresignedUrlService s3PresignedUrlService;

    @Transactional(readOnly = true)
    public List<MarketplaceCatalogItemResponse> getPublishedItems(String subject) {
        List<MarketplaceItem> items = subject == null || subject.isBlank()
                ? marketplaceItemRepository.findByStatusOrderByPublishedAtDesc(MarketplaceItemStatus.PUBLISHED)
                : marketplaceItemRepository.findByStatusAndSubjectIgnoreCaseOrderByPublishedAtDesc(
                        MarketplaceItemStatus.PUBLISHED, subject.trim());
        Map<UUID, MarketplacePackVersionIdentity> identities =
                packVersionService.identitiesOf(items.stream().map(MarketplaceItem::getItemId).toList());
        Set<UUID> versionIds = identities.values().stream()
                .map(MarketplacePackVersionIdentity::versionId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, RatingSummary> versionRatings = versionRatings(versionIds);
        Set<UUID> legacyItemIds = items.stream()
                .map(MarketplaceItem::getItemId)
                .filter(itemId -> identities.getOrDefault(
                        itemId,
                        MarketplacePackVersionIdentity.EMPTY
                ).versionId() == null)
                .collect(Collectors.toSet());
        Map<UUID, RatingSummary> legacyRatings = legacyRatings(legacyItemIds);
        return items.stream()
                .map(item -> {
                    MarketplacePackVersionIdentity identity = identities.getOrDefault(
                            item.getItemId(),
                            MarketplacePackVersionIdentity.EMPTY
                    );
                    RatingSummary rating = identity.versionId() == null
                            ? legacyRatings.getOrDefault(item.getItemId(), RatingSummary.EMPTY)
                            : versionRatings.getOrDefault(identity.versionId(), RatingSummary.EMPTY);
                    return toCatalogResponse(item, identity, rating);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MarketplaceItemDetailResponse getPublishedItem(UUID itemId) {
        MarketplaceItem item = marketplaceItemRepository.findById(itemId)
                .filter(value -> value.getStatus() == MarketplaceItemStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));

        List<MarketplaceItemDetailResponse.ChapterPreview> chapters = new ArrayList<>();
        List<MarketplaceItemDetailResponse.QuestionPreview> questions = new ArrayList<>();
        for (JsonNode chapter : snapshot.getContent().path("chapters")) {
            JsonNode quizQuestions = chapter.path("quiz").path("questions");
            chapters.add(MarketplaceItemDetailResponse.ChapterPreview.builder()
                    .sequenceNo(chapter.path("sequenceNo").asInt())
                    .title(chapter.path("title").asText())
                    .summary(chapter.path("summary").isNull() ? null : chapter.path("summary").asText())
                    .questionCount(quizQuestions.size())
                    .build());
            for (JsonNode question : quizQuestions) {
                if (questions.size() == PREVIEW_QUESTION_LIMIT) {
                    break;
                }
                List<MarketplaceItemDetailResponse.OptionPreview> options = new ArrayList<>();
                for (JsonNode option : question.path("options")) {
                    options.add(MarketplaceItemDetailResponse.OptionPreview.builder()
                            .optionId(UUID.fromString(option.path("optionId").asText()))
                            .label(option.path("label").asText())
                            .text(option.path("text").asText())
                            .build());
                }
                questions.add(MarketplaceItemDetailResponse.QuestionPreview.builder()
                        .questionId(UUID.fromString(question.path("questionId").asText()))
                        .question(question.path("text").asText())
                        .options(options)
                        .build());
            }
        }

        MarketplacePackVersionIdentity identity = packVersionService.identityOf(itemId);
        RatingSummary rating = identity.versionId() == null
                ? legacyRatings(Set.of(itemId)).getOrDefault(itemId, RatingSummary.EMPTY)
                : versionRatings(Set.of(identity.versionId())).getOrDefault(
                        identity.versionId(),
                        RatingSummary.EMPTY
                );
        return MarketplaceItemDetailResponse.builder()
                .itemId(item.getItemId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .title(item.getTitle())
                .description(item.getDescription())
                .subject(item.getSubject())
                .creatorName(item.getCreator().getFullName())
                .creatorAvatarUrl(s3PresignedUrlService.createViewUrl(item.getCreator().getAvatarObjectKey()))
                .priceCoins(item.getPriceCoins())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .averageRating(rating.averageRating())
                .reviewCount(rating.reviewCount())
                .publishedAt(item.getPublishedAt())
                .chapters(chapters)
                .previewQuestions(questions)
                .build();
    }

    private MarketplaceCatalogItemResponse toCatalogResponse(
            MarketplaceItem item,
            MarketplacePackVersionIdentity identity,
            RatingSummary rating
    ) {
        MarketplaceQuizPackSnapshot snapshot = snapshotRepository.findByItemItemId(item.getItemId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_FOUND));
        return MarketplaceCatalogItemResponse.builder()
                .itemId(item.getItemId())
                .packId(identity.packId())
                .versionId(identity.versionId())
                .versionNo(identity.versionNo())
                .title(item.getTitle())
                .description(item.getDescription())
                .subject(item.getSubject())
                .creatorName(item.getCreator().getFullName())
                .creatorAvatarUrl(s3PresignedUrlService.createViewUrl(item.getCreator().getAvatarObjectKey()))
                .priceCoins(item.getPriceCoins())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .averageRating(rating.averageRating())
                .reviewCount(rating.reviewCount())
                .publishedAt(item.getPublishedAt())
                .build();
    }

    private Map<UUID, RatingSummary> versionRatings(Set<UUID> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.summarizeByVersionIds(versionIds).stream()
                .collect(Collectors.toMap(
                        MarketplaceReviewRepository.VersionRatingSummary::getVersionId,
                        this::ratingSummary
                ));
    }

    private Map<UUID, RatingSummary> legacyRatings(Set<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.summarizeLegacyByItemIds(itemIds).stream()
                .collect(Collectors.toMap(
                        MarketplaceReviewRepository.LegacyRatingSummary::getItemId,
                        summary -> new RatingSummary(
                                summary.getAverageRating(),
                                Math.toIntExact(summary.getReviewCount())
                        )
                ));
    }

    private RatingSummary ratingSummary(MarketplaceReviewRepository.VersionRatingSummary summary) {
        return new RatingSummary(summary.getAverageRating(), Math.toIntExact(summary.getReviewCount()));
    }

    private record RatingSummary(double averageRating, int reviewCount) {
        private static final RatingSummary EMPTY = new RatingSummary(0D, 0);
    }
}
