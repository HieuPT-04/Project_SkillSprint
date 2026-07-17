package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.CreatorPayoutAllocationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "creator_payout_allocations",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_creator_payout_allocation_entry",
                columnNames = {"payout_id", "earning_entry_id"}
        )
)
public class CreatorPayoutAllocation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "allocation_id")
    private UUID allocationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payout_id", nullable = false)
    private CreatorPayout payout;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "earning_entry_id", nullable = false)
    private CreatorEarningEntry earningEntry;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreatorPayoutAllocationState state = CreatorPayoutAllocationState.RESERVED;
}
