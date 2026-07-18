package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.MarketplacePackVersion;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceRankedQuizAccessServiceTest {

    @Mock MarketplaceVersionAccessService versionAccessService;
    @InjectMocks MarketplaceRankedQuizAccessService service;

    @Test
    void delegatesReadAccessToSharedVersionPolicy() {
        UUID versionId = UUID.randomUUID();
        MarketplacePackVersion version = new MarketplacePackVersion();
        when(versionAccessService.requireAccess("buyer", versionId)).thenReturn(version);

        assertThat(service.requireRankedAccess("buyer", versionId)).isSameAs(version);
        verify(versionAccessService).requireAccess("buyer", versionId);
    }

    @Test
    void delegatesLockedAccessToSharedVersionPolicy() {
        UUID versionId = UUID.randomUUID();
        MarketplacePackVersion version = new MarketplacePackVersion();
        when(versionAccessService.requireAndLockAccess("buyer", versionId)).thenReturn(version);

        assertThat(service.requireAndLockRankedAccess("buyer", versionId)).isSameAs(version);
        verify(versionAccessService).requireAndLockAccess("buyer", versionId);
    }
}
