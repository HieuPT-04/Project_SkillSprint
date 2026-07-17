package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceSaleSettlement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceSaleSettlementRepository extends JpaRepository<MarketplaceSaleSettlement, UUID> {

    Optional<MarketplaceSaleSettlement> findBySaleSaleId(UUID saleId);
}
