package com.skillsprint.service.marketplace;

import com.skillsprint.dto.request.marketplace.CompleteCreatorPayoutRequest;
import com.skillsprint.dto.request.marketplace.CreateCreatorPayoutQrUploadUrlRequest;
import com.skillsprint.dto.request.marketplace.CreateCreatorPayoutRequest;
import com.skillsprint.dto.request.marketplace.RejectCreatorPayoutRequest;
import com.skillsprint.dto.request.marketplace.UpsertCreatorPayoutDestinationRequest;
import com.skillsprint.dto.response.marketplace.CreatorEarningResponse;
import com.skillsprint.dto.response.marketplace.CreatorEarningsResponse;
import com.skillsprint.dto.response.marketplace.CreatorPayoutDestinationResponse;
import com.skillsprint.dto.response.marketplace.CreatorPayoutQrUploadUrlResponse;
import com.skillsprint.dto.response.marketplace.CreatorPayoutResponse;
import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.entity.CreatorPayout;
import com.skillsprint.entity.CreatorPayoutAllocation;
import com.skillsprint.entity.CreatorPayoutDestination;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import com.skillsprint.enums.marketplace.CreatorPayoutAllocationState;
import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
import com.skillsprint.enums.marketplace.PlatformTreasuryAsset;
import com.skillsprint.enums.marketplace.PlatformTreasuryDirection;
import com.skillsprint.enums.marketplace.PlatformTreasuryEntryType;
import com.skillsprint.enums.marketplace.PlatformTreasuryReferenceType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CreatorEarningEntryRepository;
import com.skillsprint.repository.CreatorPayoutAllocationRepository;
import com.skillsprint.repository.CreatorPayoutDestinationRepository;
import com.skillsprint.repository.CreatorPayoutRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreatorPayoutService {

    UserRepository userRepository;
    CreatorEarningEntryRepository earningEntryRepository;
    CreatorPayoutAllocationRepository allocationRepository;
    CreatorPayoutDestinationRepository destinationRepository;
    CreatorPayoutRepository payoutRepository;
    S3PresignedUrlService s3PresignedUrlService;
    MarketplacePayoutAuditService payoutAuditService;
    PlatformTreasuryService platformTreasuryService;

    @Transactional(readOnly = true)
    public CreatorEarningsResponse getEarnings(String creatorId) {
        List<CreatorEarningEntry> earnings = earningEntryRepository.findByCreatorUserIdOrderByCreatedAtDesc(creatorId);
        List<CreatorPayoutAllocation> allocations = allocationRepository.findByPayoutCreatorUserIdOrderByCreatedAtDesc(creatorId);
        Map<UUID, AllocationAmounts> amounts = allocationAmounts(allocations);

        int available = 0;
        int reserved = 0;
        int paid = 0;
        List<CreatorEarningResponse> responses = new ArrayList<>();
        for (CreatorEarningEntry earning : earnings) {
            AllocationAmounts allocation = amounts.getOrDefault(earning.getEarningEntryId(), AllocationAmounts.EMPTY);
            int earningAvailable = availableAmount(earning, allocation);
            available += earningAvailable;
            reserved += allocation.reserved();
            paid += allocation.paid();
            responses.add(CreatorEarningResponse.builder()
                    .earningEntryId(earning.getEarningEntryId())
                    .settlementId(earning.getSettlement().getSettlementId())
                    .saleId(earning.getSettlement().getSale().getSaleId())
                    .amount(earning.getAmount())
                    .availableAmount(earningAvailable)
                    .reservedAmount(allocation.reserved())
                    .paidAmount(allocation.paid())
                    .state(earning.getState())
                    .createdAt(earning.getCreatedAt())
                    .build());
        }

        return CreatorEarningsResponse.builder()
                .pendingAmount(available)
                .reservedAmount(reserved)
                .paidAmount(paid)
                .availableAmount(available)
                .earnings(responses)
                .build();
    }

    public CreatorPayoutQrUploadUrlResponse createQrUploadUrl(
            String creatorId,
            CreateCreatorPayoutQrUploadUrlRequest request
    ) {
        return s3PresignedUrlService.createCreatorPayoutQrUploadUrl(creatorId, request);
    }

    @Transactional(readOnly = true)
    public CreatorPayoutDestinationResponse getDestination(String creatorId) {
        CreatorPayoutDestination destination = destinationRepository.findByCreatorUserIdAndActiveTrue(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PAYOUT_DESTINATION_NOT_FOUND));
        return toDestinationResponse(destination);
    }

    @Transactional
    public CreatorPayoutDestinationResponse upsertDestination(
            String creatorId,
            UpsertCreatorPayoutDestinationRequest request
    ) {
        User creator = findUser(creatorId);
        String qrObjectKey = s3PresignedUrlService.confirmCreatorPayoutQrUpload(creatorId, request.getQrObjectKey());
        CreatorPayoutDestination destination = destinationRepository.findByCreatorUserIdAndActiveTrue(creatorId)
                .orElseGet(() -> {
                    CreatorPayoutDestination value = new CreatorPayoutDestination();
                    value.setCreator(creator);
                    return value;
                });
        destination.setBankName(request.getBankName().trim());
        destination.setBankCode(normalizeBlank(request.getBankCode()));
        destination.setAccountHolder(request.getAccountHolder().trim());
        // Account numbers are intentionally not accepted until an approved encryption mechanism exists.
        destination.setAccountNumberEncrypted(null);
        destination.setQrObjectKey(qrObjectKey);
        destination.setActive(true);
        return toDestinationResponse(destinationRepository.save(destination));
    }

    @Transactional
    public CreatorPayoutResponse requestPayout(String creatorId, CreateCreatorPayoutRequest request) {
        User creator = findUser(creatorId);
        CreatorPayoutDestination destination = destinationRepository.findByCreatorUserIdAndActiveTrue(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PAYOUT_DESTINATION_NOT_FOUND));
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new AppException(ErrorCode.MARKETPLACE_PAYOUT_AMOUNT_INVALID);
        }
        if (availableEarnings(creatorId) < request.getAmount()) {
            throw new AppException(ErrorCode.MARKETPLACE_CREATOR_EARNINGS_INSUFFICIENT);
        }

        CreatorPayout payout = new CreatorPayout();
        payout.setCreator(creator);
        payout.setDestination(destination);
        payout.setRequestedAmount(request.getAmount());
        payout.setStatus(CreatorPayoutStatus.REQUESTED);
        copyDestinationSnapshot(destination, payout);
        payout = payoutRepository.save(payout);
        payoutAuditService.record(creator, payout, BusinessActionType.MARKETPLACE_PAYOUT_REQUESTED,
                "Creator tạo yêu cầu rút tiền");
        return toPayoutResponse(payout, true);
    }

    @Transactional(readOnly = true)
    public List<CreatorPayoutResponse> getPayouts(String creatorId) {
        return payoutRepository.findByCreatorUserIdOrderByCreatedAtDesc(creatorId)
                .stream()
                .map(payout -> toPayoutResponse(payout, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CreatorPayoutResponse> getAdminPayouts(CreatorPayoutStatus status) {
        List<CreatorPayout> payouts = status == null
                ? payoutRepository.findAllByOrderByCreatedAtAsc()
                : payoutRepository.findByStatusOrderByCreatedAtAsc(status);
        return payouts.stream().map(payout -> toPayoutResponse(payout, true)).toList();
    }

    @Transactional
    public CreatorPayoutResponse approve(String adminId, UUID payoutId) {
        User admin = findUser(adminId);
        CreatorPayout payout = findPayoutForUpdate(payoutId);
        requireStatus(payout, CreatorPayoutStatus.REQUESTED);
        reserveEarnings(payout);
        payout.setStatus(CreatorPayoutStatus.APPROVED);
        payout.setAdminActor(admin);
        payout = payoutRepository.save(payout);
        payoutAuditService.record(admin, payout, BusinessActionType.MARKETPLACE_PAYOUT_APPROVED,
                "Admin duyệt yêu cầu rút tiền");
        return toPayoutResponse(payout, true);
    }

    @Transactional
    public CreatorPayoutResponse startProcessing(String adminId, UUID payoutId) {
        User admin = findUser(adminId);
        CreatorPayout payout = findPayoutForUpdate(payoutId);
        requireStatus(payout, CreatorPayoutStatus.APPROVED);
        payout.setStatus(CreatorPayoutStatus.PROCESSING);
        payout.setAdminActor(admin);
        payout = payoutRepository.save(payout);
        payoutAuditService.record(admin, payout, BusinessActionType.MARKETPLACE_PAYOUT_PROCESSING,
                "Admin bắt đầu chuyển khoản thủ công");
        return toPayoutResponse(payout, true);
    }

    @Transactional
    public CreatorPayoutResponse complete(String adminId, UUID payoutId, CompleteCreatorPayoutRequest request) {
        User admin = findUser(adminId);
        CreatorPayout payout = findPayoutForUpdate(payoutId);
        requireStatus(payout, CreatorPayoutStatus.PROCESSING);
        List<CreatorPayoutAllocation> allocations = allocationRepository.findByPayoutPayoutIdOrderByCreatedAtAsc(payoutId);
        if (allocations.isEmpty() || allocations.stream().anyMatch(allocation -> allocation.getState() != CreatorPayoutAllocationState.RESERVED)
                || allocations.stream().mapToInt(CreatorPayoutAllocation::getAmount).sum() != payout.getRequestedAmount()) {
            throw new AppException(ErrorCode.MARKETPLACE_PAYOUT_STATE_INVALID);
        }
        allocations.forEach(allocation -> allocation.setState(CreatorPayoutAllocationState.PAID));
        allocationRepository.saveAll(allocations);
        refreshEarningStates(allocations);
        payout.setStatus(CreatorPayoutStatus.COMPLETED);
        payout.setAdminActor(admin);
        payout.setExternalTransferReference(request.getExternalTransferReference().trim());
        payout.setPaidVndAmount(request.getPaidVndAmount());
        payout.setNotes(normalizeBlank(request.getNotes()));
        payout = payoutRepository.save(payout);
        payoutAuditService.record(admin, payout, BusinessActionType.MARKETPLACE_PAYOUT_COMPLETED,
                "Admin xác nhận chuyển khoản hoàn tất");
        platformTreasuryService.record(
                PlatformTreasuryAsset.VND,
                PlatformTreasuryDirection.DEBIT,
                PlatformTreasuryEntryType.CREATOR_PAYOUT_COMPLETED,
                PlatformTreasuryReferenceType.CREATOR_PAYOUT,
                payout.getPayoutId(),
                request.getPaidVndAmount(),
                admin,
                payout.getCreator(),
                payout.getExternalTransferReference(),
                payout.getNotes(),
                Map.of("requestedCoinAmount", payout.getRequestedAmount()),
                java.time.Instant.now()
        );
        return toPayoutResponse(payout, true);
    }

    @Transactional
    public CreatorPayoutResponse reject(String adminId, UUID payoutId, RejectCreatorPayoutRequest request) {
        User admin = findUser(adminId);
        CreatorPayout payout = findPayoutForUpdate(payoutId);
        if (payout.getStatus() != CreatorPayoutStatus.REQUESTED && payout.getStatus() != CreatorPayoutStatus.APPROVED) {
            throw new AppException(ErrorCode.MARKETPLACE_PAYOUT_STATE_INVALID);
        }
        releaseReservations(payout);
        payout.setStatus(CreatorPayoutStatus.REJECTED);
        payout.setAdminActor(admin);
        payout.setRejectionReason(request.getReason().trim());
        payout = payoutRepository.save(payout);
        payoutAuditService.record(admin, payout, BusinessActionType.MARKETPLACE_PAYOUT_REJECTED,
                "Admin từ chối yêu cầu rút tiền");
        return toPayoutResponse(payout, true);
    }

    @Transactional
    public CreatorPayoutResponse fail(String adminId, UUID payoutId, RejectCreatorPayoutRequest request) {
        User admin = findUser(adminId);
        CreatorPayout payout = findPayoutForUpdate(payoutId);
        requireStatus(payout, CreatorPayoutStatus.PROCESSING);
        releaseReservations(payout);
        payout.setStatus(CreatorPayoutStatus.FAILED);
        payout.setAdminActor(admin);
        payout.setNotes(request.getReason().trim());
        payout = payoutRepository.save(payout);
        payoutAuditService.record(admin, payout, BusinessActionType.MARKETPLACE_PAYOUT_FAILED,
                "Admin ghi nhận chuyển khoản thất bại");
        return toPayoutResponse(payout, true);
    }

    private void reserveEarnings(CreatorPayout payout) {
        List<CreatorEarningEntry> earnings = earningEntryRepository.findEligibleForUpdate(payout.getCreator().getUserId());
        if (earnings.isEmpty()) {
            throw new AppException(ErrorCode.MARKETPLACE_CREATOR_EARNINGS_INSUFFICIENT);
        }
        Map<UUID, AllocationAmounts> currentAmounts = allocationAmountsFor(earnings);
        int remaining = payout.getRequestedAmount();
        List<CreatorPayoutAllocation> newAllocations = new ArrayList<>();
        for (CreatorEarningEntry earning : earnings) {
            int available = availableAmount(earning, currentAmounts.getOrDefault(earning.getEarningEntryId(), AllocationAmounts.EMPTY));
            if (available <= 0) {
                continue;
            }
            int amount = Math.min(available, remaining);
            CreatorPayoutAllocation allocation = new CreatorPayoutAllocation();
            allocation.setPayout(payout);
            allocation.setEarningEntry(earning);
            allocation.setAmount(amount);
            allocation.setState(CreatorPayoutAllocationState.RESERVED);
            newAllocations.add(allocation);
            remaining -= amount;
            if (remaining == 0) {
                break;
            }
        }
        if (remaining != 0) {
            throw new AppException(ErrorCode.MARKETPLACE_CREATOR_EARNINGS_INSUFFICIENT);
        }
        allocationRepository.saveAll(newAllocations);
        refreshEarningStates(newAllocations);
    }

    private void releaseReservations(CreatorPayout payout) {
        List<CreatorPayoutAllocation> allocations = allocationRepository.findByPayoutPayoutIdOrderByCreatedAtAsc(payout.getPayoutId());
        allocations.stream()
                .filter(allocation -> allocation.getState() == CreatorPayoutAllocationState.RESERVED)
                .forEach(allocation -> allocation.setState(CreatorPayoutAllocationState.RELEASED));
        allocationRepository.saveAll(allocations);
        refreshEarningStates(allocations);
    }

    private void refreshEarningStates(Collection<CreatorPayoutAllocation> allocations) {
        Map<UUID, CreatorEarningEntry> entries = new HashMap<>();
        for (CreatorPayoutAllocation allocation : allocations) {
            entries.put(allocation.getEarningEntry().getEarningEntryId(), allocation.getEarningEntry());
        }
        Map<UUID, AllocationAmounts> amounts = allocationAmountsFor(entries.values());
        for (CreatorEarningEntry entry : entries.values()) {
            if (entry.getState() == CreatorEarningState.REVERSED) {
                continue;
            }
            AllocationAmounts allocation = amounts.getOrDefault(entry.getEarningEntryId(), AllocationAmounts.EMPTY);
            if (allocation.paid() >= entry.getAmount()) {
                entry.setState(CreatorEarningState.PAID);
            } else if (availableAmount(entry, allocation) == 0 && allocation.reserved() > 0) {
                entry.setState(CreatorEarningState.RESERVED);
            } else {
                entry.setState(CreatorEarningState.PENDING);
            }
        }
        earningEntryRepository.saveAll(entries.values());
    }

    private int availableEarnings(String creatorId) {
        List<CreatorEarningEntry> earnings = earningEntryRepository.findByCreatorUserIdOrderByCreatedAtDesc(creatorId);
        Map<UUID, AllocationAmounts> amounts = allocationAmounts(
                allocationRepository.findByPayoutCreatorUserIdOrderByCreatedAtDesc(creatorId));
        return earnings.stream().mapToInt(earning -> availableAmount(
                earning, amounts.getOrDefault(earning.getEarningEntryId(), AllocationAmounts.EMPTY))).sum();
    }

    private Map<UUID, AllocationAmounts> allocationAmountsFor(Collection<CreatorEarningEntry> earnings) {
        if (earnings.isEmpty()) {
            return Map.of();
        }
        List<UUID> earningIds = earnings.stream().map(CreatorEarningEntry::getEarningEntryId).toList();
        Map<UUID, Integer> reserved = aggregateAmounts(earningIds, EnumSet.of(CreatorPayoutAllocationState.RESERVED));
        Map<UUID, Integer> paid = aggregateAmounts(earningIds, EnumSet.of(CreatorPayoutAllocationState.PAID));
        Map<UUID, AllocationAmounts> amounts = new HashMap<>();
        for (UUID earningId : earningIds) {
            amounts.put(earningId, new AllocationAmounts(reserved.getOrDefault(earningId, 0), paid.getOrDefault(earningId, 0)));
        }
        return amounts;
    }

    private Map<UUID, Integer> aggregateAmounts(
            Collection<UUID> earningIds,
            Collection<CreatorPayoutAllocationState> states
    ) {
        Map<UUID, Integer> amounts = new HashMap<>();
        allocationRepository.sumAmountsByEarningEntryIdsAndStates(earningIds, states)
                .forEach(total -> amounts.put(total.getEarningEntryId(), Math.toIntExact(total.getAmount())));
        return amounts;
    }

    private Map<UUID, AllocationAmounts> allocationAmounts(List<CreatorPayoutAllocation> allocations) {
        Map<UUID, AllocationAmounts> amounts = new HashMap<>();
        for (CreatorPayoutAllocation allocation : allocations) {
            UUID earningId = allocation.getEarningEntry().getEarningEntryId();
            AllocationAmounts current = amounts.getOrDefault(earningId, AllocationAmounts.EMPTY);
            amounts.put(earningId, switch (allocation.getState()) {
                case RESERVED -> new AllocationAmounts(current.reserved() + allocation.getAmount(), current.paid());
                case PAID -> new AllocationAmounts(current.reserved(), current.paid() + allocation.getAmount());
                case RELEASED -> current;
            });
        }
        return amounts;
    }

    private int availableAmount(CreatorEarningEntry earning, AllocationAmounts amounts) {
        if (earning.getState() == CreatorEarningState.REVERSED) {
            return 0;
        }
        return Math.max(0, earning.getAmount() - amounts.reserved() - amounts.paid());
    }

    private CreatorPayout findPayoutForUpdate(UUID payoutId) {
        return payoutRepository.findByPayoutIdForUpdate(payoutId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PAYOUT_NOT_FOUND));
    }

    private void requireStatus(CreatorPayout payout, CreatorPayoutStatus expected) {
        if (payout.getStatus() != expected) {
            throw new AppException(ErrorCode.MARKETPLACE_PAYOUT_STATE_INVALID);
        }
    }

    private User findUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private void copyDestinationSnapshot(CreatorPayoutDestination source, CreatorPayout target) {
        target.setDestinationBankName(source.getBankName());
        target.setDestinationBankCode(source.getBankCode());
        target.setDestinationAccountHolder(source.getAccountHolder());
        target.setDestinationAccountNumberEncrypted(null);
        target.setDestinationQrObjectKey(source.getQrObjectKey());
    }

    private CreatorPayoutDestinationResponse toDestinationResponse(CreatorPayoutDestination destination) {
        return CreatorPayoutDestinationResponse.builder()
                .destinationId(destination.getDestinationId())
                .bankName(destination.getBankName())
                .bankCode(destination.getBankCode())
                .accountHolder(destination.getAccountHolder())
                .accountNumberMasked(null)
                .qrViewUrl(s3PresignedUrlService.createViewUrl(destination.getQrObjectKey()))
                .updatedAt(destination.getUpdatedAt())
                .build();
    }

    private CreatorPayoutResponse toPayoutResponse(CreatorPayout payout, boolean includeQrViewUrl) {
        return CreatorPayoutResponse.builder()
                .payoutId(payout.getPayoutId())
                .creatorUserId(payout.getCreator().getUserId())
                .creatorName(payout.getCreator().getFullName())
                .creatorEmail(payout.getCreator().getEmail())
                .requestedAmount(payout.getRequestedAmount())
                .paidVndAmount(payout.getPaidVndAmount())
                .status(payout.getStatus())
                .bankName(payout.getDestinationBankName())
                .bankCode(payout.getDestinationBankCode())
                .accountHolder(payout.getDestinationAccountHolder())
                .accountNumberMasked(null)
                .qrViewUrl(includeQrViewUrl ? s3PresignedUrlService.createViewUrl(payout.getDestinationQrObjectKey()) : null)
                .adminActorUserId(payout.getAdminActor() == null ? null : payout.getAdminActor().getUserId())
                .externalTransferReference(payout.getExternalTransferReference())
                .rejectionReason(payout.getRejectionReason())
                .notes(payout.getNotes())
                .createdAt(payout.getCreatedAt())
                .updatedAt(payout.getUpdatedAt())
                .build();
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record AllocationAmounts(int reserved, int paid) {
        private static final AllocationAmounts EMPTY = new AllocationAmounts(0, 0);
    }
}
