package com.skillsprint.dto.response.admin;

import com.skillsprint.dto.response.payment.PaymentTransactionResponse;
import java.math.BigDecimal;
import java.time.Instant;
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
    UserStats users;
    WorkspaceStats workspaces;
    SubscriptionStats subscriptions;
    PaymentStats payments;
    List<PaymentTransactionResponse> recentPayments;

    @Getter
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class UserStats {
        long total;
        long active;
        long disabled;
        long newToday;
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
}
