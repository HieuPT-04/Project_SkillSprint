package com.skillsprint.dto.response.admin;

import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import com.skillsprint.enums.auth.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminDashboardResponse {

    Instant generatedAt;
    DateRange range;
    OverviewStats overview;
    UserStats users;
    WorkspaceStats workspaces;
    SubscriptionStats subscriptions;
    PaymentStats payments;
    LearningStats learning;
    ChartStats charts;
    AlertStats alerts;
    List<RecentUser> recentUsers;
    List<PaymentTransactionResponse> recentPayments;

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class DateRange {
        LocalDate from;
        LocalDate to;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OverviewStats {
        long totalUsers;
        long activeUsers;
        long paidUsers;
        long activeSubscriptions;
        long pendingPayments;
        long failedPayments;
        BigDecimal totalRevenue;
        BigDecimal todayRevenue;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class UserStats {
        long total;
        long active;
        long disabled;
        long newToday;
        long newInRange;
        long emailVerified;
        long unverified;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class WorkspaceStats {
        long total;
        long active;
        long archived;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SubscriptionStats {
        long trial;
        long active;
        long expired;
        long canceled;
        long pastDue;
        long free;
        long skillBuilder;
        long premium;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PaymentStats {
        long total;
        long pending;
        long paid;
        long failed;
        long canceled;
        long expired;
        BigDecimal revenueTotal;
        BigDecimal revenueToday;
        BigDecimal revenueThisMonth;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class LearningStats {
        MaterialStats materials;
        RoadmapStats roadmaps;
        CalendarTaskStats calendarTasks;
        StudySessionStats studySessions;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MaterialStats {
        long total;
        long pending;
        long processing;
        long completed;
        long failed;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RoadmapStats {
        long total;
        long draft;
        long active;
        long completed;
        long archived;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CalendarTaskStats {
        long total;
        long todo;
        long completed;
        long missed;
        long cancelled;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class StudySessionStats {
        long total;
        long inProgress;
        long completed;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ChartStats {
        List<RevenuePoint> revenueByDay;
        List<CountPoint> newUsersByDay;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RevenuePoint {
        LocalDate date;
        BigDecimal amount;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CountPoint {
        LocalDate date;
        long count;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class AlertStats {
        long pendingPaymentsOverdue;
        long failedPayments;
        long failedMaterialProcessing;
        long unverifiedUsers;
    }

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RecentUser {
        String userId;
        String email;
        String fullName;
        boolean emailVerified;
        UserStatus status;
        Instant createdAt;
        Instant lastLoginAt;
    }
}
