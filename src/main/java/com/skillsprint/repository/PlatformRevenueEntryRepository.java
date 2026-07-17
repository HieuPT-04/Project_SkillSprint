package com.skillsprint.repository;

import com.skillsprint.entity.PlatformRevenueEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRevenueEntryRepository extends JpaRepository<PlatformRevenueEntry, UUID> {

    Optional<PlatformRevenueEntry> findBySaleSaleId(UUID saleId);
}
