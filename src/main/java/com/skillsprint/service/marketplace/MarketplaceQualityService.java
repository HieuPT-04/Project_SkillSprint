package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.response.marketplace.MarketplaceQualityJobResponse;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.MarketplaceQualityJob;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceQualityJobStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.MarketplaceQualityJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceQualityService {

    static final int MAX_RETRIES = 2;
    static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    static final List<MarketplaceQualityJobStatus> REUSABLE_STATUSES = List.of(
            MarketplaceQualityJobStatus.QUEUED,
            MarketplaceQualityJobStatus.RUNNING,
            MarketplaceQualityJobStatus.PASSED
    );

    MarketplaceQualityJobRepository qualityJobRepository;
    MarketplacePackVersionRepository versionRepository;
    MarketplaceQualityFingerprint fingerprint;
    MarketplaceQualityValidator validator;
    ObjectMapper objectMapper;

    @Transactional
    public MarketplaceQualityJobResponse queueForCreator(String userId, UUID versionId) {
        MarketplacePackVersion version = versionRepository.findByVersionIdForUpdate(versionId)
                .filter(candidate -> candidate.getPack().getCreator().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
        if (version.getStatus() != MarketplacePackVersionStatus.DRAFT) {
            throw new AppException(ErrorCode.MARKETPLACE_ITEM_NOT_EDITABLE);
        }
        return response(queueLocked(version), version);
    }

    @Transactional
    public MarketplaceQualityJob queue(MarketplacePackVersion version) {
        MarketplacePackVersion lockedVersion = versionRepository.findByVersionIdForUpdate(version.getVersionId())
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
        return queueLocked(lockedVersion);
    }

    private MarketplaceQualityJob queueLocked(MarketplacePackVersion version) {
        String currentFingerprint = fingerprint.of(version);
        Optional<MarketplaceQualityJob> reusable = qualityJobRepository
                .findTopByPackVersionVersionIdAndSnapshotFingerprintOrderByCreatedAtDesc(
                        version.getVersionId(), currentFingerprint)
                .filter(job -> REUSABLE_STATUSES.contains(job.getStatus()));
        if (reusable.isPresent()) {
            applySummary(version, reusable.get());
            return reusable.get();
        }

        List<MarketplaceQualityJob> supersededJobs = qualityJobRepository
                .findByPackVersionVersionIdAndStatusIn(
                        version.getVersionId(),
                        List.of(MarketplaceQualityJobStatus.QUEUED, MarketplaceQualityJobStatus.RUNNING));
        supersededJobs.forEach(job -> {
            job.setStatus(MarketplaceQualityJobStatus.FAILED);
            job.setScore(0);
            job.setReport(staleReport());
            job.setNextRetryAt(null);
            job.setCompletedAt(Instant.now());
        });
        if (!supersededJobs.isEmpty()) {
            qualityJobRepository.saveAll(supersededJobs);
        }

        MarketplaceQualityJob job = new MarketplaceQualityJob();
        job.setPackVersion(version);
        job.setRequestedBy(version.getPack().getCreator());
        job.setStatus(MarketplaceQualityJobStatus.QUEUED);
        job.setSnapshotFingerprint(currentFingerprint);
        job.setMaxRetries(MAX_RETRIES);
        job = qualityJobRepository.save(job);

        version.setQualityStatus(MarketplaceQualityJobStatus.QUEUED);
        version.setQualityScore(null);
        version.setQualitySnapshotFingerprint(currentFingerprint);
        version.setQualityValidatedAt(null);
        return job;
    }

    @Transactional(readOnly = true)
    public MarketplaceQualityJobResponse getLatestForCreator(String userId, UUID versionId) {
        MarketplacePackVersion version = versionRepository.findById(versionId)
                .filter(candidate -> candidate.getPack().getCreator().getUserId().equals(userId))
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
        MarketplaceQualityJob job = qualityJobRepository.findTopByPackVersionVersionIdOrderByCreatedAtDesc(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_QUALITY_JOB_NOT_FOUND));
        return response(job, version);
    }

    @Transactional(readOnly = true)
    public Optional<MarketplaceQualityJobResponse> findLatestForAdmin(MarketplacePackVersion version) {
        return qualityJobRepository
                .findTopByPackVersionVersionIdOrderByCreatedAtDesc(version.getVersionId())
                .map(job -> response(job, version));
    }

    @Transactional(readOnly = true)
    public List<MarketplaceQualityJobResponse> findRecentForAdmin(MarketplacePackVersion version) {
        return qualityJobRepository
                .findTop10ByPackVersionVersionIdOrderByCreatedAtDesc(version.getVersionId())
                .stream()
                .map(job -> response(job, version))
                .toList();
    }

    @Transactional
    public Optional<ClaimedJob> claimNextQueuedJob() {
        return qualityJobRepository.findNextQueuedForUpdate(Instant.now()).map(job -> {
            job.setStatus(MarketplaceQualityJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            job.setNextRetryAt(null);
            MarketplacePackVersion version = job.getPackVersion();
            return new ClaimedJob(job.getJobId(), job.getSnapshotFingerprint(), version);
        });
    }

    public MarketplaceQualityValidator.ValidationResult validate(ClaimedJob claimedJob) {
        return validator.validate(claimedJob.version());
    }

    @Transactional
    public void completeJob(UUID jobId, MarketplaceQualityValidator.ValidationResult result) {
        MarketplaceQualityJob job = qualityJobRepository.findByJobIdForUpdate(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_QUALITY_JOB_NOT_FOUND));
        if (job.getStatus() != MarketplaceQualityJobStatus.RUNNING) {
            return;
        }

        MarketplacePackVersion version = job.getPackVersion();
        if (!job.getSnapshotFingerprint().equals(fingerprint.of(version))) {
            markStale(job);
            return;
        }

        job.setStatus(result.passed()
                ? MarketplaceQualityJobStatus.PASSED
                : MarketplaceQualityJobStatus.FAILED);
        job.setScore(result.score());
        job.setReport(result.report());
        job.setErrorCode(null);
        job.setCompletedAt(Instant.now());
        applySummary(version, job);
    }

    @Transactional
    public void recordJobFailure(UUID jobId, RuntimeException exception) {
        MarketplaceQualityJob job = qualityJobRepository.findByJobIdForUpdate(jobId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_QUALITY_JOB_NOT_FOUND));
        if (job.getStatus() != MarketplaceQualityJobStatus.RUNNING) {
            return;
        }
        retryOrFail(job, job.getPackVersion(), exception);
    }

    @Transactional(readOnly = true)
    public void requireCurrentPass(MarketplacePackVersion version) {
        String currentFingerprint = fingerprint.of(version);
        if (version.getQualityStatus() != MarketplaceQualityJobStatus.PASSED
                || version.getQualityScore() == null
                || version.getQualityScore() < MarketplaceQualityValidator.PASSING_SCORE
                || !currentFingerprint.equals(version.getQualitySnapshotFingerprint())) {
            throw new AppException(ErrorCode.MARKETPLACE_QUALITY_VALIDATION_REQUIRED);
        }
    }

    @Transactional(readOnly = true)
    public Map<UUID, Summary> summariesByLegacyItemIds(Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return versionRepository.findByLegacyItemIdIn(itemIds).stream()
                .collect(Collectors.toMap(MarketplacePackVersion::getLegacyItemId, this::summary));
    }

    public Summary summary(MarketplacePackVersion version) {
        boolean current = version.getQualitySnapshotFingerprint() != null
                && version.getQualitySnapshotFingerprint().equals(fingerprint.of(version));
        return new Summary(version.getQualityStatus(), version.getQualityScore(), current);
    }

    private void markStale(MarketplaceQualityJob job) {
        job.setStatus(MarketplaceQualityJobStatus.FAILED);
        job.setScore(0);
        job.setReport(staleReport());
        job.setCompletedAt(Instant.now());
    }

    private void retryOrFail(
            MarketplaceQualityJob job,
            MarketplacePackVersion version,
            RuntimeException exception
    ) {
        int retryCount = job.getRetryCount() + 1;
        job.setRetryCount(retryCount);
        job.setErrorCode("QUALITY_VALIDATION_ERROR");
        job.setStartedAt(null);
        if (retryCount <= job.getMaxRetries()) {
            job.setStatus(MarketplaceQualityJobStatus.QUEUED);
            job.setNextRetryAt(Instant.now().plus(RETRY_DELAY));
            log.warn("[MARKETPLACE_QUALITY] Retrying job {} ({}/{})",
                    job.getJobId(), retryCount, job.getMaxRetries());
            return;
        }

        job.setStatus(MarketplaceQualityJobStatus.ERROR);
        job.setCompletedAt(Instant.now());
        if (job.getSnapshotFingerprint().equals(fingerprint.of(version))) {
            applySummary(version, job);
        }
        log.error("[MARKETPLACE_QUALITY] Job {} exhausted retries", job.getJobId(), exception);
    }

    private void applySummary(MarketplacePackVersion version, MarketplaceQualityJob job) {
        if (!job.getSnapshotFingerprint().equals(fingerprint.of(version))) {
            return;
        }
        version.setQualityStatus(job.getStatus());
        version.setQualityScore(job.getScore());
        version.setQualitySnapshotFingerprint(job.getSnapshotFingerprint());
        version.setQualityValidatedAt(job.getCompletedAt());
    }

    private ObjectNode staleReport() {
        ObjectNode report = objectMapper.createObjectNode();
        report.put("passingScore", MarketplaceQualityValidator.PASSING_SCORE);
        report.put("blockingIssueCount", 1);
        report.put("chapterCount", 0);
        report.put("questionCount", 0);
        ObjectNode issue = report.putArray("issues").addObject();
        issue.put("code", "SNAPSHOT_STALE");
        issue.put("severity", "BLOCKING");
        issue.put("message", "Snapshot đã thay đổi; cần chạy kiểm định mới.");
        return report;
    }

    private MarketplaceQualityJobResponse response(
            MarketplaceQualityJob job,
            MarketplacePackVersion version
    ) {
        return MarketplaceQualityJobResponse.builder()
                .jobId(job.getJobId())
                .versionId(version.getVersionId())
                .status(job.getStatus())
                .score(job.getScore())
                .currentSnapshot(job.getSnapshotFingerprint().equals(fingerprint.of(version)))
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .report(report(job.getReport()))
                .build();
    }

    private MarketplaceQualityJobResponse.QualityReportResponse report(JsonNode report) {
        if (report == null || report.isNull()) {
            return null;
        }
        List<MarketplaceQualityJobResponse.QualityIssueResponse> issues = report.path("issues").isArray()
                ? java.util.stream.StreamSupport.stream(report.path("issues").spliterator(), false)
                        .map(this::issue)
                        .toList()
                : List.of();
        return MarketplaceQualityJobResponse.QualityReportResponse.builder()
                .passingScore(report.path("passingScore").asInt(MarketplaceQualityValidator.PASSING_SCORE))
                .blockingIssueCount(report.path("blockingIssueCount").asInt(issues.size()))
                .chapterCount(report.path("chapterCount").asInt())
                .questionCount(report.path("questionCount").asInt())
                .issues(issues)
                .build();
    }

    private MarketplaceQualityJobResponse.QualityIssueResponse issue(JsonNode issue) {
        String questionId = issue.path("questionId").asText(null);
        return MarketplaceQualityJobResponse.QualityIssueResponse.builder()
                .code(issue.path("code").asText())
                .severity(issue.path("severity").asText())
                .chapterSequenceNo(issue.has("chapterSequenceNo")
                        ? issue.path("chapterSequenceNo").asInt()
                        : null)
                .questionId(parseUuidOrNull(questionId))
                .message(issue.path("message").asText())
                .build();
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record Summary(MarketplaceQualityJobStatus status, Integer score, boolean current) {
        public static final Summary EMPTY = new Summary(null, null, false);
    }

    public record ClaimedJob(
            UUID jobId,
            String snapshotFingerprint,
            MarketplacePackVersion version
    ) {
    }
}
