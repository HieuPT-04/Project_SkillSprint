package com.skillsprint.service.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceContentReportRequest;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceReportEvidenceUploadUrlRequest;
import com.skillsprint.dto.request.marketplace.UpdateMarketplaceContentReportStatusRequest;
import com.skillsprint.dto.response.common.PageResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceContentReportResponse;
import com.skillsprint.dto.response.marketplace.MarketplaceReportEvidenceUploadUrlResponse;
import com.skillsprint.entity.MarketplaceContentReport;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportCategory;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportTargetType;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.MarketplaceContentReportRepository;
import com.skillsprint.repository.MarketplacePackVersionRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buyer-facing and admin operations for marketplace content reports. Creating a report never
 * mutates the reported content; only an explicit admin transition changes report state.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarketplaceContentReportService {

    static final int MAX_ADMIN_PAGE_SIZE = 50;

    MarketplaceContentReportRepository reportRepository;
    MarketplacePackVersionRepository versionRepository;
    UserRepository userRepository;
    MarketplaceVersionAccessService versionAccessService;
    S3PresignedUrlService s3PresignedUrlService;

    @Transactional
    public MarketplaceContentReportResponse createReport(
            String reporterId,
            CreateMarketplaceContentReportRequest request
    ) {
        MarketplacePackVersion version = requireReadableVersion(reporterId, request.getPackVersionId());
        String targetRef = resolveAndValidateTarget(version, request.getTargetType(), request.getTargetRef());

        if (reportRepository.existsOpenReport(
                reporterId,
                version.getVersionId(),
                request.getTargetType(),
                targetRef,
                request.getCategory())) {
            throw new AppException(ErrorCode.MARKETPLACE_REPORT_DUPLICATED);
        }

        String evidenceObjectKey = null;
        if (request.getEvidenceObjectKey() != null && !request.getEvidenceObjectKey().isBlank()) {
            evidenceObjectKey = s3PresignedUrlService.confirmMarketplaceReportEvidence(
                    reporterId, request.getEvidenceObjectKey());
        }

        MarketplaceContentReport report = new MarketplaceContentReport();
        report.setReporter(requireUser(reporterId));
        report.setPackVersion(version);
        report.setTargetType(request.getTargetType());
        report.setTargetRef(targetRef);
        report.setCategory(request.getCategory());
        report.setDescription(normalize(request.getDescription()));
        report.setEvidenceObjectKey(evidenceObjectKey);
        report.setStatus(MarketplaceReportStatus.OPEN);

        MarketplaceContentReport saved = reportRepository.saveAndFlush(report);
        return toReporterResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MarketplaceContentReportResponse> getMyReports(String reporterId) {
        return reportRepository.findByReporterUserIdOrderByCreatedAtDesc(reporterId).stream()
                .map(this::toReporterResponse)
                .toList();
    }

    public MarketplaceReportEvidenceUploadUrlResponse createEvidenceUploadUrl(
            String reporterId,
            CreateMarketplaceReportEvidenceUploadUrlRequest request
    ) {
        return s3PresignedUrlService.createMarketplaceReportEvidenceUploadUrl(reporterId, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<MarketplaceContentReportResponse> getAdminReports(
            MarketplaceReportStatus status,
            MarketplaceReportTargetType targetType,
            MarketplaceReportCategory category,
            int page,
            int size
    ) {
        Pageable pageable = adminPageable(page, size);
        Page<MarketplaceContentReportResponse> reports = reportRepository
                .searchAdmin(status, targetType, category, pageable)
                .map(this::toAdminResponse);
        return PageResponse.from(reports);
    }

    @Transactional(readOnly = true)
    public MarketplaceContentReportResponse getAdminReport(UUID reportId) {
        return toAdminResponse(requireReport(reportId));
    }

    @Transactional
    public MarketplaceContentReportResponse updateStatus(
            String adminUserId,
            UUID reportId,
            UpdateMarketplaceContentReportStatusRequest request
    ) {
        MarketplaceContentReport report = requireReport(reportId);
        MarketplaceReportStatus next = request.getStatus();
        if (!report.getStatus().canTransitionTo(next)) {
            throw new AppException(ErrorCode.MARKETPLACE_REPORT_STATUS_INVALID);
        }

        report.setStatus(next);
        report.setResolutionNote(normalize(request.getResolutionNote()));
        if (next.isTerminal()) {
            report.setReviewedBy(requireUser(adminUserId));
            report.setReviewedAt(Instant.now());
        } else {
            report.setReviewedBy(null);
            report.setReviewedAt(null);
        }

        MarketplaceContentReport saved = reportRepository.saveAndFlush(report);
        return toAdminResponse(saved);
    }

    private MarketplacePackVersion requireReadableVersion(String viewerId, UUID versionId) {
        MarketplacePackVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND));
        if (version.getStatus() == MarketplacePackVersionStatus.PUBLISHED
                || versionAccessService.hasAccess(viewerId, version)) {
            return version;
        }
        throw new AppException(ErrorCode.MARKETPLACE_PACK_VERSION_NOT_FOUND);
    }

    /** Confirms the target exists in the snapshot and normalizes the stored reference. */
    private String resolveAndValidateTarget(
            MarketplacePackVersion version,
            MarketplaceReportTargetType targetType,
            String requestedRef
    ) {
        String ref = requestedRef == null ? null : requestedRef.trim();
        return switch (targetType) {
            case VERSION, CREATOR -> null;
            case CHAPTER -> {
                if (ref == null || ref.isBlank() || !chapterExists(version, ref)) {
                    throw new AppException(ErrorCode.MARKETPLACE_REPORT_TARGET_INVALID);
                }
                yield ref;
            }
            case QUESTION -> {
                if (ref == null || ref.isBlank() || !questionExists(version, ref)) {
                    throw new AppException(ErrorCode.MARKETPLACE_REPORT_TARGET_INVALID);
                }
                yield ref;
            }
        };
    }

    private boolean chapterExists(MarketplacePackVersion version, String chapterId) {
        for (JsonNode chapter : chapters(version)) {
            if (chapterId.equals(chapter.path("chapterId").asText(null))) {
                return true;
            }
        }
        return false;
    }

    private boolean questionExists(MarketplacePackVersion version, String questionId) {
        for (JsonNode chapter : chapters(version)) {
            for (JsonNode question : chapter.path("quiz").path("questions")) {
                if (questionId.equals(question.path("questionId").asText(null))) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonNode chapters(MarketplacePackVersion version) {
        JsonNode content = version.getContent();
        if (content == null) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        return content.path("chapters");
    }

    private MarketplaceContentReport requireReport(UUID reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.MARKETPLACE_REPORT_NOT_FOUND));
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
    }

    private Pageable adminPageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_ADMIN_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /** Reporter-facing view: no reporter identity leak, no evidence URL. */
    private MarketplaceContentReportResponse toReporterResponse(MarketplaceContentReport report) {
        return baseResponse(report).build();
    }

    /** Admin view: adds reporter identity and a short-lived evidence view URL. */
    private MarketplaceContentReportResponse toAdminResponse(MarketplaceContentReport report) {
        User reporter = report.getReporter();
        return baseResponse(report)
                .reporterId(reporter == null ? null : reporter.getUserId())
                .reporterName(reporter == null ? null : reporter.getFullName())
                .reviewedByName(report.getReviewedBy() == null ? null : report.getReviewedBy().getFullName())
                .evidenceUrl(s3PresignedUrlService.createViewUrl(report.getEvidenceObjectKey()))
                .build();
    }

    private MarketplaceContentReportResponse.MarketplaceContentReportResponseBuilder baseResponse(
            MarketplaceContentReport report
    ) {
        MarketplacePackVersion version = report.getPackVersion();
        return MarketplaceContentReportResponse.builder()
                .reportId(report.getReportId())
                .packVersionId(version.getVersionId())
                .packId(version.getPack().getPackId())
                .versionNo(version.getVersionNo())
                .versionTitle(version.getTitle())
                .targetType(report.getTargetType())
                .targetRef(report.getTargetRef())
                .category(report.getCategory())
                .description(report.getDescription())
                .status(report.getStatus())
                .resolutionNote(report.getResolutionNote())
                .hasEvidence(report.getEvidenceObjectKey() != null)
                .reviewedAt(report.getReviewedAt())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
