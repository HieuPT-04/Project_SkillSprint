package com.skillsprint.dto.response.marketplace;

import com.skillsprint.enums.marketplace.MarketplaceDisputeReason;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * Dispute view for the buyer (own disputes) and admins. {@code allowedActions} is server-driven so
 * the client never infers policy from status/date. Reporter/buyer identity is populated for admins.
 */
@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MarketplaceDisputeResponse {

    UUID disputeId;
    UUID saleId;
    UUID packVersionId;
    UUID packId;
    Integer versionNo;
    String versionTitle;
    Integer saleCoinAmount;
    MarketplaceDisputeReason reason;
    String description;
    MarketplaceDisputeStatus status;
    String decisionNote;
    Instant decidedAt;
    Instant refundedAt;
    Integer refundCoinAmount;
    UUID refundWalletTransactionId;
    Instant createdAt;
    Instant updatedAt;

    /** Server-authoritative next actions. Values: REVIEW, APPROVE, REJECT, COMPLETE_REFUND. */
    List<String> allowedActions;

    // Admin-only identity.
    String buyerId;
    String buyerName;
    String adminActorName;
}
