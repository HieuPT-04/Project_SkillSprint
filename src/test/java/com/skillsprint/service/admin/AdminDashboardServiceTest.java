package com.skillsprint.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.dto.response.admin.AdminDashboardResponse;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.mapper.PaymentMapper;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.StudySessionRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.SubscriptionRepository;
import com.skillsprint.repository.UploadedMaterialRepository;
import com.skillsprint.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Dashboard revenue must report subscription money only. Coin top-ups share the
 * payment_transactions table but are wallet deposits, not product revenue.
 */
@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    static final BigDecimal SUBSCRIPTION_REVENUE = new BigDecimal("200000");

    @Mock UserRepository userRepository;
    @Mock StudyWorkspaceRepository workspaceRepository;
    @Mock UploadedMaterialRepository uploadedMaterialRepository;
    @Mock RoadmapRepository roadmapRepository;
    @Mock CalendarTaskRepository calendarTaskRepository;
    @Mock StudySessionRepository studySessionRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock PaymentTransactionRepository paymentTransactionRepository;
    @Mock PaymentMapper paymentMapper;
    @InjectMocks AdminDashboardService service;

    /**
     * Only the SUBSCRIPTION sums are stubbed. A COIN_TOP_UP sum would return null and
     * surface as zero, so any figure that still counted top-ups would have to read them
     * through a purpose-agnostic query — which no longer exists.
     */
    @Test
    void revenueFiguresCountSubscriptionPaymentsOnly() {
        when(paymentTransactionRepository.sumAmountByPurposeAndStatus(
                PaymentPurpose.SUBSCRIPTION, PaymentStatus.PAID)).thenReturn(SUBSCRIPTION_REVENUE);
        when(paymentTransactionRepository.sumAmountByPurposeAndStatusAndPaidAtAfter(
                org.mockito.ArgumentMatchers.eq(PaymentPurpose.SUBSCRIPTION),
                org.mockito.ArgumentMatchers.eq(PaymentStatus.PAID),
                any(Instant.class))).thenReturn(SUBSCRIPTION_REVENUE);

        AdminDashboardResponse response = service.getDashboard(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"));

        assertThat(response.getPayments().getRevenueTotal()).isEqualByComparingTo(SUBSCRIPTION_REVENUE);
        assertThat(response.getPayments().getRevenueToday()).isEqualByComparingTo(SUBSCRIPTION_REVENUE);
        assertThat(response.getPayments().getRevenueThisMonth()).isEqualByComparingTo(SUBSCRIPTION_REVENUE);
        assertThat(response.getOverview().getTotalRevenue()).isEqualByComparingTo(SUBSCRIPTION_REVENUE);
        assertThat(response.getOverview().getTodayRevenue()).isEqualByComparingTo(SUBSCRIPTION_REVENUE);

        verify(paymentTransactionRepository, never()).sumAmountByPurposeAndStatus(
                PaymentPurpose.COIN_TOP_UP, PaymentStatus.PAID);
    }

    @Test
    void dailyRevenueChartCountsSubscriptionPaymentsOnly() {
        BigDecimal dayRevenue = new BigDecimal("50000");
        when(paymentTransactionRepository.sumAmountByPurposeAndStatusAndPaidAtBetween(
                org.mockito.ArgumentMatchers.eq(PaymentPurpose.SUBSCRIPTION),
                org.mockito.ArgumentMatchers.eq(PaymentStatus.PAID),
                any(Instant.class),
                any(Instant.class))).thenReturn(dayRevenue);

        AdminDashboardResponse response = service.getDashboard(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"));

        assertThat(response.getCharts().getRevenueByDay()).hasSize(3);
        assertThat(response.getCharts().getRevenueByDay())
                .allSatisfy(point -> assertThat(point.getAmount()).isEqualByComparingTo(dayRevenue));

        // Every chart bucket is asked for SUBSCRIPTION money; none for top-ups.
        verify(paymentTransactionRepository, org.mockito.Mockito.times(3))
                .sumAmountByPurposeAndStatusAndPaidAtBetween(
                        org.mockito.ArgumentMatchers.eq(PaymentPurpose.SUBSCRIPTION),
                        org.mockito.ArgumentMatchers.eq(PaymentStatus.PAID),
                        any(Instant.class),
                        any(Instant.class));
        verify(paymentTransactionRepository, never()).sumAmountByPurposeAndStatusAndPaidAtBetween(
                org.mockito.ArgumentMatchers.eq(PaymentPurpose.COIN_TOP_UP),
                any(),
                any(),
                any());
    }

    @Test
    void missingRevenueIsReportedAsZeroRatherThanNull() {
        AdminDashboardResponse response = service.getDashboard(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"));

        assertThat(response.getPayments().getRevenueTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPayments().getRevenueToday()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getPayments().getRevenueThisMonth()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getCharts().getRevenueByDay()).hasSize(1);
        assertThat(response.getCharts().getRevenueByDay().get(0).getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Counts are labelled as payment counts, so they still include every purpose. */
    @Test
    void paymentCountsRemainPurposeAgnostic() {
        when(paymentTransactionRepository.count()).thenReturn(7L);
        // Lenient: the dashboard also counts the other statuses, which default to zero.
        org.mockito.Mockito.lenient()
                .when(paymentTransactionRepository.countByStatus(PaymentStatus.PAID)).thenReturn(5L);

        AdminDashboardResponse response = service.getDashboard(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"));

        assertThat(response.getPayments().getTotal()).isEqualTo(7L);
        assertThat(response.getPayments().getPaid()).isEqualTo(5L);
        verify(paymentTransactionRepository).countByStatus(PaymentStatus.PAID);
    }
}
