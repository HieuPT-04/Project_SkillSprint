package com.skillsprint.enums.marketplace;

/**
 * Lifecycle of a marketplace refund dispute.
 *
 * <p>{@link #APPROVED} records the admin decision only; money movement happens in a separate,
 * explicit refund-completion step that transitions {@link #APPROVED} to {@link #REFUNDED}.
 */
public enum MarketplaceDisputeStatus {
    OPEN,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    REFUNDED;

    public boolean isActive() {
        return this == OPEN || this == UNDER_REVIEW || this == APPROVED;
    }

    public boolean isDecided() {
        return this == APPROVED || this == REJECTED || this == REFUNDED;
    }

    /** Admin review transitions before money moves: an open/under-review dispute is decided here. */
    public boolean canDecideTo(MarketplaceDisputeStatus next) {
        if (this != OPEN && this != UNDER_REVIEW) {
            return false;
        }
        return next == UNDER_REVIEW || next == APPROVED || next == REJECTED;
    }
}
