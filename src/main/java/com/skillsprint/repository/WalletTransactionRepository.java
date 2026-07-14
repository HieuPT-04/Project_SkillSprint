package com.skillsprint.repository;

import com.skillsprint.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, java.util.UUID> { java.util.List<WalletTransaction> findByWalletUserUserIdOrderByCreatedAtDesc(String userId); }
