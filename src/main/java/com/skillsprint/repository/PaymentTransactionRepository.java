package com.skillsprint.repository;

import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.enums.payment.PaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTxnRef(String txnRef);

    List<PaymentTransaction> findByUserUserIdOrderByCreatedAtDesc(String userId);

    List<PaymentTransaction> findByUserUserIdAndStatusOrderByCreatedAtDesc(
            String userId,
            PaymentStatus status
    );
}