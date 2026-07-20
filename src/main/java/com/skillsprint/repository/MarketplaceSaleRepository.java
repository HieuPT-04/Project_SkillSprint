package com.skillsprint.repository;

import com.skillsprint.entity.MarketplaceSale;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketplaceSaleRepository extends JpaRepository<MarketplaceSale, UUID> {

    Optional<MarketplaceSale> findByBuyerUserIdAndIdempotencyKey(String buyerId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sale from MarketplaceSale sale where sale.saleId = :saleId")
    Optional<MarketplaceSale> findByIdForUpdate(@Param("saleId") UUID saleId);
}
