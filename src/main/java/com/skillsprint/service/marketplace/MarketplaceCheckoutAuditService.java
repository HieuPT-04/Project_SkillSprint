package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.repository.BusinessActivityLogRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

/**
 * Records a completed marketplace checkout in the same transaction as its sale.
 * Metadata is deliberately limited to immutable checkout and settlement identifiers and amounts.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceCheckoutAuditService {

    BusinessActivityLogRepository activityLogRepository;
    ObjectMapper objectMapper;

    public void recordCompletedCheckout(MarketplaceSale sale, MarketplaceSaleSettlement settlement) {
        BusinessActivityLog log = new BusinessActivityLog();
        log.setUser(sale.getBuyer());
        log.setEntityType(BusinessEntityType.MARKETPLACE_SALE);
        log.setEntityId(sale.getSaleId());
        log.setActionType(sale.getSourceEntitlement() == null
                ? BusinessActionType.MARKETPLACE_SALE_COMPLETED
                : BusinessActionType.MARKETPLACE_VERSION_UPGRADE_COMPLETED);
        log.setTitle(sale.getSourceEntitlement() == null
                ? "Hoàn tất thanh toán Quiz Pack"
                : "Hoàn tất nâng cấp phiên bản Quiz Pack");
        log.setDescription("Ghi nhận thanh toán Marketplace thành công");
        log.setNewValue(toJson(checkoutSnapshot(sale, settlement)));
        log.setMetadata(toJson(metadata(sale)));
        activityLogRepository.saveAndFlush(log);
    }

    private Map<String, Object> checkoutSnapshot(MarketplaceSale sale, MarketplaceSaleSettlement settlement) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("saleId", sale.getSaleId());
        snapshot.put("grossCoinAmount", sale.getGrossCoinAmount());
        snapshot.put("grossVndAmount", sale.getGrossVndAmount());
        snapshot.put("originalGrossCoinAmount", sale.getOriginalGrossCoinAmount());
        snapshot.put("discountCoinAmount", sale.getDiscountCoinAmount());
        snapshot.put("creatorAmount", settlement.getCreatorAmount());
        snapshot.put("platformAmount", settlement.getPlatformAmount());
        snapshot.put("creatorShareBps", settlement.getCreatorShareBps());
        snapshot.put("platformShareBps", settlement.getPlatformShareBps());
        snapshot.put("coinToVndRate", settlement.getCoinToVndRate());
        return snapshot;
    }

    private Map<String, Object> metadata(MarketplaceSale sale) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("module", "MARKETPLACE");
        metadata.put("packId", sale.getPack().getPackId());
        metadata.put("packVersionId", sale.getPackVersion().getVersionId());
        metadata.put("sourceEntitlementId", sale.getSourceEntitlement() == null
                ? null
                : sale.getSourceEntitlement().getEntitlementId());
        return metadata;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize marketplace checkout audit event", ex);
        }
    }
}
