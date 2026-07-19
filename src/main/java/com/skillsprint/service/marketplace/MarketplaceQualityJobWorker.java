package com.skillsprint.service.marketplace;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceQualityJobWorker {

    MarketplaceQualityService qualityService;

    @Scheduled(fixedDelayString = "${app.marketplace.quality.fixed-delay-ms:3000}")
    public void processNextQueuedJob() {
        qualityService.claimNextQueuedJob().ifPresent(claimedJob -> {
            try {
                var result = qualityService.validate(claimedJob);
                qualityService.completeJob(claimedJob.jobId(), result);
            } catch (RuntimeException exception) {
                qualityService.recordJobFailure(claimedJob.jobId(), exception);
            }
        });
    }
}
