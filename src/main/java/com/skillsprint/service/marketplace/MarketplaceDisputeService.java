package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.CreateMarketplaceDisputeRequest;
import com.skillsprint.dto.request.marketplace.DecideMarketplaceDisputeRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceDisputeEligibilityResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceDisputeResponse;
import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceRefundDispute;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserWallet;
import com.skillsprint.entity.WalletTransaction;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import com.skillsprint.enums.marketplace.MarketplaceDisputeStatus;
import com.skillsprint.enums.marketplace.MarketplaceEntitlementStatus;
import com.skillsprint.enums.marketplace.MarketplaceSaleStatus;
import com.skillsprint.enums.marketplace.MarketplaceSettlementStatus;
import com.skillsprint.enums.marketplace.WalletTransactionDirection;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CreatorEarningEntryRepository;
import com.skillsprint.repository.MarketplaceEntitlementRepository;
import com.skillsprint.repository.MarketplaceRefundDisputeRepository;
import com.skillsprint.repository.MarketplaceSaleRepository;
import com.skillsprint.repository.MarketplaceSaleSettlementRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.repository.UserWalletRepository;
import com.skillsprint.repository.WalletTransactionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buyer refund-dispute lifecycle and the explicit, idempotent refund-completion step.
 *
 * <p>Refund completion moves money only with compensating records — a MARKETPLACE_REFUND wallet
 * credit, the sale marked REFUNDED, the settlement REVERSED, the creator earning REVERSED, and the
 * entitlement REVOKED. It refuses to run if the creator earning has already been paid or reserved
 * into a payout, so the 80/20 model is never mutated destructively.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceDisputeService {

    static final int MAX_ADMIN_PAGE_SIZE = 50;

    MarketplaceRefundDisputeRepository disputeRepository;
    MarketplaceSaleRepository saleRepository;
    MarketplaceEntitlementRepository entitlementRepository;
    MarketplaceSaleSettlementRepository settlementRepository;
    CreatorEarningEntryRepository earningRepository;
    UserWalletRepository walletRepository;
    WalletTransactionRepository walletTransactionRepository;
    UserRepository userRepository;

    // ----- Buyer -----

    @Transactional(readOnly = true)
    public MarketplaceDisputeEligibilityResponse getEligibility(String buyerId, UUID saleId) {
        MarketplaceSale sale = saleRepository.findById(saleId).orElse(null);
        if (sale == null || !sale.getBuyer().getUserId().equals(buyerId)) {
            return eligibility(saleId, false, "NOT_OWNER", null);
        }
        MarketplaceRefundDispute active = disputeRepository.findByBuyerUserIdOrderByCreatedAtDesc(buyerId).stream()
                .filter(dispute -> dispute.getSale().getSaleId().equals(saleId))
                .findFirst()
                .orElse(null);
        if (sale.getStatus() == MarketplaceSaleStatus.REFUNDED) {
            return eligibility(saleId, false, "ALREADY_REFUNDED", active);
        }
        if (sale.getStatus() != MarketplaceSaleStatus.COMPLETED) {
            return eligibility(saleId, false, "SALE_NOT_COMPLETED", active);
        }
        if (active != null && active.getStatus().isActive()) {
            return eligibility(saleId, false, "DISPUTE_ACTIVE", active);
        }
        return eligibility(saleId, true, null, active);
    }

    /**
     * Resolves the caller's own sale for a version and returns its dispute eligibility. Lets the
     * owned-pack UI open a dispute from a versionId without ever handling another buyer's sale id.
     */
    @Transactional(readOnly = true)
    public MarketplaceDisputeEligibilityResponse getEligibilityByVersion(String buyerId, UUID versionId) {
        MarketplaceEntitlement entitlement = entitlementRepository
                .findFirstByBuyerUserIdAndPackVersionVersionIdOrderByGrantedAtDesc(buyerId, versionId)
                .orElse(null);
        if (entitlement == null) {
            return eligibility(null, false, "NOT_OWNER", null);
        }
        return getEligibility(buyerId, entitlement.getSourceSale().getSaleId());
    }

    @Transactional
    public MarketplaceDisputeResponse createDispute(String buyerId, CreateMarketplaceDisputeRequest request) {
        MarketplaceSale sale = saleRepository.findByIdForUpdate(request.getSaleId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_DISPUTE_SALE_NOT_FOUND));
        if (!sale.getBuyer().getUserId().equals(buyerId)) {
            throw new AppException(ErrorCode.MARKETPLACE_DISPUTE_NOT_ELIGIBLE);
        }
        if (sale.getStatus() != MarketplaceSaleStatus.COMPLETED) {
            throw new AppException(ErrorCode.MARKETPLACE_DISPUTE_NOT_ELIGIBLE);
        }
        if (disputeRepository.existsActiveForSale(sale.getSaleId())) {
            throw new AppException(ErrorCode.MARKETPLACE_DISPUTE_ALREADY_ACTIVE);
        }

        MarketplaceRefundDispute dispute = new MarketplaceRefundDispute();
        dispute.setSale(sale);
        dispute.setBuyer(sale.getBuyer());
        dispute.setPackVersion(sale.getPackVersion());
        dispute.setReason(request.getReason());
        dispute.setDescription(normalize(request.getDescription()));
        dispute.setStatus(MarketplaceDisputeStatus.OPEN);
        try {
            MarketplaceRefundDispute saved = disputeRepository.saveAndFlush(dispute);
            return toResponse(saved, false);
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index closed a race with a concurrent create/retry.
            throw new AppException(ErrorCode.MARKETPLACE_DISPUTE_ALREADY_ACTIVE);
        }
    }

    @Transactional(readOnly = true)
    public List<MarketplaceDisputeResponse> getMyDisputes(String buyerId) {
        return disputeRepository.findByBuyerUserIdOrderByCreatedAtDesc(buyerId).stream()
                .map(dispute -> toResponse(dispute, false))
                .toList();
    }

    // ----- Admin review -----

    @Transactional(readOnly = true)
    public PageResponse<MarketplaceDisputeResponse> getAdminDisputes(
            MarketplaceDisputeStatus status,
            int page,
            int size
    ) {
        Pageable pageable = adminPageable(page, size);
        Page<MarketplaceDisputeResponse> disputes = disputeRepository.searchAdmin(status, pageable)
                .map(dispute -> toResponse(dispute, true));
        return PageResponse.from(disputes);
    }

    @Transactional(readOnly = true)
    public MarketplaceDisputeResponse getAdminDispute(UUID disputeId) {
        return toResponse(requireDispute(disputeId), true);
    }

    @Transactional
    public MarketplaceDisputeResponse decide(
            String adminUserId,
            UUID disputeId,
            DecideMarketplaceDisputeRequest request
    ) {
        MarketplaceRefundDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_DISPUTE_NOT_FOUND));
        MarketplaceDisputeStatus next = request.getStatus();
        if (!dispute.getStatus().canDecideTo(next)) {
            throw new AppException(ErrorCode.MARKETPLACE_DISPUTE_STATE_INVALID);
        }

        if (next == MarketplaceDisputeStatus.UNDER_REVIEW) {
            dispute.setStatus(next);
        } else {
            String note = normalize(request.getDecisionNote());
            if (note == null) {
                throw new AppException(ErrorCode.MARKETPLACE_DISPUTE_DECISION_NOTE_REQUIRED);
            }
            dispute.setStatus(next);
            dispute.setAdminActor(requireUser(adminUserId));
            dispute.setDecisionNote(note);
            dispute.setDecidedAt(Instant.now());
        }

        return toResponse(disputeRepository.saveAndFlush(dispute), true);
    }

    /**
     * Executes the approved refund exactly once. Idempotent: a dispute already REFUNDED returns its
     * current state without moving money again.
     */
    @Transactional
    public MarketplaceDisputeResponse completeRefund(String adminUserId, UUID disputeId) {
        MarketplaceRefundDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_DISPUTE_NOT_FOUND));
        if (dispute.getStatus() == MarketplaceDisputeStatus.REFUNDED) {
            return toResponse(dispute, true);
        }
        if (dispute.getStatus() != MarketplaceDisputeStatus.APPROVED) {
            throw new AppException(ErrorCode.MARKETPLACE_REFUND_NOT_APPROVED);
        }

        MarketplaceSale sale = saleRepository.findByIdForUpdate(dispute.getSale().getSaleId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_REFUND_STATE_INCONSISTENT));
        if (sale.getStatus() != MarketplaceSaleStatus.COMPLETED) {
            throw new AppException(ErrorCode.MARKETPLACE_REFUND_STATE_INCONSISTENT);
        }

        MarketplaceSaleSettlement settlement = settlementRepository.findBySaleSaleId(sale.getSaleId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_REFUND_STATE_INCONSISTENT));
        CreatorEarningEntry earning = earningRepository.findBySettlementIdForUpdate(settlement.getSettlementId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_REFUND_STATE_INCONSISTENT));

        // Safety boundary: the 80/20 model can only be reversed while the creator's share is still
        // pending. Once reserved/paid into a payout, an automatic clawback would corrupt the ledger.
        if (earning.getState() != CreatorEarningState.PENDING || earning.getPayout() != null) {
            throw new AppException(ErrorCode.MARKETPLACE_REFUND_EARNING_LOCKED);
        }

        int refundAmount = sale.getGrossCoinAmount();
        UUID walletTransactionId = null;
        if (refundAmount > 0) {
            User buyer = sale.getBuyer();
            UserWallet wallet = walletRepository.findByUserIdForUpdate(buyer.getUserId())
                    .orElseGet(() -> {
                        UserWallet created = new UserWallet();
                        created.setUser(buyer);
                        return walletRepository.saveAndFlush(created);
                    });
            int before = wallet.getBalance();
            int after = before + refundAmount;
            wallet.setBalance(after);
            walletRepository.saveAndFlush(wallet);

            WalletTransaction transaction = new WalletTransaction();
            transaction.setWallet(wallet);
            transaction.setDirection(WalletTransactionDirection.CREDIT);
            transaction.setAmount(refundAmount);
            transaction.setBalanceBefore(before);
            transaction.setBalanceAfter(after);
            transaction.setReferenceType(WalletTransactionReferenceType.MARKETPLACE_REFUND);
            transaction.setReferenceId(sale.getSaleId());
            transaction.setAdjustedBy(requireUser(adminUserId));
            transaction.setAdjustmentReason("Hoàn tiền tranh chấp Marketplace");
            walletTransactionId = walletTransactionRepository.saveAndFlush(transaction).getTransactionId();
        }

        // Compensating reversals — never destructive edits to prior financial records.
        settlement.setStatus(MarketplaceSettlementStatus.REVERSED);
        settlementRepository.saveAndFlush(settlement);
        earning.setState(CreatorEarningState.REVERSED);
        earningRepository.saveAndFlush(earning);

        entitlementRepository.findBySourceSaleSaleId(sale.getSaleId()).ifPresent(entitlement -> {
            if (entitlement.getStatus() == MarketplaceEntitlementStatus.ACTIVE) {
                entitlement.setStatus(MarketplaceEntitlementStatus.REVOKED);
                entitlement.setRevokedAt(Instant.now());
                entitlementRepository.saveAndFlush(entitlement);
            }
        });

        sale.setStatus(MarketplaceSaleStatus.REFUNDED);
        saleRepository.saveAndFlush(sale);

        dispute.setStatus(MarketplaceDisputeStatus.REFUNDED);
        dispute.setRefundedAt(Instant.now());
        dispute.setRefundCoinAmount(refundAmount);
        dispute.setRefundWalletTransactionId(walletTransactionId);
        return toResponse(disputeRepository.saveAndFlush(dispute), true);
    }

    // ----- helpers -----

    private MarketplaceDisputeEligibilityResponse eligibility(
            UUID saleId,
            boolean eligible,
            String reason,
            MarketplaceRefundDispute existing
    ) {
        return MarketplaceDisputeEligibilityResponse.builder()
                .saleId(saleId)
                .eligible(eligible)
                .ineligibilityReason(reason)
                .existingDispute(existing == null ? null : toResponse(existing, false))
                .build();
    }

    private List<String> allowedActions(MarketplaceDisputeStatus status) {
        return switch (status) {
            case OPEN -> List.of("REVIEW", "APPROVE", "REJECT");
            case UNDER_REVIEW -> List.of("APPROVE", "REJECT");
            case APPROVED -> List.of("COMPLETE_REFUND");
            case REJECTED, REFUNDED -> List.of();
        };
    }

    private MarketplaceDisputeResponse toResponse(MarketplaceRefundDispute dispute, boolean forAdmin) {
        MarketplacePackVersion version = dispute.getPackVersion();
        MarketplaceDisputeResponse.MarketplaceDisputeResponseBuilder builder = MarketplaceDisputeResponse.builder()
                .disputeId(dispute.getDisputeId())
                .saleId(dispute.getSale().getSaleId())
                .packVersionId(version.getVersionId())
                .packId(version.getPack().getPackId())
                .versionNo(version.getVersionNo())
                .versionTitle(version.getTitle())
                .saleCoinAmount(dispute.getSale().getGrossCoinAmount())
                .reason(dispute.getReason())
                .description(dispute.getDescription())
                .status(dispute.getStatus())
                .decisionNote(dispute.getDecisionNote())
                .decidedAt(dispute.getDecidedAt())
                .refundedAt(dispute.getRefundedAt())
                .refundCoinAmount(dispute.getRefundCoinAmount())
                .refundWalletTransactionId(dispute.getRefundWalletTransactionId())
                .allowedActions(allowedActions(dispute.getStatus()))
                .createdAt(dispute.getCreatedAt())
                .updatedAt(dispute.getUpdatedAt());
        if (forAdmin) {
            User buyer = dispute.getBuyer();
            builder.buyerId(buyer == null ? null : buyer.getUserId())
                    .buyerName(buyer == null ? null : buyer.getFullName())
                    .adminActorName(dispute.getAdminActor() == null ? null : dispute.getAdminActor().getFullName());
        }
        return builder.build();
    }

    private MarketplaceRefundDispute requireDispute(UUID disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_DISPUTE_NOT_FOUND));
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private Pageable adminPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_ADMIN_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
