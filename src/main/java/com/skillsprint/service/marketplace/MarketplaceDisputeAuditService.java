package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.MarketplaceRefundDispute;
import com.skillsprint.entity.User;
import com.skillsprint.dto.response.marketplace.MarketplaceAuditTimelineEventResponse;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.repository.BusinessActivityLogRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketplaceDisputeAuditService {
    private final BusinessActivityLogRepository activityLogRepository;
    private final ObjectMapper objectMapper;

    public void record(User actor, MarketplaceRefundDispute dispute, BusinessActionType action, String description) {
        BusinessActivityLog log = new BusinessActivityLog();
        log.setUser(actor);
        log.setEntityType(BusinessEntityType.MARKETPLACE_DISPUTE);
        log.setEntityId(dispute.getDisputeId());
        log.setActionType(action);
        log.setTitle("Cập nhật yêu cầu hoàn tiền Marketplace");
        log.setDescription(description);
        log.setMetadata(toJson(Map.of("saleId", dispute.getSale().getSaleId(), "status", dispute.getStatus(), "refundCoinAmount", dispute.getRefundCoinAmount() == null ? 0 : dispute.getRefundCoinAmount())));
        activityLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceAuditTimelineEventResponse> getTimeline(UUID disputeId) {
        return activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(
                        BusinessEntityType.MARKETPLACE_DISPUTE, disputeId)
                .stream()
                .map(log -> MarketplaceAuditTimelineEventResponse.builder()
                        .logId(log.getLogId()).actionType(log.getActionType()).title(log.getTitle())
                        .description(log.getDescription())
                        .actorUserId(log.getUser() == null ? null : log.getUser().getUserId())
                        .actorName(log.getUser() == null ? "SYSTEM" : log.getUser().getFullName())
                        .occurredAt(log.getCreatedAt()).build())
                .toList();
    }

    private String toJson(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Unable to serialize dispute audit event", ex); }
    }
}
