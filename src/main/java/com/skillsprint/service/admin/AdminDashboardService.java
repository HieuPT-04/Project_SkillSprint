package com.skillsprint.service.admin;

import com.skillsprint.dto.response.admin.AdminDashboardResponse;
import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.entity.User;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.material.MaterialProcessingStatus;
import com.skillsprint.enums.payment.PaymentPurpose;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.plan.SubscriptionStatus;
import com.skillsprint.enums.roadmap.RoadmapStatus;
import com.skillsprint.enums.session.StudySessionStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.mapper.PaymentMapper;
import com.skillsprint.repository.CalendarTaskRepository;
import com.skillsprint.repository.RoadmapRepository;
import com.skillsprint.repository.PaymentTransactionRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.StudySessionRepository;
import com.skillsprint.repository.SubscriptionRepository;
import com.skillsprint.repository.UploadedMaterialRepository;
import com.skillsprint.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

    /**
     * Dashboard revenue means subscription revenue. Coin top-ups also live in
     * payment_transactions, but a top-up is a wallet deposit the buyer can still spend,
     * not money the product has earned, so it must never be added to these figures.
     * Coin top-up reporting is a separate metric that does not exist yet.
     */
    static PaymentPurpose REVENUE_PURPOSE = PaymentPurpose.SUBSCRIPTION;

    UserRepository userRepository;
    StudyWorkspaceRepository workspaceRepository;
    UploadedMaterialRepository uploadedMaterialRepository;
    RoadmapRepository roadmapRepository;
    CalendarTaskRepository calendarTaskRepository;
    StudySessionRepository studySessionRepository;
    SubscriptionRepository subscriptionRepository;
    PaymentTransactionRepository paymentTransactionRepository;
    PaymentMapper paymentMapper;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(LocalDate from, LocalDate to) {
        Instant now = Instant.now();
        Instant todayStart = LocalDate.now(VN_ZONE).atStartOfDay(VN_ZONE).toInstant();
        Instant monthStart = LocalDate.now(VN_ZONE).withDayOfMonth(1).atStartOfDay(VN_ZONE).toInstant();
        DateRange dateRange = normalizeRange(from, to);

        AdminDashboardResponse.UserStats userStats = buildUserStats(todayStart, dateRange);
        AdminDashboardResponse.WorkspaceStats workspaceStats = buildWorkspaceStats();
        AdminDashboardResponse.SubscriptionStats subscriptionStats = buildSubscriptionStats();
        AdminDashboardResponse.PaymentStats paymentStats = buildPaymentStats(todayStart, monthStart);

        return AdminDashboardResponse.builder()
                .generatedAt(now)
                .range(AdminDashboardResponse.DateRange.builder()
                        .from(dateRange.from())
                        .to(dateRange.to())
                        .build())
                .overview(buildOverviewStats(userStats, subscriptionStats, paymentStats))
                .users(userStats)
                .workspaces(workspaceStats)
                .subscriptions(subscriptionStats)
                .payments(paymentStats)
                .learning(buildLearningStats())
                .charts(buildChartStats(dateRange))
                .alerts(buildAlertStats(userStats, paymentStats))
                .recentUsers(buildRecentUsers())
                .recentPayments(buildRecentPayments())
                .build();
    }

    private AdminDashboardResponse.UserStats buildUserStats(Instant todayStart, DateRange dateRange) {
        return AdminDashboardResponse.UserStats.builder()
                .total(userRepository.count())
                .active(userRepository.countByStatus(UserStatus.ACTIVE))
                .disabled(userRepository.countByStatus(UserStatus.DISABLED))
                .newToday(userRepository.countByCreatedAtAfter(todayStart))
                .newInRange(countUsersInDayRange(dateRange.from(), dateRange.to()))
                .emailVerified(userRepository.countByEmailVerified(true))
                .unverified(userRepository.countByEmailVerified(false))
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

    // Counts stay purpose-agnostic: they are labelled as payment counts and every
    // payment, whatever its purpose, is one. Revenue is not — see REVENUE_PURPOSE.
    private AdminDashboardResponse.PaymentStats buildPaymentStats(Instant todayStart, Instant monthStart) {
        return AdminDashboardResponse.PaymentStats.builder()
                .total(paymentTransactionRepository.count())
                .pending(paymentTransactionRepository.countByStatus(PaymentStatus.PENDING))
                .paid(paymentTransactionRepository.countByStatus(PaymentStatus.PAID))
                .failed(paymentTransactionRepository.countByStatus(PaymentStatus.FAILED))
                .canceled(paymentTransactionRepository.countByStatus(PaymentStatus.CANCELED))
                .expired(paymentTransactionRepository.countByStatus(PaymentStatus.EXPIRED))
                .revenueTotal(defaultMoney(paymentTransactionRepository.sumAmountByPurposeAndStatus(
                        REVENUE_PURPOSE,
                        PaymentStatus.PAID
                )))
                .revenueToday(defaultMoney(paymentTransactionRepository.sumAmountByPurposeAndStatusAndPaidAtAfter(
                        REVENUE_PURPOSE,
                        PaymentStatus.PAID,
                        todayStart
                )))
                .revenueThisMonth(defaultMoney(paymentTransactionRepository.sumAmountByPurposeAndStatusAndPaidAtAfter(
                        REVENUE_PURPOSE,
                        PaymentStatus.PAID,
                        monthStart
                )))
                .build();
    }

    private AdminDashboardResponse.OverviewStats buildOverviewStats(
            AdminDashboardResponse.UserStats userStats,
            AdminDashboardResponse.SubscriptionStats subscriptionStats,
            AdminDashboardResponse.PaymentStats paymentStats
    ) {
        return AdminDashboardResponse.OverviewStats.builder()
                .totalUsers(userStats.getTotal())
                .activeUsers(userStats.getActive())
                .paidUsers(subscriptionStats.getSkillBuilder() + subscriptionStats.getPremium())
                .activeSubscriptions(subscriptionStats.getActive())
                .pendingPayments(paymentStats.getPending())
                .failedPayments(paymentStats.getFailed())
                .totalRevenue(paymentStats.getRevenueTotal())
                .todayRevenue(paymentStats.getRevenueToday())
                .build();
    }

    private AdminDashboardResponse.LearningStats buildLearningStats() {
        return AdminDashboardResponse.LearningStats.builder()
                .materials(AdminDashboardResponse.MaterialStats.builder()
                        .total(uploadedMaterialRepository.count())
                        .pending(uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.PENDING))
                        .processing(countProcessingMaterials())
                        .completed(uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.COMPLETED))
                        .failed(uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.FAILED))
                        .build())
                .roadmaps(AdminDashboardResponse.RoadmapStats.builder()
                        .total(roadmapRepository.count())
                        .draft(roadmapRepository.countByStatus(RoadmapStatus.DRAFT))
                        .active(roadmapRepository.countByStatus(RoadmapStatus.ACTIVE))
                        .completed(roadmapRepository.countByStatus(RoadmapStatus.COMPLETED))
                        .archived(roadmapRepository.countByStatus(RoadmapStatus.ARCHIVED))
                        .build())
                .calendarTasks(AdminDashboardResponse.CalendarTaskStats.builder()
                        .total(calendarTaskRepository.count())
                        .todo(calendarTaskRepository.countByStatus(CalendarTaskStatus.TODO))
                        .completed(calendarTaskRepository.countByStatus(CalendarTaskStatus.COMPLETED))
                        .missed(calendarTaskRepository.countByStatus(CalendarTaskStatus.MISSED))
                        .cancelled(calendarTaskRepository.countByStatus(CalendarTaskStatus.CANCELLED))
                        .build())
                .studySessions(AdminDashboardResponse.StudySessionStats.builder()
                        .total(studySessionRepository.count())
                        .inProgress(studySessionRepository.countByStatus(StudySessionStatus.IN_PROGRESS))
                        .completed(studySessionRepository.countByStatus(StudySessionStatus.COMPLETED))
                        .build())
                .build();
    }

    private AdminDashboardResponse.ChartStats buildChartStats(DateRange dateRange) {
        List<AdminDashboardResponse.RevenuePoint> revenueByDay = new ArrayList<>();
        List<AdminDashboardResponse.CountPoint> newUsersByDay = new ArrayList<>();

        LocalDate current = dateRange.from();
        while (!current.isAfter(dateRange.to())) {
            Instant dayStart = current.atStartOfDay(VN_ZONE).toInstant();
            Instant nextDayStart = current.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

            revenueByDay.add(AdminDashboardResponse.RevenuePoint.builder()
                    .date(current)
                    .amount(defaultMoney(paymentTransactionRepository.sumAmountByPurposeAndStatusAndPaidAtBetween(
                            REVENUE_PURPOSE,
                            PaymentStatus.PAID,
                            dayStart,
                            nextDayStart
                    )))
                    .build());

            newUsersByDay.add(AdminDashboardResponse.CountPoint.builder()
                    .date(current)
                    .count(userRepository.countByCreatedAtBetween(dayStart, nextDayStart))
                    .build());

            current = current.plusDays(1);
        }

        return AdminDashboardResponse.ChartStats.builder()
                .revenueByDay(revenueByDay)
                .newUsersByDay(newUsersByDay)
                .build();
    }

    private AdminDashboardResponse.AlertStats buildAlertStats(
            AdminDashboardResponse.UserStats userStats,
            AdminDashboardResponse.PaymentStats paymentStats
    ) {
        return AdminDashboardResponse.AlertStats.builder()
                .pendingPaymentsOverdue(paymentTransactionRepository.countByStatusAndExpireAtBefore(
                        PaymentStatus.PENDING,
                        Instant.now()
                ))
                .failedPayments(paymentStats.getFailed())
                .failedMaterialProcessing(uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.FAILED))
                .unverifiedUsers(userStats.getUnverified())
                .build();
    }

    private List<PaymentTransactionResponse> buildRecentPayments() {
        return paymentTransactionRepository.findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    private List<AdminDashboardResponse.RecentUser> buildRecentUsers() {
        return userRepository.findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toRecentUser)
                .toList();
    }

    private AdminDashboardResponse.RecentUser toRecentUser(User user) {
        return AdminDashboardResponse.RecentUser.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .emailVerified(user.isEmailVerified())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private long countProcessingMaterials() {
        return uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.EXTRACTING)
                + uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.EXTRACTED)
                + uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.CLEANING)
                + uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.CHUNKING)
                + uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.ANALYZING)
                + uploadedMaterialRepository.countByProcessingStatus(MaterialProcessingStatus.REVIEW_REQUIRED);
    }

    private long countUsersInDayRange(LocalDate from, LocalDate to) {
        Instant fromInclusive = from.atStartOfDay(VN_ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
        return userRepository.countByCreatedAtBetween(fromInclusive, toExclusive);
    }

    private DateRange normalizeRange(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(VN_ZONE);
        LocalDate normalizedFrom = from == null ? today.withDayOfMonth(1) : from;
        LocalDate normalizedTo = to == null ? today : to;

        if (normalizedTo.isBefore(normalizedFrom)) {
            normalizedTo = normalizedFrom;
        }

        if (normalizedFrom.plusDays(30).isBefore(normalizedTo)) {
            normalizedTo = normalizedFrom.plusDays(30);
        }

        return new DateRange(normalizedFrom, normalizedTo);
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
