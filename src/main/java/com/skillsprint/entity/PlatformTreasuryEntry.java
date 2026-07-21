package com.skillsprint.entity;

import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import com.skillsprint.enums.marketplace.PlatformTreasuryReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "platform_treasury_entries")
public class PlatformTreasuryEntry extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "treasury_entry_id")
    private UUID treasuryEntryId;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private PlatformTreasuryAsset asset;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private PlatformTreasuryDirection direction;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, length = 60)
    private PlatformTreasuryEntryType entryType;
    @Enumerated(EnumType.STRING) @Column(name = "reference_type", nullable = false, length = 40)
    private PlatformTreasuryReferenceType referenceType;
    @Column(name = "reference_id", nullable = false) private UUID referenceId;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(name = "actor_user_id", length = 255) private String actorUserId;
    @Column(name = "actor_name_snapshot", length = 255) private String actorNameSnapshot;
    @Column(name = "counterparty_user_id", length = 255) private String counterpartyUserId;
    @Column(name = "counterparty_name_snapshot", length = 255) private String counterpartyNameSnapshot;
    @Column(name = "external_reference", length = 200) private String externalReference;
    @Column(columnDefinition = "TEXT") private String note;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private Map<String, Object> metadata;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200) private String idempotencyKey;
}
