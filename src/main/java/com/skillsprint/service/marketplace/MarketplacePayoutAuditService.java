package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.entity.CreatorPayout;
import com.skillsprint.entity.User;
import com.skillsprint.enums.log.BusinessActionType;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.repository.BusinessActivityLogRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePayoutAuditService {

    BusinessActivityLogRepository activityLogRepository;
    ObjectMapper objectMapper;

    public void record(User actor, CreatorPayout payout, BusinessActionType actionType, String description) {
        BusinessActivityLog log = new BusinessActivityLog();
        log.setUser(actor);
        log.setEntityType(BusinessEntityType.CREATOR_PAYOUT);
        log.setEntityId(payout.getPayoutId());
        log.setActionType(actionType);
        log.setTitle("Cập nhật yêu cầu rút tiền Creator");
        log.setDescription(description);
        log.setNewValue(toJson(snapshot(payout)));
        log.setMetadata(toJson(metadata(payout)));
        activityLogRepository.save(log);
    }

    private Map<String, Object> snapshot(CreatorPayout payout) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("payoutId", payout.getPayoutId());
        snapshot.put("creatorId", payout.getCreator().getUserId());
        snapshot.put("requestedAmount", payout.getRequestedAmount());
        snapshot.put("status", payout.getStatus());
        snapshot.put("adminActorId", payout.getAdminActor() == null ? null : payout.getAdminActor().getUserId());
        snapshot.put("externalTransferReference", payout.getExternalTransferReference());
        return snapshot;
    }

    private Map<String, Object> metadata(CreatorPayout payout) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("module", "MARKETPLACE");
        metadata.put("payoutId", payout.getPayoutId());
        metadata.put("creatorId", payout.getCreator().getUserId());
        return metadata;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize marketplace payout audit event", ex);
        }
    }
}
