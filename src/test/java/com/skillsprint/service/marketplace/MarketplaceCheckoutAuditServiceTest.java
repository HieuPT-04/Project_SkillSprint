package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.MarketplaceEntitlement;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceSale;
import com.skillsprint.entity.MarketplaceSaleSettlement;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.repository.BusinessActivityLogRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class MarketplaceCheckoutAuditServiceTest {

    @Test
    void writesACompletedCheckoutEventWithOnlySafeCheckoutMetadata() {
        BusinessActivityLogRepository repository = Mockito.mock(BusinessActivityLogRepository.class);
        MarketplaceCheckoutAuditService service = new MarketplaceCheckoutAuditService(repository, new ObjectMapper());
        MarketplaceSale sale = sale(false);

        service.recordCompletedCheckout(sale, settlement());

        ArgumentCaptor<BusinessActivityLog> logCaptor = ArgumentCaptor.forClass(BusinessActivityLog.class);
        verify(repository).save(logCaptor.capture());
        BusinessActivityLog log = logCaptor.getValue();
        assertThat(log.getUser()).isSameAs(sale.getBuyer());
        assertThat(log.getEntityType()).isEqualTo(BusinessEntityType.MARKETPLACE_SALE);
        assertThat(log.getEntityId()).isEqualTo(sale.getSaleId());
        assertThat(log.getActionType()).isEqualTo(BusinessActionType.MARKETPLACE_SALE_COMPLETED);
        assertThat(log.getNewValue())
                .contains("grossCoinAmount", "originalGrossCoinAmount", "discountCoinAmount", "creatorAmount")
                .doesNotContain("buyer@example.com", "accountNumber", "question");
        assertThat(log.getMetadata())
                .contains("MARKETPLACE", "packId", "packVersionId")
                .doesNotContain("buyer@example.com", "accountNumber", "question");
    }

    @Test
    void recordsASeparateActionForVersionUpgrades() {
        BusinessActivityLogRepository repository = Mockito.mock(BusinessActivityLogRepository.class);
        MarketplaceCheckoutAuditService service = new MarketplaceCheckoutAuditService(repository, new ObjectMapper());

        service.recordCompletedCheckout(sale(true), settlement());

        ArgumentCaptor<BusinessActivityLog> logCaptor = ArgumentCaptor.forClass(BusinessActivityLog.class);
        verify(repository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getActionType())
                .isEqualTo(BusinessActionType.MARKETPLACE_VERSION_UPGRADE_COMPLETED);
    }

    @Test
    void propagatesSerializationFailureSoTheCallingCheckoutTransactionCanRollBack() throws Exception {
        BusinessActivityLogRepository repository = Mockito.mock(BusinessActivityLogRepository.class);
        ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(Mockito.any())).thenThrow(new JsonProcessingException("serialization failed") { });
        MarketplaceCheckoutAuditService service = new MarketplaceCheckoutAuditService(repository, objectMapper);

        assertThatThrownBy(() -> service.recordCompletedCheckout(sale(false), settlement()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to serialize marketplace checkout audit event");

        verify(repository, never()).save(Mockito.any());
    }

    private MarketplaceSale sale(boolean upgrade) {
        User buyer = new User();
        buyer.setUserId("buyer");
        buyer.setEmail("buyer@example.com");

        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());

        MarketplaceSale sale = new MarketplaceSale();
        sale.setSaleId(UUID.randomUUID());
        sale.setBuyer(buyer);
        sale.setPack(pack);
        sale.setPackVersion(version);
        sale.setGrossCoinAmount(90);
        sale.setOriginalGrossCoinAmount(100);
        sale.setDiscountCoinAmount(10);
        sale.setGrossVndAmount(90L);
        if (upgrade) {
            MarketplaceEntitlement sourceEntitlement = new MarketplaceEntitlement();
            sourceEntitlement.setEntitlementId(UUID.randomUUID());
            sale.setSourceEntitlement(sourceEntitlement);
        }
        return sale;
    }

    private MarketplaceSaleSettlement settlement() {
        MarketplaceSaleSettlement settlement = new MarketplaceSaleSettlement();
        settlement.setCreatorAmount(72);
        settlement.setPlatformAmount(18);
        settlement.setCreatorShareBps(8_000);
        settlement.setPlatformShareBps(2_000);
        settlement.setCoinToVndRate(new BigDecimal("1.0000"));
        return settlement;
    }
}
