package com.skillsprint.service.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skillsprint.dto.request.marketplace.CreateMarketplaceContentReportRequest;
import com.skillsprint.dto.request.marketplace.UpdateMarketplaceContentReportStatusRequest;
import com.skillsprint.dto.response.marketplace.MarketplaceContentReportResponse;
import com.skillsprint.entity.MarketplaceContentReport;
import com.skillsprint.entity.MarketplacePack;
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
import com.skillsprint.repository.MarketplaceReviewRepository;
import com.skillsprint.repository.UserRepository;
import com.skillsprint.service.storage.S3PresignedUrlService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class MarketplaceContentReportServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock MarketplaceContentReportRepository reportRepository;
    @Mock MarketplacePackVersionRepository versionRepository;
    @Mock MarketplaceReviewRepository reviewRepository;
    @Mock UserRepository userRepository;
    @Mock MarketplaceVersionAccessService versionAccessService;
    @Mock S3PresignedUrlService s3PresignedUrlService;

    MarketplaceContentReportService service;
    MarketplacePackVersion version;
    User reporter;

    @BeforeEach
    void setUp() {
        service = new MarketplaceContentReportService(
                reportRepository, versionRepository, reviewRepository, userRepository,
                versionAccessService, s3PresignedUrlService);
        version = publishedVersion();
        reporter = user("buyer", "Buyer");
    }

    @Test
    void ownerReportsQuestionWithoutSupplyingUserId() {
        CreateMarketplaceContentReportRequest request = request(
                MarketplaceReportTargetType.QUESTION, "11111111-1111-1111-1111-111111111111");
        allowOwner();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(reportRepository.existsActiveReport(any(), any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById("buyer")).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(MarketplaceContentReport.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        MarketplaceContentReportResponse response = service.createReport("buyer", request);

        assertThat(response.getReportId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(MarketplaceReportStatus.OPEN);
        assertThat(response.getTargetType()).isEqualTo(MarketplaceReportTargetType.QUESTION);
        // Reporter-facing response never leaks the reporter identity.
        assertThat(response.getReporterId()).isNull();
        assertThat(response.getReporterName()).isNull();
    }

    @Test
    void reportingUnknownQuestionIsRejected() {
        CreateMarketplaceContentReportRequest request = request(
                MarketplaceReportTargetType.QUESTION, "99999999-9999-9999-9999-999999999999");
        allowOwner();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.createReport("buyer", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REPORT_TARGET_INVALID));
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateActiveReportIsRejected() {
        // The active guard covers both OPEN and IN_REVIEW; the repository boolean stands in for either.
        CreateMarketplaceContentReportRequest request = request(MarketplaceReportTargetType.VERSION, null);
        allowOwner();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(reportRepository.existsActiveReport(
                "buyer", version.getVersionId(), MarketplaceReportTargetType.VERSION, null,
                MarketplaceReportCategory.MISLEADING)).thenReturn(true);

        assertThatThrownBy(() -> service.createReport("buyer", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REPORT_DUPLICATED));
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateRaceReturnsTypedDuplicateError() {
        // Guard passed, but the DB partial unique index closed a race on insert.
        CreateMarketplaceContentReportRequest request = request(MarketplaceReportTargetType.VERSION, null);
        allowOwner();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(reportRepository.existsActiveReport(any(), any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById("buyer")).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(MarketplaceContentReport.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.createReport("buyer", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REPORT_DUPLICATED));
    }

    @Test
    void buyerReportsAReviewBelongingToTheVersion() {
        String reviewId = "33333333-3333-3333-3333-333333333333";
        CreateMarketplaceContentReportRequest request = request(MarketplaceReportTargetType.REVIEW, reviewId);
        allowOwner();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(reviewRepository.existsByReviewIdAndPackVersionVersionId(
                UUID.fromString(reviewId), version.getVersionId())).thenReturn(true);
        when(reportRepository.existsActiveReport(any(), any(), any(), any(), any())).thenReturn(false);
        when(userRepository.findById("buyer")).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(MarketplaceContentReport.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        MarketplaceContentReportResponse response = service.createReport("buyer", request);

        assertThat(response.getTargetType()).isEqualTo(MarketplaceReportTargetType.REVIEW);
        assertThat(response.getTargetRef()).isEqualTo(reviewId);
    }

    @Test
    void reportingAReviewFromAnotherVersionIsRejected() {
        String reviewId = "44444444-4444-4444-4444-444444444444";
        CreateMarketplaceContentReportRequest request = request(MarketplaceReportTargetType.REVIEW, reviewId);
        allowOwner();
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(reviewRepository.existsByReviewIdAndPackVersionVersionId(
                UUID.fromString(reviewId), version.getVersionId())).thenReturn(false);

        assertThatThrownBy(() -> service.createReport("buyer", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REPORT_TARGET_INVALID));
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void reportOnPublishedVersionWithoutOwnershipIsForbidden() {
        CreateMarketplaceContentReportRequest request = request(MarketplaceReportTargetType.VERSION, null);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));
        when(versionAccessService.hasAccess("buyer", version)).thenReturn(false);

        assertThatThrownBy(() -> service.createReport("buyer", request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.FORBIDDEN));
        verify(reportRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminResolvesOpenReportAndRecordsAuditTrail() {
        MarketplaceContentReport report = openReport();
        UUID reportId = report.getReportId();
        UpdateMarketplaceContentReportStatusRequest request = new UpdateMarketplaceContentReportStatusRequest();
        request.setStatus(MarketplaceReportStatus.RESOLVED);
        request.setResolutionNote("Fixed");
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(userRepository.findById("admin")).thenReturn(Optional.of(user("admin", "Admin")));
        when(reportRepository.saveAndFlush(report)).thenReturn(report);

        MarketplaceContentReportResponse response = service.updateStatus("admin", reportId, request);

        assertThat(response.getStatus()).isEqualTo(MarketplaceReportStatus.RESOLVED);
        assertThat(report.getReviewedBy().getUserId()).isEqualTo("admin");
        assertThat(report.getReviewedAt()).isNotNull();
        assertThat(report.getResolutionNote()).isEqualTo("Fixed");
    }

    @Test
    void terminalReportCannotTransitionAgain() {
        MarketplaceContentReport report = openReport();
        report.setStatus(MarketplaceReportStatus.DISMISSED);
        UpdateMarketplaceContentReportStatusRequest request = new UpdateMarketplaceContentReportStatusRequest();
        request.setStatus(MarketplaceReportStatus.RESOLVED);
        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.updateStatus("admin", report.getReportId(), request))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.MARKETPLACE_REPORT_STATUS_INVALID));
    }

    @Test
    void adminResponseExposesReporterIdentity() {
        MarketplaceContentReport report = openReport();
        when(reportRepository.findById(report.getReportId())).thenReturn(Optional.of(report));

        MarketplaceContentReportResponse response = service.getAdminReport(report.getReportId());

        assertThat(response.getReporterId()).isEqualTo("buyer");
        assertThat(response.getReporterName()).isEqualTo("Buyer");
    }

    private CreateMarketplaceContentReportRequest request(MarketplaceReportTargetType type, String ref) {
        CreateMarketplaceContentReportRequest request = new CreateMarketplaceContentReportRequest();
        request.setPackVersionId(version.getVersionId());
        request.setTargetType(type);
        request.setTargetRef(ref);
        request.setCategory(MarketplaceReportCategory.MISLEADING);
        request.setDescription("  problem  ");
        return request;
    }

    private void allowOwner() {
        when(versionAccessService.hasAccess("buyer", version)).thenReturn(true);
    }

    private MarketplaceContentReport openReport() {
        MarketplaceContentReport report = new MarketplaceContentReport();
        report.setReportId(UUID.randomUUID());
        report.setReporter(reporter);
        report.setPackVersion(version);
        report.setTargetType(MarketplaceReportTargetType.VERSION);
        report.setCategory(MarketplaceReportCategory.MISLEADING);
        report.setStatus(MarketplaceReportStatus.OPEN);
        report.setCreatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        report.setUpdatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        return report;
    }

    private MarketplaceContentReport persisted(MarketplaceContentReport report) {
        report.setReportId(UUID.randomUUID());
        report.setCreatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        report.setUpdatedAt(Instant.parse("2026-07-19T00:00:00Z"));
        return report;
    }

    private MarketplacePackVersion publishedVersion() {
        MarketplacePack pack = new MarketplacePack();
        pack.setPackId(UUID.randomUUID());
        MarketplacePackVersion packVersion = new MarketplacePackVersion();
        packVersion.setVersionId(UUID.randomUUID());
        packVersion.setPack(pack);
        packVersion.setVersionNo(1);
        packVersion.setTitle("Pack");
        packVersion.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        packVersion.setContent(sampleContent());
        return packVersion;
    }

    private ObjectNode sampleContent() {
        ObjectNode content = MAPPER.createObjectNode();
        ObjectNode chapter = MAPPER.createObjectNode();
        chapter.put("chapterId", "22222222-2222-2222-2222-222222222222");
        chapter.put("title", "Chapter 1");
        ObjectNode quiz = MAPPER.createObjectNode();
        ObjectNode question = MAPPER.createObjectNode();
        question.put("questionId", "11111111-1111-1111-1111-111111111111");
        quiz.putArray("questions").add(question);
        chapter.set("quiz", quiz);
        content.putArray("chapters").add(chapter);
        return content;
    }

    private User user(String id, String name) {
        User user = new User();
        user.setUserId(id);
        user.setFullName(name);
        return user;
    }
}
