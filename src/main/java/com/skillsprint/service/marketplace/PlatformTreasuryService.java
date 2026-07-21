package com.skillsprint.service.marketplace;

import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.marketplace.PlatformTreasuryEntryResponse;
import com.skillsprint.dto.response.marketplace.PlatformTreasurySummaryResponse;
import com.skillsprint.entity.PlatformTreasuryEntry;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import com.skillsprint.enums.marketplace.PlatformTreasuryReferenceType;
import com.skillsprint.repository.PlatformTreasuryEntryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlatformTreasuryService {

    PlatformTreasuryEntryRepository treasuryEntryRepository;

    @Transactional(readOnly = true)
    public PlatformTreasurySummaryResponse getSummary() {
        BigDecimal vndInflow = sum(PlatformTreasuryAsset.VND, PlatformTreasuryDirection.CREDIT);
        BigDecimal vndOutflow = sum(PlatformTreasuryAsset.VND, PlatformTreasuryDirection.DEBIT);
        BigDecimal commissionEarned = sum(PlatformTreasuryAsset.COIN, PlatformTreasuryDirection.CREDIT);
        BigDecimal commissionReversed = sum(PlatformTreasuryAsset.COIN, PlatformTreasuryDirection.DEBIT);
        return PlatformTreasurySummaryResponse.builder()
                .vndInflow(vndInflow).vndOutflow(vndOutflow).vndNetPosition(vndInflow.subtract(vndOutflow))
                .commissionCoinEarned(commissionEarned).commissionCoinReversed(commissionReversed)
                .commissionCoinNetPosition(commissionEarned.subtract(commissionReversed)).build();
    }

    @Transactional(readOnly = true)
    public PageResponse<PlatformTreasuryEntryResponse> getEntries(
            PlatformTreasuryAsset asset, PlatformTreasuryEntryType entryType, Instant from, Instant to, int page, int size) {
        return PageResponse.from(treasuryEntryRepository.search(asset, entryType, from, to,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(this::toResponse));
    }

    public void record(
            PlatformTreasuryAsset asset,
            PlatformTreasuryDirection direction,
            PlatformTreasuryEntryType entryType,
            PlatformTreasuryReferenceType referenceType,
            UUID referenceId,
            BigDecimal amount,
            User actor,
            User counterparty,
            String externalReference,
            String note,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        String idempotencyKey = entryType + ":" + referenceId;
        if (treasuryEntryRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        PlatformTreasuryEntry entry = new PlatformTreasuryEntry();
        entry.setAsset(asset);
        entry.setDirection(direction);
        entry.setEntryType(entryType);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setAmount(amount);
        entry.setActorUserId(actor == null ? null : actor.getUserId());
        entry.setActorNameSnapshot(actor == null ? "SYSTEM" : actor.getFullName());
        entry.setCounterpartyUserId(counterparty == null ? null : counterparty.getUserId());
        entry.setCounterpartyNameSnapshot(counterparty == null ? null : counterparty.getFullName());
        entry.setExternalReference(externalReference);
        entry.setNote(note);
        entry.setMetadata(metadata);
        entry.setOccurredAt(occurredAt);
        entry.setIdempotencyKey(idempotencyKey);
        try {
            treasuryEntryRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            // The unique idempotency key is the concurrency boundary for retried financial events.
            // Do not hide an unrelated constraint failure.
            if (treasuryEntryRepository.findByIdempotencyKey(idempotencyKey).isEmpty()) {
                throw ex;
            }
        }
    }

    private BigDecimal sum(PlatformTreasuryAsset asset, PlatformTreasuryDirection direction) {
        return treasuryEntryRepository.sumAmountByAssetAndDirection(asset, direction);
    }

    private PlatformTreasuryEntryResponse toResponse(PlatformTreasuryEntry entry) {
        return PlatformTreasuryEntryResponse.builder()
                .entryId(entry.getTreasuryEntryId()).asset(entry.getAsset()).direction(entry.getDirection())
                .entryType(entry.getEntryType()).referenceType(entry.getReferenceType()).referenceId(entry.getReferenceId())
                .amount(entry.getAmount()).actorUserId(entry.getActorUserId()).actorName(entry.getActorNameSnapshot())
                .counterpartyUserId(entry.getCounterpartyUserId()).counterpartyName(entry.getCounterpartyNameSnapshot())
                .externalReference(entry.getExternalReference()).note(entry.getNote()).metadata(entry.getMetadata())
                .occurredAt(entry.getOccurredAt()).build();
    }
}
