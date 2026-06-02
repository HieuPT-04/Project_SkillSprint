package com.skillsprint.service.admin;

import com.skillsprint.dto.response.admin.AdminDashboardResponse;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.mapper.PaymentMapper;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.SubscriptionRepository;
import com.skillsprint.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminDashboardService {

    static ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    UserRepository userRepository;
    StudyWorkspaceRepository workspaceRepository;
    SubscriptionRepository subscriptionRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    PaymentMapper paymentMapper;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        Instant now = Instant.now();
        Instant todayStart = LocalDate.now(VN_ZONE).atStartOfDay(VN_ZONE).toInstant();
        Instant monthStart = LocalDate.now(VN_ZONE).withDayOfMonth(1).atStartOfDay(VN_ZONE).toInstant();

        return AdminDashboardResponse.builder()
                .generatedAt(now)
                .users(buildUserStats(todayStart))
                .workspaces(buildWorkspaceStats())
                .subscriptions(buildSubscriptionStats())
                .payments(buildPaymentStats(todayStart, monthStart))
                .recentPayments(buildRecentPayments())
                .build();
    }

    private AdminDashboardResponse.UserStats buildUserStats(Instant todayStart) {
        return AdminDashboardResponse.UserStats.builder()
                .total(userRepository.count())
                .active(userRepository.countByStatus(UserStatus.ACTIVE))
                .disabled(userRepository.countByStatus(UserStatus.DISABLED))
                .newToday(userRepository.countByCreatedAtAfter(todayStart))
                .build();
    }

    private AdminDashboardResponse.WorkspaceStats buildWorkspaceStats() {
        return AdminDashboardResponse.WorkspaceStats.builder()
                .total(workspaceRepository.countByStatusNot(WorkspaceStatus.DELETED))
                .active(workspaceRepository.countByStatus(WorkspaceStatus.ACTIVE))
                .archived(workspaceRepository.countByStatus(WorkspaceStatus.ARCHIVED))
                .build();
    }

    private AdminDashboardResponse.SubscriptionStats buildSubscriptionStats() {
        return AdminDashboardResponse.SubscriptionStats.builder()
                .trial(subscriptionRepository.countByStatus(SubscriptionStatus.TRIAL))
                .active(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE))
                .expired(subscriptionRepository.countByStatus(SubscriptionStatus.EXPIRED))
                .canceled(subscriptionRepository.countByStatus(SubscriptionStatus.CANCELED))
                .pastDue(subscriptionRepository.countByStatus(SubscriptionStatus.PAST_DUE))
                .free(subscriptionRepository.countByStatusAndPlanType(SubscriptionStatus.ACTIVE, ServicePlanType.FREE))
                .skillBuilder(subscriptionRepository.countByStatusAndPlanType(SubscriptionStatus.ACTIVE, ServicePlanType.SKILL_BUILDER))
                .premium(subscriptionRepository.countByStatusAndPlanType(SubscriptionStatus.ACTIVE, ServicePlanType.PREMIUM))
                .build();
    }

    private AdminDashboardResponse.PaymentStats buildPaymentStats(Instant todayStart, Instant monthStart) {
        return AdminDashboardResponse.PaymentStats.builder()
                .total(paymentTransactionRepository.count())
                .pending(paymentTransactionRepository.countByStatus(PaymentStatus.PENDING))
                .paid(paymentTransactionRepository.countByStatus(PaymentStatus.PAID))
                .failed(paymentTransactionRepository.countByStatus(PaymentStatus.FAILED))
                .canceled(paymentTransactionRepository.countByStatus(PaymentStatus.CANCELED))
                .expired(paymentTransactionRepository.countByStatus(PaymentStatus.EXPIRED))
                .revenueTotal(defaultMoney(paymentTransactionRepository.sumAmountByStatus(PaymentStatus.PAID)))
                .revenueToday(defaultMoney(paymentTransactionRepository.sumAmountByStatusAndPaidAtAfter(PaymentStatus.PAID, todayStart)))
                .revenueThisMonth(defaultMoney(paymentTransactionRepository.sumAmountByStatusAndPaidAtAfter(PaymentStatus.PAID, monthStart)))
                .build();
    }

    private List<PaymentTransactionResponse> buildRecentPayments() {
        return paymentTransactionRepository.findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
