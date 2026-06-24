package com.skillsprint.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skillsprint.entity.CalendarTask;
import com.skillsprint.entity.CommunityPost;
import com.skillsprint.entity.PaymentTransaction;
import com.skillsprint.entity.PointEvent;
import com.skillsprint.entity.ServicePlan;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.User;
import com.skillsprint.entity.UserPointSummary;
import com.skillsprint.enums.calendar.CalendarTaskCategory;
import com.skillsprint.enums.calendar.CalendarTaskPriority;
import com.skillsprint.enums.calendar.CalendarTaskStatus;
import com.skillsprint.enums.community.CommunityPostStatus;
import com.skillsprint.enums.payment.PaymentProvider;
import com.skillsprint.enums.payment.PaymentStatus;
import com.skillsprint.enums.plan.ServicePlanType;
import com.skillsprint.enums.points.PointEventType;
import com.skillsprint.enums.points.PointSourceType;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryQueryBehaviorTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    StudyWorkspaceRepository workspaceRepository;

    @Autowired
    CalendarTaskRepository calendarTaskRepository;

    @Autowired
    PointEventRepository pointEventRepository;

    @Autowired
    UserPointSummaryRepository userPointSummaryRepository;

    @Autowired
    CommunityPostRepository communityPostRepository;

    @Autowired
    ServicePlanRepository servicePlanRepository;

    @Autowired
    PaymentTransactionRepository paymentTransactionRepository;

    User alice;
    User bob;
    StudyWorkspace aliceWorkspace;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(user("repo-alice", "alice@example.com", "Alice"));
        bob = userRepository.save(user("repo-bob", "bob@example.com", "Bob"));
        aliceWorkspace = workspaceRepository.save(workspace(alice, "Java"));
    }

    @Test
    void calendarRepositoryFindsOnlyUnnotifiedOverdueTodoTasks() {
        CalendarTask yesterdayTodo = calendarTask("Yesterday", LocalDate.parse("2026-06-23"), null, null);
        CalendarTask todayPastEnd = calendarTask(
                "Past end",
                LocalDate.parse("2026-06-24"),
                LocalTime.parse("08:00"),
                LocalTime.parse("09:00")
        );
        CalendarTask completed = calendarTask("Completed", LocalDate.parse("2026-06-23"), null, null);
        completed.setStatus(CalendarTaskStatus.COMPLETED);
        CalendarTask alreadyNotified = calendarTask("Notified", LocalDate.parse("2026-06-23"), null, null);
        alreadyNotified.setOverdueNotified(true);
        CalendarTask future = calendarTask("Future", LocalDate.parse("2026-06-25"), null, null);
        calendarTaskRepository.saveAllAndFlush(List.of(
                yesterdayTodo,
                todayPastEnd,
                completed,
                alreadyNotified,
                future
        ));

        List<CalendarTask> result = calendarTaskRepository.findOverdueUnnotifiedTasks(
                LocalDate.parse("2026-06-24"),
                LocalTime.parse("10:00")
        );

        assertEquals(2, result.size());
        assertTrue(result.stream().map(CalendarTask::getTitle).toList().containsAll(List.of("Yesterday", "Past end")));
    }

    @Test
    void pointRepositorySumsRanksAndOrdersWeeklyLeaderboard() {
        LocalDate weekStart = LocalDate.parse("2026-06-22");
        userPointSummaryRepository.save(summary(alice, 500, 4));
        userPointSummaryRepository.save(summary(bob, 300, 2));
        pointEventRepository.saveAllAndFlush(List.of(
                pointEvent(alice, 120, weekStart),
                pointEvent(alice, 80, weekStart),
                pointEvent(bob, 150, weekStart)
        ));

        assertEquals(200, pointEventRepository.sumWeeklyPoints(alice.getUserId(), weekStart));
        assertEquals(1, pointEventRepository.calculateWeeklyRank(weekStart, 200));
        assertEquals(2, pointEventRepository.calculateWeeklyRank(weekStart, 150));

        List<PointEventRepository.LeaderboardRow> leaderboard =
                pointEventRepository.findWeeklyLeaderboard(weekStart, PageRequest.of(0, 10));

        assertEquals(List.of(alice.getUserId(), bob.getUserId()),
                leaderboard.stream().map(PointEventRepository.LeaderboardRow::getUserId).toList());
        assertEquals(200, leaderboard.get(0).getPoints());
        assertEquals(4, leaderboard.get(0).getStreakDays());
    }

    @Test
    void communityPostRepositoryFiltersSearchHashtagAndMyPosts() {
        CommunityPost approved = post(alice, "Learning Java and Spring", "#java\n#spring", CommunityPostStatus.APPROVED);
        CommunityPost hidden = post(alice, "Hidden Java", "#java", CommunityPostStatus.HIDDEN);
        CommunityPost deleted = post(alice, "Deleted Java", "#java", CommunityPostStatus.DELETED);
        CommunityPost bobPost = post(bob, "Spring for Bob", "#spring", CommunityPostStatus.APPROVED);
        communityPostRepository.saveAllAndFlush(List.of(approved, hidden, deleted, bobPost));

        var search = communityPostRepository.searchByStatus(
                CommunityPostStatus.APPROVED,
                "java",
                "#spring",
                PageRequest.of(0, 10)
        );

        assertEquals(1, search.getTotalElements());
        assertEquals(approved.getContent(), search.getContent().get(0).getContent());

        var myPosts = communityPostRepository.findMyPosts(
                alice.getUserId(),
                null,
                PageRequest.of(0, 10)
        );

        assertEquals(2, myPosts.getTotalElements());
        assertTrue(myPosts.getContent().stream().noneMatch(post -> post.getStatus() == CommunityPostStatus.DELETED));
    }

    @Test
    void paymentRepositoryFiltersAdminSearchAndRevenueSums() {
        ServicePlan plan = servicePlanRepository.save(plan());
        PaymentTransaction alicePaid = payment(alice, plan, PaymentStatus.PAID, "TXN-1", "PROVIDER-1", "200000");
        alicePaid.setPaidAt(Instant.parse("2026-06-24T08:00:00Z"));
        PaymentTransaction bobPending = payment(bob, plan, PaymentStatus.PENDING, "TXN-2", "PROVIDER-2", "100000");
        paymentTransactionRepository.saveAllAndFlush(List.of(alicePaid, bobPending));

        var paidSearch = paymentTransactionRepository.searchAdminPayments(
                PaymentStatus.PAID.name(),
                "alice",
                PageRequest.of(0, 10)
        );

        assertEquals(1, paidSearch.getTotalElements());
        assertEquals(alice.getUserId(), paidSearch.getContent().get(0).getUser().getUserId());
        assertEquals(new BigDecimal("200000.00"), paymentTransactionRepository.sumAmountByStatus(PaymentStatus.PAID));
        assertEquals(new BigDecimal("200000.00"), paymentTransactionRepository.sumAmountByStatusAndPaidAtBetween(
                PaymentStatus.PAID,
                Instant.parse("2026-06-24T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:00Z")
        ));
    }

    private CalendarTask calendarTask(String title, LocalDate date, LocalTime start, LocalTime end) {
        CalendarTask task = new CalendarTask();
        task.setWorkspace(aliceWorkspace);
        task.setUser(alice);
        task.setTitle(title);
        task.setTaskDate(date);
        task.setStartTime(start);
        task.setEndTime(end);
        task.setDurationMinutes(60);
        task.setCategory(CalendarTaskCategory.PERSONAL);
        task.setPriority(CalendarTaskPriority.MEDIUM);
        task.setStatus(CalendarTaskStatus.TODO);
        return task;
    }

    private PointEvent pointEvent(User user, int points, LocalDate weekStart) {
        PointEvent event = new PointEvent();
        event.setUser(user);
        event.setWorkspace(aliceWorkspace);
        event.setEventType(PointEventType.QUIZ_PASSED);
        event.setSourceType(PointSourceType.QUIZ);
        event.setSourceId(user.getUserId() + "-" + points);
        event.setPoints(points);
        event.setDescription("Quiz");
        event.setEventDate(weekStart);
        event.setWeekStartDate(weekStart);
        event.setMonthStartDate(weekStart.withDayOfMonth(1));
        return event;
    }

    private UserPointSummary summary(User user, int totalPoints, int streakDays) {
        UserPointSummary summary = new UserPointSummary();
        summary.setUser(user);
        summary.setTotalPoints(totalPoints);
        summary.setCurrentWeekPoints(totalPoints);
        summary.setCurrentWeekStartDate(LocalDate.parse("2026-06-22"));
        summary.setCurrentMonthPoints(totalPoints);
        summary.setCurrentMonthStartDate(LocalDate.parse("2026-06-01"));
        summary.setStreakDays(streakDays);
        summary.setLastPointDate(LocalDate.parse("2026-06-24"));
        return summary;
    }

    private CommunityPost post(User author, String content, String hashtags, CommunityPostStatus status) {
        CommunityPost post = new CommunityPost();
        post.setAuthor(author);
        post.setContent(content);
        post.setHashtags(hashtags);
        post.setStatus(status);
        return post;
    }

    private PaymentTransaction payment(
            User user,
            ServicePlan plan,
            PaymentStatus status,
            String txnRef,
            String providerTransactionId,
            String amount
    ) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setUser(user);
        payment.setPlan(plan);
        payment.setProvider(PaymentProvider.SEPAY);
        payment.setStatus(status);
        payment.setTxnRef(txnRef);
        payment.setProviderTransactionId(providerTransactionId);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrency("VND");
        payment.setSubscriptionMonths(1);
        payment.setExpireAt(Instant.parse("2026-06-25T00:00:00Z"));
        return payment;
    }

    private ServicePlan plan() {
        ServicePlan plan = new ServicePlan();
        plan.setPlanName("Premium");
        plan.setPlanType(ServicePlanType.PREMIUM);
        plan.setMonthlyPrice(new BigDecimal("200000"));
        plan.setCurrency("VND");
        plan.setActive(true);
        return plan;
    }

    private StudyWorkspace workspace(User user, String name) {
        StudyWorkspace workspace = new StudyWorkspace();
        workspace.setUser(user);
        workspace.setName(name);
        workspace.setStatus(WorkspaceStatus.ACTIVE);
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
