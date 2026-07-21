package com.skillsprint.service.marketplace;

import com.skillsprint.dto.response.marketplace.MarketplaceAuditTimelineEventResponse;
import com.skillsprint.entity.BusinessActivityLog;
import com.skillsprint.enums.log.BusinessEntityType;
import com.skillsprint.repository.BusinessActivityLogRepository;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplacePayoutAuditQueryService {
    BusinessActivityLogRepository activityLogRepository;

    @Transactional(readOnly = true)
    public List<MarketplaceAuditTimelineEventResponse> getTimeline(UUID payoutId) {
        return activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(
                        BusinessEntityType.CREATOR_PAYOUT, payoutId)
                .stream().map(this::toResponse).toList();
    }

    private MarketplaceAuditTimelineEventResponse toResponse(BusinessActivityLog log) {
        return MarketplaceAuditTimelineEventResponse.builder()
                .logId(log.getLogId()).actionType(log.getActionType()).title(log.getTitle())
                .description(log.getDescription())
                .actorUserId(log.getUser() == null ? null : log.getUser().getUserId())
                .actorName(log.getUser() == null ? "SYSTEM" : log.getUser().getFullName())
                .occurredAt(log.getCreatedAt()).build();
    }
}
