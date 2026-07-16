package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceSale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceSaleRepository extends JpaRepository<MarketplaceSale, UUID> {

    Optional<MarketplaceSale> findByBuyerUserIdAndIdempotencyKey(String buyerId, String idempotencyKey);
}
