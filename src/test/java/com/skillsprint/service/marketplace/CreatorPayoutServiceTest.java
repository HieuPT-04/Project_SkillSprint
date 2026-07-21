package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.skillsprint.dto.request.marketplace.CompleteCreatorPayoutRequest;
import com.skillsprint.dto.request.marketplace.CreateCreatorPayoutRequest;
import com.skillsprint.dto.request.marketplace.RejectCreatorPayoutRequest;
import com.skillsprint.entity.CreatorEarningEntry;
import com.skillsprint.entity.CreatorPayout;
import com.skillsprint.entity.CreatorPayoutAllocation;
import com.skillsprint.entity.CreatorPayoutDestination;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.CreatorPayoutAllocationState;
import com.skillsprint.enums.marketplace.CreatorEarningState;
import com.skillsprint.enums.marketplace.CreatorPayoutStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.CreatorEarningEntryRepository;
import com.skillsprint.repository.CreatorPayoutAllocationRepository;
import com.skillsprint.repository.CreatorPayoutDestinationRepository;
import com.skillsprint.repository.CreatorPayoutRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreatorPayoutServiceTest {

    @Mock UserRepository userRepository;
    @Mock CreatorEarningEntryRepository earningEntryRepository;
    @Mock CreatorPayoutAllocationRepository allocationRepository;
    @Mock CreatorPayoutDestinationRepository destinationRepository;
    @Mock CreatorPayoutRepository payoutRepository;
    @Mock S3PresignedUrlService s3PresignedUrlService;
    @Mock MarketplacePayoutAuditService payoutAuditService;
    @Mock PlatformTreasuryService platformTreasuryService;

    private CreatorPayoutService service;
    private User creator;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new CreatorPayoutService(
                userRepository,
                earningEntryRepository,
                allocationRepository,
                destinationRepository,
                payoutRepository,
                s3PresignedUrlService,
                payoutAuditService,
                platformTreasuryService);
        creator = user("creator", "creator@example.com");
        admin = user("admin", "admin@example.com");
        lenient().when(userRepository.findById("creator")).thenReturn(Optional.of(creator));
        lenient().when(userRepository.findById("admin")).thenReturn(Optional.of(admin));
        lenient().when(payoutRepository.save(any(CreatorPayout.class))).thenAnswer(invocation -> {
            CreatorPayout payout = invocation.getArgument(0);
            if (payout.getPayoutId() == null) {
                payout.setPayoutId(UUID.randomUUID());
            }
            return payout;
        });
    }

    @Test
    void approveReservesTheExactRequestedAmountAcrossEarnings() {
        CreatorPayout payout = payout(CreatorPayoutStatus.REQUESTED, 90);
        CreatorEarningEntry first = earning(50);
        CreatorEarningEntry second = earning(80);
        AtomicBoolean allocationsSaved = new AtomicBoolean();
        when(payoutRepository.findByPayoutIdForUpdate(payout.getPayoutId())).thenReturn(Optional.of(payout));
        when(earningEntryRepository.findEligibleForUpdate("creator")).thenReturn(List.of(first, second));
        when(allocationRepository.saveAll(any())).thenAnswer(invocation -> {
            allocationsSaved.set(true);
            return invocation.getArgument(0);
        });
        when(allocationRepository.sumAmountsByEarningEntryIdsAndStates(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<CreatorPayoutAllocationState> states = List.copyOf(invocation.getArgument(1));
            if (!allocationsSaved.get() || !states.contains(CreatorPayoutAllocationState.RESERVED)) {
                return List.of();
            }
            return List.of(total(first.getEarningEntryId(), 50), total(second.getEarningEntryId(), 40));
        });

        service.approve("admin", payout.getPayoutId());

        verify(allocationRepository).saveAll(argThat(allocations -> {
            List<CreatorPayoutAllocation> values = new ArrayList<>();
            allocations.forEach(values::add);
            return values.stream().map(CreatorPayoutAllocation::getAmount).toList().equals(List.of(50, 40));
        }));
        assertThat(payout.getStatus()).isEqualTo(CreatorPayoutStatus.APPROVED);
        assertThat(payout.getAdminActor()).isSameAs(admin);
        verify(payoutAuditService).record(admin, payout,
                com.skillsprint.enums.log.BusinessActionType.MARKETPLACE_PAYOUT_APPROVED,
                "Admin duyệt yêu cầu rút tiền");
    }

    @Test
    void rejectReleasesPreviouslyReservedAllocations() {
        CreatorPayout payout = payout(CreatorPayoutStatus.APPROVED, 50);
        CreatorEarningEntry entry = earning(50);
        CreatorPayoutAllocation allocation = allocation(payout, entry, 50, CreatorPayoutAllocationState.RESERVED);
        when(payoutRepository.findByPayoutIdForUpdate(payout.getPayoutId())).thenReturn(Optional.of(payout));
        when(allocationRepository.findByPayoutPayoutIdOrderByCreatedAtAsc(payout.getPayoutId())).thenReturn(List.of(allocation));
        when(allocationRepository.sumAmountsByEarningEntryIdsAndStates(any(), any())).thenReturn(List.of());

        service.reject("admin", payout.getPayoutId(), reason("Insufficient verification"));

        assertThat(allocation.getState()).isEqualTo(CreatorPayoutAllocationState.RELEASED);
        assertThat(payout.getStatus()).isEqualTo(CreatorPayoutStatus.REJECTED);
        assertThat(payout.getRejectionReason()).isEqualTo("Insufficient verification");
    }

    @Test
    void completionRequiresProcessingAndCannotPayTwice() {
        CreatorPayout payout = payout(CreatorPayoutStatus.PROCESSING, 50);
        CreatorEarningEntry entry = earning(50);
        CreatorPayoutAllocation allocation = allocation(payout, entry, 50, CreatorPayoutAllocationState.RESERVED);
        when(payoutRepository.findByPayoutIdForUpdate(payout.getPayoutId())).thenReturn(Optional.of(payout));
        when(allocationRepository.findByPayoutPayoutIdOrderByCreatedAtAsc(payout.getPayoutId())).thenReturn(List.of(allocation));
        when(allocationRepository.sumAmountsByEarningEntryIdsAndStates(any(), any())).thenReturn(List.of());

        service.complete("admin", payout.getPayoutId(), complete("BANK-REF-001"));

        assertThat(payout.getStatus()).isEqualTo(CreatorPayoutStatus.COMPLETED);
        assertThat(allocation.getState()).isEqualTo(CreatorPayoutAllocationState.PAID);
        assertThat(payout.getExternalTransferReference()).isEqualTo("BANK-REF-001");
        assertThatThrownBy(() -> service.complete("admin", payout.getPayoutId(), complete("BANK-REF-002")))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_PAYOUT_STATE_INVALID);
    }

    @Test
    void requestSnapshotsTheActiveDestinationWithoutUsingTheCoinWallet() {
        CreatorPayoutDestination destination = destination();
        CreatorEarningEntry entry = earning(100);
        when(destinationRepository.findByCreatorUserIdAndActiveTrue("creator")).thenReturn(Optional.of(destination));
        when(earningEntryRepository.findByCreatorUserIdOrderByCreatedAtDesc("creator")).thenReturn(List.of(entry));
        when(allocationRepository.findByPayoutCreatorUserIdOrderByCreatedAtDesc("creator")).thenReturn(List.of());

        var response = service.requestPayout("creator", request(60));

        assertThat(response.getRequestedAmount()).isEqualTo(60);
        assertThat(response.getStatus()).isEqualTo(CreatorPayoutStatus.REQUESTED);
        verify(payoutRepository).save(argThat(saved ->
                destination.getQrObjectKey().equals(saved.getDestinationQrObjectKey())
                        && saved.getDestinationAccountNumberEncrypted() == null));
    }

    @Test
    void reversedEarningsAreNeverAvailableForDisplayOrPayout() {
        CreatorEarningEntry reversed = earning(100);
        reversed.setState(CreatorEarningState.REVERSED);
        MarketplaceSale sale = new MarketplaceSale();
        sale.setSaleId(UUID.randomUUID());
        MarketplaceSaleSettlement settlement = new MarketplaceSaleSettlement();
        settlement.setSettlementId(UUID.randomUUID());
        settlement.setSale(sale);
        reversed.setSettlement(settlement);
        when(earningEntryRepository.findByCreatorUserIdOrderByCreatedAtDesc("creator")).thenReturn(List.of(reversed));
        when(allocationRepository.findByPayoutCreatorUserIdOrderByCreatedAtDesc("creator")).thenReturn(List.of());
        when(destinationRepository.findByCreatorUserIdAndActiveTrue("creator")).thenReturn(Optional.of(destination()));

        assertThat(service.getEarnings("creator").getAvailableAmount()).isZero();
        assertThatThrownBy(() -> service.requestPayout("creator", request(1)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_CREATOR_EARNINGS_INSUFFICIENT);
        verify(payoutRepository, never()).save(any(CreatorPayout.class));
    }

    private CreatorPayout payout(CreatorPayoutStatus status, int amount) {
        CreatorPayout payout = new CreatorPayout();
        payout.setPayoutId(UUID.randomUUID());
        payout.setCreator(creator);
        payout.setStatus(status);
        payout.setRequestedAmount(amount);
        payout.setDestinationQrObjectKey("creator-payouts/creator/qr/image.png");
        payout.setDestinationBankName("MBBank");
        payout.setDestinationAccountHolder("Creator");
        return payout;
    }

    private CreatorEarningEntry earning(int amount) {
        CreatorEarningEntry earning = new CreatorEarningEntry();
        earning.setEarningEntryId(UUID.randomUUID());
        earning.setCreator(creator);
        earning.setAmount(amount);
        return earning;
    }

    private CreatorPayoutAllocation allocation(
            CreatorPayout payout,
            CreatorEarningEntry earning,
            int amount,
            CreatorPayoutAllocationState state
    ) {
        CreatorPayoutAllocation allocation = new CreatorPayoutAllocation();
        allocation.setPayout(payout);
        allocation.setEarningEntry(earning);
        allocation.setAmount(amount);
        allocation.setState(state);
        return allocation;
    }

    private CreatorPayoutDestination destination() {
        CreatorPayoutDestination destination = new CreatorPayoutDestination();
        destination.setCreator(creator);
        destination.setBankName("MBBank");
        destination.setAccountHolder("Creator");
        destination.setQrObjectKey("creator-payouts/creator/qr/image.png");
        return destination;
    }

    private CreatorPayoutAllocationRepository.EarningAllocationTotal total(UUID earningEntryId, long amount) {
        CreatorPayoutAllocationRepository.EarningAllocationTotal total = org.mockito.Mockito.mock(
                CreatorPayoutAllocationRepository.EarningAllocationTotal.class);
        when(total.getEarningEntryId()).thenReturn(earningEntryId);
        when(total.getAmount()).thenReturn(amount);
        return total;
    }

    private CreateCreatorPayoutRequest request(int amount) {
        CreateCreatorPayoutRequest request = new CreateCreatorPayoutRequest();
        request.setAmount(amount);
        return request;
    }

    private CompleteCreatorPayoutRequest complete(String reference) {
        CompleteCreatorPayoutRequest request = new CompleteCreatorPayoutRequest();
        request.setExternalTransferReference(reference);
        return request;
    }

    private RejectCreatorPayoutRequest reason(String value) {
        RejectCreatorPayoutRequest request = new RejectCreatorPayoutRequest();
        request.setReason(value);
        return request;
    }

    private User user(String userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(userId);
        return user;
    }
}
