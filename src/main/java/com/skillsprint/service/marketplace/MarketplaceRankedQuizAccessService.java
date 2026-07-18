package com.skillsprint.service.marketplace;

import com.skillsprint.entity.MarketplacePackVersion;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranked-specific facade kept for compatibility. The ownership policy lives in
 * {@link MarketplaceVersionAccessService} and is shared with Practice Quiz.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceRankedQuizAccessService {

    MarketplaceVersionAccessService versionAccessService;

    @Transactional(readOnly = true)
    public MarketplacePackVersion requireRankedAccess(String buyerId, UUID versionId) {
        return versionAccessService.requireAccess(buyerId, versionId);
    }

    @Transactional
    public MarketplacePackVersion requireAndLockRankedAccess(String buyerId, UUID versionId) {
        return versionAccessService.requireAndLockAccess(buyerId, versionId);
    }
}
