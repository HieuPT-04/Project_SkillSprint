package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.dto.response.marketplace.MarketplaceCatalogItemResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceItemDetailResponse;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.MarketplaceReview;
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
import java.util.UUID;
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
        return items.stream()
                .map(item -> toCatalogResponse(
                        item,
                        identities.getOrDefault(item.getItemId(), MarketplacePackVersionIdentity.EMPTY)))
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
                .averageRating(averageRating(itemId))
                .reviewCount(reviewCount(itemId))
                .publishedAt(item.getPublishedAt())
                .chapters(chapters)
                .previewQuestions(questions)
                .build();
    }

    private MarketplaceCatalogItemResponse toCatalogResponse(
            MarketplaceItem item,
            MarketplacePackVersionIdentity identity
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
                .priceCoins(item.getPriceCoins())
                .chapterCount(snapshot.getChapterCount())
                .quizCount(snapshot.getQuizCount())
                .questionCount(snapshot.getQuestionCount())
                .averageRating(averageRating(item.getItemId()))
                .reviewCount(reviewCount(item.getItemId()))
                .publishedAt(item.getPublishedAt())
                .build();
    }

    private double averageRating(UUID itemId) {
        List<MarketplaceReview> reviews = reviewRepository.findByItemItemId(itemId);
        return reviews.isEmpty() ? 0 : reviews.stream().mapToInt(MarketplaceReview::getRating).average().orElse(0);
    }

    private int reviewCount(UUID itemId) {
        return reviewRepository.findByItemItemId(itemId).size();
    }
}
