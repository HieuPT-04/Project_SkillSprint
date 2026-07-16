package com.skillsprint.service.marketplace;

import com.skillsprint.entity.MarketplacePackVersion;
import java.util.UUID;

/**
 * The additive version identity every Marketplace response exposes alongside the
 * legacy {@code itemId} compatibility identifier.
 *
 * <p>{@link #EMPTY} keeps the existing endpoints working for an item that has no
 * version yet: the additive fields serialize as null rather than failing a response
 * that used to succeed.
 */
public record MarketplacePackVersionIdentity(UUID packId, UUID versionId, Integer versionNo) {

    public static final MarketplacePackVersionIdentity EMPTY =
            new MarketplacePackVersionIdentity(null, null, null);

    public static MarketplacePackVersionIdentity of(MarketplacePackVersion version) {
        return new MarketplacePackVersionIdentity(
                version.getPack().getPackId(),
                version.getVersionId(),
                version.getVersionNo()
        );
    }

    public static MarketplacePackVersionIdentity ofNullable(MarketplacePackVersion version) {
        return version == null ? EMPTY : of(version);
    }
}
