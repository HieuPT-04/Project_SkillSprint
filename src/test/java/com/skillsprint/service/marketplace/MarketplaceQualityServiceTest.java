package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplaceQualityJobResponse;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceQualityJob;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceQualityJobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplaceQualityServiceTest {

    @Mock MarketplaceQualityJobRepository qualityJobRepository;
    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplaceQualityFingerprint fingerprint;
    @Mock MarketplaceQualityValidator validator;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks MarketplaceQualityService service;

    MarketplacePackVersion version;
    MarketplaceQualityJobWorker worker;

    @BeforeEach
    void setUp() {
        User creator = new User();
        creator.setUserId("creator");
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        pack.setCreator(creator);
        version = new MarketplacePackVersion();
        version.setVersionId(UUID.randomUUID());
        version.setVersionNo(1);
        version.setPack(pack);
        version.setStatus(MarketplacePackVersionStatus.DRAFT);
        worker = new MarketplaceQualityJobWorker(service);
    }

    @Test
    void queueIsIdempotentForSameActiveSnapshot() {
        MarketplaceQualityJob existing = job("hash", MarketplaceQualityJobStatus.QUEUED);
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));
        when(fingerprint.of(version)).thenReturn("hash");
        when(qualityJobRepository.findTopByPackVersionVersionIdAndSnapshotFingerprintOrderByCreatedAtDesc(
                version.getVersionId(), "hash")).thenReturn(Optional.of(existing));

        MarketplaceQualityJobResponse response = service.queueForCreator("creator", version.getVersionId());

        assertThat(response.getJobId()).isEqualTo(existing.getJobId());
        assertThat(response.getStatus()).isEqualTo(MarketplaceQualityJobStatus.QUEUED);
        assertThat(version.getQualitySnapshotFingerprint()).isEqualTo("hash");
        assertThat(response.isCurrentSnapshot()).isTrue();
    }

    @Test
    void adminCanQueueQualityForLegacyPendingReviewVersion() {
        version.setStatus(MarketplacePackVersionStatus.PENDING_REVIEW);
        MarketplaceQualityJob existing = job("hash", MarketplaceQualityJobStatus.QUEUED);
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));
        when(fingerprint.of(version)).thenReturn("hash");
        when(qualityJobRepository.findTopByPackVersionVersionIdAndSnapshotFingerprintOrderByCreatedAtDesc(
                version.getVersionId(), "hash")).thenReturn(Optional.of(existing));

        MarketplaceQualityJobResponse response = service.queueForAdmin(version.getVersionId());

        assertThat(response.getJobId()).isEqualTo(existing.getJobId());
        assertThat(response.getStatus()).isEqualTo(MarketplaceQualityJobStatus.QUEUED);
    }

    @Test
    void adminCannotQueueQualityOutsidePendingReview() {
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.queueForAdmin(version.getVersionId()))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
    }

    @Test
    void queueSupersedesAnActiveJobFromAnOlderSnapshot() {
        MarketplaceQualityJob oldJob = job("old-hash", MarketplaceQualityJobStatus.RUNNING);
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));
        when(fingerprint.of(version)).thenReturn("new-hash");
        when(qualityJobRepository.findTopByPackVersionVersionIdAndSnapshotFingerprintOrderByCreatedAtDesc(
                version.getVersionId(), "new-hash")).thenReturn(Optional.empty());
        when(qualityJobRepository.findByPackVersionVersionIdAndStatusIn(
                version.getVersionId(),
                List.of(MarketplaceQualityJobStatus.QUEUED, MarketplaceQualityJobStatus.RUNNING)))
                .thenReturn(List.of(oldJob));
        when(qualityJobRepository.save(any(MarketplaceQualityJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketplaceQualityJob queued = service.queue(version);

        assertThat(oldJob.getStatus()).isEqualTo(MarketplaceQualityJobStatus.FAILED);
        assertThat(oldJob.getCompletedAt()).isNotNull();
        assertThat(oldJob.getReport().path("issues").get(0).path("code").asText())
                .isEqualTo("SNAPSHOT_STALE");
        assertThat(queued.getStatus()).isEqualTo(MarketplaceQualityJobStatus.QUEUED);
        assertThat(queued.getSnapshotFingerprint()).isEqualTo("new-hash");
        verify(qualityJobRepository).saveAll(List.of(oldJob));
    }

    @Test
    void workerPassesValidCurrentSnapshotAndUpdatesVersionSummary() {
        MarketplaceQualityJob queued = job("hash", MarketplaceQualityJobStatus.QUEUED);
        ObjectNode report = objectMapper.createObjectNode();
        report.put("passingScore", 80);
        report.put("blockingIssueCount", 0);
        report.putArray("issues");
        when(qualityJobRepository.findNextQueuedForUpdate(any(Instant.class))).thenReturn(Optional.of(queued));
        when(qualityJobRepository.findByJobIdForUpdate(queued.getJobId())).thenReturn(Optional.of(queued));
        when(fingerprint.of(version)).thenReturn("hash");
        when(validator.validate(version)).thenReturn(
                new MarketplaceQualityValidator.ValidationResult(100, true, report));

        worker.processNextQueuedJob();

        assertThat(queued.getStatus()).isEqualTo(MarketplaceQualityJobStatus.PASSED);
        assertThat(queued.getScore()).isEqualTo(100);
        assertThat(version.getQualityStatus()).isEqualTo(MarketplaceQualityJobStatus.PASSED);
        assertThat(version.getQualityScore()).isEqualTo(100);
        assertThat(version.getQualityValidatedAt()).isNotNull();
    }

    @Test
    void staleJobCannotOverwriteCurrentVersionSummary() {
        MarketplaceQualityJob queued = job("old-hash", MarketplaceQualityJobStatus.QUEUED);
        version.setQualityStatus(MarketplaceQualityJobStatus.QUEUED);
        version.setQualitySnapshotFingerprint("new-hash");
        when(qualityJobRepository.findNextQueuedForUpdate(any(Instant.class))).thenReturn(Optional.of(queued));
        when(qualityJobRepository.findByJobIdForUpdate(queued.getJobId())).thenReturn(Optional.of(queued));
        when(fingerprint.of(version)).thenReturn("new-hash");
        ObjectNode report = objectMapper.createObjectNode();
        when(validator.validate(version)).thenReturn(
                new MarketplaceQualityValidator.ValidationResult(100, true, report));

        worker.processNextQueuedJob();

        assertThat(queued.getStatus()).isEqualTo(MarketplaceQualityJobStatus.FAILED);
        assertThat(queued.getReport().path("issues").get(0).path("code").asText())
                .isEqualTo("SNAPSHOT_STALE");
        assertThat(version.getQualityStatus()).isEqualTo(MarketplaceQualityJobStatus.QUEUED);
        assertThat(version.getQualitySnapshotFingerprint()).isEqualTo("new-hash");
    }

    @Test
    void unexpectedFailureRetriesWithinBound() {
        MarketplaceQualityJob queued = job("hash", MarketplaceQualityJobStatus.QUEUED);
        when(qualityJobRepository.findNextQueuedForUpdate(any(Instant.class))).thenReturn(Optional.of(queued));
        when(qualityJobRepository.findByJobIdForUpdate(queued.getJobId())).thenReturn(Optional.of(queued));
        when(validator.validate(version)).thenThrow(new IllegalStateException("broken draft"));

        worker.processNextQueuedJob();

        assertThat(queued.getStatus()).isEqualTo(MarketplaceQualityJobStatus.QUEUED);
        assertThat(queued.getRetryCount()).isEqualTo(1);
        assertThat(queued.getNextRetryAt()).isNotNull();
        assertThat(queued.getErrorCode()).isEqualTo(MarketplaceQualityService.VALIDATION_ERROR_CODE);
    }

    @Test
    void exhaustedFailureReturnsAUserSafeErrorReport() {
        MarketplaceQualityJob running = job("hash", MarketplaceQualityJobStatus.RUNNING);
        running.setRetryCount(2);
        when(qualityJobRepository.findByJobIdForUpdate(running.getJobId())).thenReturn(Optional.of(running));
        when(fingerprint.of(version)).thenReturn("hash");

        service.recordJobFailure(running.getJobId(), new IllegalStateException("broken draft"));

        assertThat(running.getStatus()).isEqualTo(MarketplaceQualityJobStatus.ERROR);
        assertThat(running.getReport().path("issues").get(0).path("code").asText())
                .isEqualTo(MarketplaceQualityService.VALIDATION_ERROR_CODE);
    }

    @Test
    void currentPassRequiresMatchingFingerprint() {
        version.setQualityStatus(MarketplaceQualityJobStatus.PASSED);
        version.setQualityScore(100);
        version.setQualitySnapshotFingerprint("old-hash");
        when(fingerprint.of(version)).thenReturn("new-hash");

        assertThatThrownBy(() -> service.requireCurrentPass(version))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_QUALITY_VALIDATION_REQUIRED);
    }

    @Test
    void anotherCreatorCannotReadOrQueueVersion() {
        when(versionRepository.findByVersionIdForUpdate(version.getVersionId())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.queueForCreator("intruder", version.getVersionId()))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND);
        verify(versionRepository).findByVersionIdForUpdate(version.getVersionId());
    }

    @Test
    void adminReportReadDoesNotFailForMalformedLegacyQuestionId() {
        MarketplaceQualityJob failed = job("hash", MarketplaceQualityJobStatus.FAILED);
        ObjectNode report = objectMapper.createObjectNode();
        report.put("passingScore", 80);
        report.put("blockingIssueCount", 1);
        ObjectNode issue = report.putArray("issues").addObject();
        issue.put("code", "INVALID_QUESTION_ID");
        issue.put("severity", "BLOCKING");
        issue.put("questionId", "legacy-question-id");
        issue.put("message", "Mã câu hỏi không hợp lệ.");
        failed.setReport(report);
        failed.setErrorCode(MarketplaceQualityService.VALIDATION_ERROR_CODE);
        when(qualityJobRepository.findTopByPackVersionVersionIdOrderByCreatedAtDesc(version.getVersionId()))
                .thenReturn(Optional.of(failed));
        when(fingerprint.of(version)).thenReturn("hash");

        MarketplaceQualityJobResponse response = service.findLatestForAdmin(version).orElseThrow();

        assertThat(response.getReport().getIssues()).singleElement()
                .extracting(MarketplaceQualityJobResponse.QualityIssueResponse::getQuestionId)
                .isNull();
        assertThat(response.getErrorCode()).isEqualTo(MarketplaceQualityService.VALIDATION_ERROR_CODE);
    }

    private MarketplaceQualityJob job(String hash, MarketplaceQualityJobStatus status) {
        MarketplaceQualityJob job = new MarketplaceQualityJob();
        job.setJobId(UUID.randomUUID());
        job.setPackVersion(version);
        job.setRequestedBy(version.getPack().getCreator());
        job.setSnapshotFingerprint(hash);
        job.setStatus(status);
        job.setRetryCount(0);
        job.setMaxRetries(2);
        return job;
    }
}
