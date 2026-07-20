package com.skillsprint.enums.marketplace;

/** Lifecycle of a marketplace content report. Terminal states are RESOLVED and DISMISSED. */
public enum MarketplaceReportStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    DISMISSED;

    public boolean isTerminal() {
        return this == RESOLVED || this == DISMISSED;
    }

    /** Admin lifecycle: an open/in-review report can move to any non-open state; terminal is final. */
    public boolean canTransitionTo(MarketplaceReportStatus next) {
        if (this == next) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        return next == IN_REVIEW || next == RESOLVED || next == DISMISSED;
    }
}
