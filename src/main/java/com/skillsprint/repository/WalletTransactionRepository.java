package com.skillsprint.repository;

import com.skillsprint.entity.WalletTransaction;
import com.skillsprint.enums.marketplace.WalletTransactionReferenceType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, java.util.UUID> {

    java.util.List<WalletTransaction> findByWalletUserUserIdOrderByCreatedAtDesc(String userId);

    @EntityGraph(attributePaths = "adjustedBy")
    java.util.List<WalletTransaction> findTop20ByWalletUserUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Guards a repeated credit from the same source. For COIN_TOP_UP the reference is the
     * payment id, and V8's partial unique index enforces the same rule in the database.
     */
    boolean existsByReferenceTypeAndReferenceId(
            WalletTransactionReferenceType referenceType,
            java.util.UUID referenceId
    );
}
