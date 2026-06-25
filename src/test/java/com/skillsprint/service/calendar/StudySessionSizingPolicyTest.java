package com.skillsprint.service.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StudySessionSizingPolicyTest {

    @Test
    void distributesEightHoursOverSixSessionsAsCleanBlocks() {
        List<Integer> durations = StudySessionSizingPolicy.planWeeklyDurations(480, 6, 120);

        assertEquals(List.of(90, 90, 75, 75, 75, 75), durations);
        assertEquals(480, durations.stream().mapToInt(Integer::intValue).sum());
        assertNoOddDurations(durations);
    }

    @Test
    void distributesEightHoursOverFiveSessionsWithoutOddDurations() {
        List<Integer> durations = StudySessionSizingPolicy.planWeeklyDurations(480, 5, 120);

        assertEquals(480, durations.stream().mapToInt(Integer::intValue).sum());
        assertNoOddDurations(durations);
        assertFalse(durations.contains(96), "must not produce 96-minute sessions");
    }

    @Test
    void neverExceedsHardMaxOrLargestWindow() {
        // 10h over 2 sessions would be 300 each; both the hard cap (120) and a 90-minute window apply.
        List<Integer> durations = StudySessionSizingPolicy.planWeeklyDurations(600, 2, 90);

        assertNoOddDurations(durations);
        durations.forEach(duration -> assertTrue(duration <= 90, "duration must fit the largest window"));
    }

    @Test
    void keepsEverySessionAtLeastTheScheduledMinimum() {
        // Tiny budget across many sessions would otherwise produce sub-minimum slivers.
        List<Integer> durations = StudySessionSizingPolicy.planWeeklyDurations(60, 4, 120);

        assertNoOddDurations(durations);
        durations.forEach(duration -> assertTrue(
                duration >= StudySessionSizingPolicy.MIN_SCHEDULED_SESSION_MINUTES,
                "duration must not drop below the scheduled minimum"));
    }

    @Test
    void fitToWindowClampsToWindowAndStaysAMultipleOfFifteen() {
        assertEquals(90, StudySessionSizingPolicy.fitToWindow(90, 120));
        assertEquals(60, StudySessionSizingPolicy.fitToWindow(90, 60));
        assertEquals(60, StudySessionSizingPolicy.fitToWindow(90, 70)); // floored to a clean block
    }

    private void assertNoOddDurations(List<Integer> durations) {
        durations.forEach(duration -> assertTrue(
                StudySessionSizingPolicy.isHumanFriendly(duration),
                "duration " + duration + " is not a multiple of 15"));
    }
}
