package com.skillsprint.service.marketplace;

import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePackRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Pack / Pack Version foundation.
 *
 * <p>In this phase {@code marketplace_items} + {@code marketplace_quiz_pack_snapshots}
 * remain the write path, and every item mirrors onto exactly one pack and its
 * Version 1 — the same shape the V7 migration produced for legacy data. That keeps
 * the invariant "one item ⇔ one pack ⇔ one version" true for rows created after the
 * migration too, so the follow-up NOT NULL migration on the historical
 * {@code pack_version_id} columns stays reachable. Version-aware write endpoints and
 * the major/minor update flow belong to a later plan.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePackVersionService {

    static final int INITIAL_VERSION_NO = 1;

    MarketplacePackRepository packRepository;
    MarketplacePackVersionRepository versionRepository;

    /** Creates the pack and its Version 1 for a newly created legacy draft item. */
    @Transactional
    public MarketplacePackVersion createInitialVersion(MarketplaceItem item, MarketplaceQuizPackSnapshot snapshot) {
        MarketplacePack pack = new MarketplacePack();
        pack.setCreator(item.getCreator());
        pack.setSourceWorkspace(item.getSourceWorkspace());
        pack.setLegacyItemId(item.getItemId());
        pack = packRepository.save(pack);

        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setPack(pack);
        version.setVersionNo(INITIAL_VERSION_NO);
        version.setUpdateType(MarketplacePackUpdateType.MAJOR);
        version.setLegacyItemId(item.getItemId());
        copyMetadata(item, snapshot, version);
        return versionRepository.save(version);
    }

    /**
     * Mirrors the current legacy item and snapshot onto its version. No-op when the
     * item has no version, so an item predating the foundation cannot break a write
     * that used to succeed.
     */
    @Transactional
    public Optional<MarketplacePackVersion> syncFromLegacyItem(
            MarketplaceItem item,
            MarketplaceQuizPackSnapshot snapshot
    ) {
        return versionRepository.findByLegacyItemId(item.getItemId()).map(version -> {
            copyMetadata(item, snapshot, version);
            return versionRepository.save(version);
        });
    }

    @Transactional(readOnly = true)
    public Optional<MarketplacePackVersion> findByItemId(UUID itemId) {
        return versionRepository.findByLegacyItemId(itemId);
    }

    @Transactional(readOnly = true)
    public MarketplacePackVersion requireByItemId(UUID itemId) {
        return versionRepository.findByLegacyItemId(itemId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MarketplacePackVersionIdentity identityOf(UUID itemId) {
        return versionRepository.findByLegacyItemId(itemId)
                .map(MarketplacePackVersionIdentity::of)
                .orElse(MarketplacePackVersionIdentity.EMPTY);
    }

    /**
     * Batch variant for list responses, so a catalog page resolves every identity in
     * one query. An item with no version is simply absent from the map; callers fall
     * back to {@link MarketplacePackVersionIdentity#EMPTY}.
     */
    @Transactional(readOnly = true)
    public Map<UUID, MarketplacePackVersionIdentity> identitiesOf(Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return versionRepository.findByLegacyItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(
                        MarketplacePackVersion::getLegacyItemId,
                        MarketplacePackVersionIdentity::of
                ));
    }

    private void copyMetadata(
            MarketplaceItem item,
            MarketplaceQuizPackSnapshot snapshot,
            MarketplacePackVersion version
    ) {
        version.setStatus(versionStatusOf(item.getStatus()));
        version.setTitle(item.getTitle());
        version.setDescription(item.getDescription());
        version.setSubject(item.getSubject());
        version.setPriceCoins(item.getPriceCoins());
        version.setCreatorValidationScore(item.getCreatorValidationScore());
        version.setReviewedBy(item.getReviewedBy());
        version.setReviewNote(item.getReviewNote());
        version.setReviewedAt(item.getReviewedAt());
        if (item.getPublishedAt() != null) {
            version.setPublishedAt(item.getPublishedAt());
        }
        version.setChapterCount(snapshot.getChapterCount());
        version.setQuizCount(snapshot.getQuizCount());
        version.setQuestionCount(snapshot.getQuestionCount());
        version.setContent(snapshot.getContent());
        applySaleable(version, item.getStatus() == MarketplaceItemStatus.PUBLISHED);
    }

    /**
     * Enforces "at most one saleable version per pack" before the write reaches the
     * partial unique index, so the caller gets a typed domain error instead of a
     * constraint violation. The index remains the authority under concurrency.
     */
    private void applySaleable(MarketplacePackVersion version, boolean saleable) {
        if (saleable && !version.isSaleable()) {
            versionRepository.findByPackPackIdAndSaleableTrue(version.getPack().getPackId())
                    .filter(current -> !current.getVersionId().equals(version.getVersionId()))
                    .ifPresent(current -> {
                        throw new AppException(ErrorCode.MARKETPLACE_PACK_SALEABLE_VERSION_CONFLICT);
                    });
        }
        version.setSaleable(saleable);
    }

    /**
     * The legacy item status names are a subset of the version status names, so a
     * migrated version keeps the exact state its item was in.
     */
    static MarketplacePackVersionStatus versionStatusOf(MarketplaceItemStatus status) {
        return MarketplacePackVersionStatus.valueOf(status.name());
    }
}
