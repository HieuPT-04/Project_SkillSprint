package com.skillsprint.mapper;

import com.skillsprint.dto.response.marketplace.MarketplaceVersionPurchaseResponse;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceCheckoutMapper {

    public MarketplaceVersionPurchaseResponse toResponse(
            MarketplaceSale sale,
            MarketplaceEntitlement entitlement,
            MarketplaceSaleSettlement settlement,
            int remainingCoinBalance
    ) {
        return MarketplaceVersionPurchaseResponse.builder()
                .saleId(sale.getSaleId())
                .entitlementId(entitlement.getEntitlementId())
                .packId(sale.getPack().getPackId())
                .packVersionId(sale.getPackVersion().getVersionId())
                .versionNo(sale.getPackVersion().getVersionNo())
                .grossCoinAmount(sale.getGrossCoinAmount())
                .creatorAmount(settlement.getCreatorAmount())
                .platformAmount(settlement.getPlatformAmount())
                .remainingCoinBalance(remainingCoinBalance)
                .build();
    }
}
