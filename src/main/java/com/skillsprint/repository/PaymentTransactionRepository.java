package com.skillsprint.repository;

import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTxnRef(String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findWithLockByTxnRef(String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findWithLockByPaymentId(UUID paymentId);

    Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId);

    List<PaymentTransaction> findByUserUserIdOrderByCreatedAtDesc(String userId);

    long countByStatus(PaymentStatus status);

    long countByStatusAndExpireAtBefore(PaymentStatus status, Instant expireAt);

    List<PaymentTransaction> findTop5ByOrderByCreatedAtDesc();

    List<PaymentTransaction> findByUserUserIdAndStatusOrderByCreatedAtDesc(
            String userId,
            PaymentStatus status
    );

    Optional<PaymentTransaction> findFirstByUserUserIdAndPlanPlanIdAndProviderAndStatusAndExpireAtAfterOrderByCreatedAtDesc(
            String userId,
            UUID planId,
            PaymentProvider provider,
            PaymentStatus status,
            Instant expireAt
    );

    List<PaymentTransaction> findByProviderAndStatusAndExpireAtBefore(
            PaymentProvider provider,
            PaymentStatus status,
            Instant expireAt
    );

    List<PaymentTransaction> findByProviderAndStatusOrderByCreatedAtDesc(
            PaymentProvider provider,
            PaymentStatus status
    );

    @Query(
            value = """
                    SELECT pt.* FROM payment_transactions pt
                    JOIN users u ON u.user_id = pt.user_id
                    WHERE (:status IS NULL OR pt.status = :status)
                      AND (
                            :search IS NULL
                            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :search, '%'))
                      )
                    ORDER BY pt.created_at DESC
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM payment_transactions pt
                    JOIN users u ON u.user_id = pt.user_id
                    WHERE (:status IS NULL OR pt.status = :status)
                      AND (
                            :search IS NULL
                            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(u.full_name) LIKE LOWER(CONCAT('%', :search, '%'))
                      )
                    """,
            nativeQuery = true)
    Page<PaymentTransaction> searchAdminPayments(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from PaymentTransaction payment
            where payment.status = :status
            """)
    java.math.BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from PaymentTransaction payment
            where payment.status = :status
              and payment.paidAt >= :from
            """)
    java.math.BigDecimal sumAmountByStatusAndPaidAtAfter(
            @Param("status") PaymentStatus status,
            @Param("from") Instant from
    );

    @Query("""
            select coalesce(sum(payment.amount), 0)
            from PaymentTransaction payment
            where payment.status = :status
              and payment.paidAt >= :from
              and payment.paidAt < :to
            """)
    java.math.BigDecimal sumAmountByStatusAndPaidAtBetween(
            @Param("status") PaymentStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
