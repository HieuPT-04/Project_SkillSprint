package com.skillsprint.repository;

import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTxnRef(String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findWithLockByTxnRef(String txnRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentTransaction> findWithLockByPaymentId(UUID paymentId);

    Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId);

    List<PaymentTransaction> findByUserUserIdOrderByCreatedAtDesc(String userId);

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
}
