package com.skillsprint.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.entity.MarketplaceContentReport;
import com.skillsprint.entity.MarketplacePack;
import com.skillsprint.entity.MarketplacePackVersion;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.enums.marketplace.MarketplacePackUpdateType;
import com.skillsprint.enums.marketplace.MarketplacePackVersionStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportCategory;
import com.skillsprint.enums.marketplace.MarketplaceReportStatus;
import com.skillsprint.enums.marketplace.MarketplaceReportTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the report repository queries. The test profile uses Hibernate-created H2 tables, so
 * the PostgreSQL partial unique index from V20 is reviewed in migration SQL while the duplicate
 * guard query {@code existsOpenReport} and cross-user isolation are executed here.
 */
@DataJpaTest
@ActiveProfiles("test")
class MarketplaceContentReportRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired StudyWorkspaceRepository workspaceRepository;
    @Autowired MarketplacePackRepository packRepository;
    @Autowired MarketplacePackVersionRepository versionRepository;
    @Autowired MarketplaceContentReportRepository reportRepository;

    User creator;
    User buyer;
    User otherBuyer;
    MarketplacePackVersion version;

    @BeforeEach
    void setUp() {
        creator = userRepository.save(user("report-creator", "creator-report@example.com", "Creator"));
        buyer = userRepository.save(user("report-buyer", "buyer-report@example.com", "Buyer"));
        otherBuyer = userRepository.save(user("report-other", "other-report@example.com", "Other"));
        StudyWorkspace workspace = workspaceRepository.save(workspace(creator));
        MarketplacePack pack = packRepository.save(pack(creator, workspace));
        version = versionRepository.save(version(pack));
    }

    @Test
    void existsActiveReportDistinguishesReporterAndStatus() {
        reportRepository.saveAndFlush(report(buyer, MarketplaceReportStatus.OPEN));

        assertThat(activeReport(buyer)).isTrue();
        // A different user reporting the same target is allowed.
        assertThat(activeReport(otherBuyer)).isFalse();
    }

    @Test
    void inReviewReportStillBlocksADuplicate() {
        reportRepository.saveAndFlush(report(buyer, MarketplaceReportStatus.IN_REVIEW));

        assertThat(activeReport(buyer)).isTrue();
    }

    @Test
    void resolvedOrDismissedReportNoLongerBlocksNewReport() {
        reportRepository.saveAndFlush(report(buyer, MarketplaceReportStatus.RESOLVED));
        assertThat(activeReport(buyer)).isFalse();

        reportRepository.saveAndFlush(report(otherBuyer, MarketplaceReportStatus.DISMISSED));
        assertThat(activeReport(otherBuyer)).isFalse();
    }

    private boolean activeReport(User reporter) {
        return reportRepository.existsActiveReport(
                reporter.getUserId(), version.getVersionId(),
                MarketplaceReportTargetType.VERSION, null, MarketplaceReportCategory.MISLEADING);
    }

    @Test
    void ownReportsListIsScopedToTheReporter() {
        reportRepository.saveAndFlush(report(buyer, MarketplaceReportStatus.OPEN));
        reportRepository.saveAndFlush(report(otherBuyer, MarketplaceReportStatus.OPEN));

        assertThat(reportRepository.findByReporterUserIdOrderByCreatedAtDesc(buyer.getUserId()))
                .singleElement()
                .satisfies(report -> assertThat(report.getReporter().getUserId()).isEqualTo(buyer.getUserId()));
    }

    @Test
    void adminSearchFiltersByStatus() {
        reportRepository.saveAndFlush(report(buyer, MarketplaceReportStatus.OPEN));
        reportRepository.saveAndFlush(report(otherBuyer, MarketplaceReportStatus.RESOLVED));

        assertThat(reportRepository.searchAdmin(
                MarketplaceReportStatus.OPEN, null, null, PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(1);
        assertThat(reportRepository.searchAdmin(
                null, null, null, PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(2);
    }

    private MarketplaceContentReport report(User reporter, MarketplaceReportStatus status) {
        MarketplaceContentReport report = new MarketplaceContentReport();
        report.setReporter(reporter);
        report.setPackVersion(version);
        report.setTargetType(MarketplaceReportTargetType.VERSION);
        report.setCategory(MarketplaceReportCategory.MISLEADING);
        report.setStatus(status);
        return report;
    }

    private MarketplacePack pack(User creator, StudyWorkspace workspace) {
        MarketplacePack pack = new MarketplacePack();
        pack.setCreator(creator);
        pack.setSourceWorkspace(workspace);
        return pack;
    }

    private MarketplacePackVersion version(MarketplacePack pack) {
        MarketplacePackVersion version = new MarketplacePackVersion();
        version.setPack(pack);
        version.setVersionNo(1);
        version.setUpdateType(MarketplacePackUpdateType.MAJOR);
        version.setStatus(MarketplacePackVersionStatus.PUBLISHED);
        version.setTitle("Report Pack");
        version.setSubject("Software");
        version.setPriceCoins(100);
        version.setChapterCount(1);
        version.setQuizCount(1);
        version.setQuestionCount(5);
        version.setContent(new ObjectMapper().createObjectNode());
        version.setSaleable(true);
        return version;
    }

    private StudyWorkspace workspace(User user) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setUser(user);
        workspace.setName("Report workspace");
        return workspace;
    }

    private User user(String userId, String email, String fullName) {
        User user = new User();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFullName(fullName);
        return user;
    }
}
