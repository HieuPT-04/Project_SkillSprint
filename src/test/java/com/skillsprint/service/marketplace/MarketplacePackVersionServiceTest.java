package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.MarketplaceItem;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceQuizPackSnapshot;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplaceItemStatus;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePackRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplacePackVersionServiceTest {

    @Mock MarketplacePackRepository packRepository;
    @Mock MarketplacePackVersionRepository versionRepository;
    @InjectMocks MarketplacePackVersionService service;

    @Test
    void newDraftItemGetsPackAndVersionOneThatIsNotSaleable() {
        MarketplaceItem item = item(MarketplaceItemStatus.DRAFT);
        when(packRepository.save(any(MarketplacePack.class))).thenAnswer(invocation -> {
            MarketplacePack pack = invocation.getArgument(0);
            pack.setPackId(UUID.randomUUID());
            return pack;
        });
        when(versionRepository.save(any(MarketplacePackVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketplacePackVersion version = service.createInitialVersion(item, snapshot(item));

        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getUpdateType()).isEqualTo(MarketplacePackUpdateType.MAJOR);
        assertThat(version.getStatus()).isEqualTo(MarketplacePackVersionStatus.DRAFT);
        assertThat(version.isSaleable()).isFalse();
        assertThat(version.getLegacyItemId()).isEqualTo(item.getItemId());
        assertThat(version.getPack().getLegacyItemId()).isEqualTo(item.getItemId());
        assertThat(version.getTitle()).isEqualTo(item.getTitle());
        assertThat(version.getQuestionCount()).isEqualTo(20);
    }

    @Test
    void publishingLegacyItemMakesItsVersionSaleable() {
        MarketplaceItem item = item(MarketplaceItemStatus.PUBLISHED);
        Instant publishedAt = Instant.parse("2026-07-16T00:00:00Z");
        item.setPublishedAt(publishedAt);
        MarketplacePackVersion version = existingVersion(item, false);
        when(versionRepository.findByLegacyItemId(item.getItemId())).thenReturn(Optional.of(version));
        when(versionRepository.findByPackPackIdAndSaleableTrue(version.getPack().getPackId()))
                .thenReturn(Optional.empty());
        when(versionRepository.save(any(MarketplacePackVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncFromLegacyItem(item, snapshot(item));

        assertThat(version.getStatus()).isEqualTo(MarketplacePackVersionStatus.PUBLISHED);
        assertThat(version.isSaleable()).isTrue();
        assertThat(version.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void suspendingLegacyItemClearsTheSaleableMarker() {
        MarketplaceItem item = item(MarketplaceItemStatus.SUSPENDED);
        MarketplacePackVersion version = existingVersion(item, true);
        when(versionRepository.findByLegacyItemId(item.getItemId())).thenReturn(Optional.of(version));
        when(versionRepository.save(any(MarketplacePackVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncFromLegacyItem(item, snapshot(item));

        assertThat(version.getStatus()).isEqualTo(MarketplacePackVersionStatus.SUSPENDED);
        assertThat(version.isSaleable()).isFalse();
    }

    @Test
    void suspendingLegacyItemPreservesItsVersionPublicationTimestamp() {
        MarketplaceItem item = item(MarketplaceItemStatus.SUSPENDED);
        MarketplacePackVersion version = existingVersion(item, true);
        Instant publishedAt = Instant.parse("2026-07-15T00:00:00Z");
        version.setPublishedAt(publishedAt);
        when(versionRepository.findByLegacyItemId(item.getItemId())).thenReturn(Optional.of(version));
        when(versionRepository.save(any(MarketplacePackVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncFromLegacyItem(item, snapshot(item));

        assertThat(version.getStatus()).isEqualTo(MarketplacePackVersionStatus.SUSPENDED);
        assertThat(version.isSaleable()).isFalse();
        assertThat(version.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void secondSaleableVersionForSamePackIsRejectedWithTypedError() {
        MarketplaceItem item = item(MarketplaceItemStatus.PUBLISHED);
        MarketplacePackVersion candidate = existingVersion(item, false);
        MarketplacePackVersion alreadySelling = existingVersion(item, true);
        alreadySelling.setVersionId(UUID.randomUUID());
        alreadySelling.setPack(candidate.getPack());

        when(versionRepository.findByLegacyItemId(item.getItemId())).thenReturn(Optional.of(candidate));
        when(versionRepository.findByPackPackIdAndSaleableTrue(candidate.getPack().getPackId()))
                .thenReturn(Optional.of(alreadySelling));

        MarketplaceQuizPackSnapshot snapshot = snapshot(item);
        assertThatThrownBy(() -> service.syncFromLegacyItem(item, snapshot))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_PACK_SALEABLE_VERSION_CONFLICT);
        assertThat(candidate.isSaleable()).isFalse();
    }

    @Test
    void alreadySaleableVersionStaysSaleableWithoutConflict() {
        MarketplaceItem item = item(MarketplaceItemStatus.PUBLISHED);
        MarketplacePackVersion version = existingVersion(item, true);
        when(versionRepository.findByLegacyItemId(item.getItemId())).thenReturn(Optional.of(version));
        when(versionRepository.save(any(MarketplacePackVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.syncFromLegacyItem(item, snapshot(item));

        assertThat(version.isSaleable()).isTrue();
    }

    @Test
    void itemWithoutVersionResolvesToEmptyIdentityInsteadOfFailing() {
        UUID itemId = UUID.randomUUID();
        when(versionRepository.findByLegacyItemId(itemId)).thenReturn(Optional.empty());

        assertThat(service.identityOf(itemId)).isEqualTo(MarketplacePackVersionIdentity.EMPTY);
    }

    @Test
    void requireByItemIdThrowsTypedErrorWhenVersionIsMissing() {
        UUID itemId = UUID.randomUUID();
        when(versionRepository.findByLegacyItemId(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireByItemId(itemId))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND);
    }

    @Test
    void everyLegacyItemStatusMapsToAVersionStatus() {
        for (MarketplaceItemStatus status : MarketplaceItemStatus.values()) {
            assertThat(MarketplacePackVersionService.versionStatusOf(status).name()).isEqualTo(status.name());
        }
    }

    private MarketplacePackVersion existingVersion(MarketplaceItem item, boolean saleable) {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        pack.setLegacyItemId(item.getItemId());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setPack(pack);
        version.setVersionNo(1);
        version.setLegacyItemId(item.getItemId());
        version.setSaleable(saleable);
        return version;
    }

    private MarketplaceItem item(MarketplaceItemStatus status) {
        User creator = new User();
        creator.setUserId("creator");
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setWorkspaceId(UUID.randomUUID());
        MarketplaceItem item = new MarketplaceItem();
        item.setItemId(UUID.randomUUID());
        item.setCreator(creator);
        item.setSourceWorkspace(workspace);
        item.setTitle("Pack");
        item.setSubject("Toan");
        item.setPriceCoins(100);
        item.setStatus(status);
        return item;
    }

    private MarketplaceQuizPackSnapshot snapshot(MarketplaceItem item) {
        MarketplaceQuizPackSnapshot snapshot = new MarketplaceQuizPackSnapshot();
        snapshot.setItem(item);
        snapshot.setChapterCount(4);
        snapshot.setQuizCount(4);
        snapshot.setQuestionCount(20);
        snapshot.setContent(new ObjectMapper().createObjectNode());
        return snapshot;
    }
}
