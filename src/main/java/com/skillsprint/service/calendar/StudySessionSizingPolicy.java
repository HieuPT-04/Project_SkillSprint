package com.skillsprint.service.calendar;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a weekly study-minute budget into a list of human-friendly per-session durations.
 *
 * <p>The Study Calendar used to give every generated task a single flat duration equal to
 * {@code weeklyMinutes / studyDays} (or the raw length of the first time window). That produced
 * learner-unfriendly values such as 80, 96 or 113 minutes. This policy instead distributes the
 * weekly budget across the default number of weekly sessions (one per selected study day) using
 * clean blocks of {@link #ROUND_BLOCK_MINUTES} minutes, preferring values in
 * {@code [PREFERRED_MIN, PREFERRED_MAX]} and only reaching {@link #HARD_MAX_SESSION_MINUTES}
 * when the budget genuinely needs it.
 *
 * <p>Example: 480 minutes over 6 sessions becomes {@code [90, 90, 75, 75, 75, 75]} (total 480);
 * 480 over 5 becomes {@code [105, 105, 90, 90, 90]} (total 480). The total weekly minutes is
 * preserved exactly whenever the budget is a multiple of {@link #ROUND_BLOCK_MINUTES} and fits
 * within the per-session bounds; otherwise the closest valid distribution is returned.
 */
final class StudySessionSizingPolicy {

    static final int ROUND_BLOCK_MINUTES = 15;
    static final int MIN_SCHEDULED_SESSION_MINUTES = 30;
    static final int PREFERRED_MIN_SESSION_MINUTES = 45;
    static final int PREFERRED_MAX_SESSION_MINUTES = 90;
    static final int HARD_MAX_SESSION_MINUTES = 120;

    private StudySessionSizingPolicy() {
    }

    /**
     * Plans the per-session durations for one representative week.
     *
     * @param weeklyMinutes     total study minutes the user committed to per week
     * @param sessionsPerWeek   default number of sessions per week (one per selected study day)
     * @param maxWindowMinutes  length of the largest selected availability window; the returned
     *                          durations never exceed this (nor {@link #HARD_MAX_SESSION_MINUTES})
     *                          so each session can fit in at least one window
     * @return a list of {@code sessionsPerWeek} durations, each a multiple of
     *         {@link #ROUND_BLOCK_MINUTES}, ordered largest-first
     */
    static List<Integer> planWeeklyDurations(int weeklyMinutes, int sessionsPerWeek, int maxWindowMinutes) {
        if (sessionsPerWeek <= 0) {
            return List.of();
        }

        int effectiveMax = Math.min(HARD_MAX_SESSION_MINUTES, floorToBlock(maxWindowMinutes));
        if (effectiveMax < ROUND_BLOCK_MINUTES) {
            effectiveMax = ROUND_BLOCK_MINUTES;
        }
        int maxUnits = effectiveMax / ROUND_BLOCK_MINUTES;
        int minUnits = Math.min(MIN_SCHEDULED_SESSION_MINUTES / ROUND_BLOCK_MINUTES, maxUnits);

        // Round the budget to the nearest clean block, then keep it within what the sessions can
        // actually hold ([minUnits, maxUnits] per session) so we never emit sub-minimum or
        // over-cap durations.
        int totalUnits = Math.round((float) Math.max(0, weeklyMinutes) / ROUND_BLOCK_MINUTES);
        totalUnits = clamp(totalUnits, sessionsPerWeek * minUnits, sessionsPerWeek * maxUnits);

        int base = totalUnits / sessionsPerWeek;
        int remainder = totalUnits % sessionsPerWeek;

        List<Integer> durations = new ArrayList<>(sessionsPerWeek);
        for (int i = 0; i < sessionsPerWeek; i++) {
            int units = base + (i < remainder ? 1 : 0);
            durations.add(units * ROUND_BLOCK_MINUTES);
        }
        return durations;
    }

    /**
     * Fits a planned duration into a concrete availability window: never longer than the window,
     * always a multiple of {@link #ROUND_BLOCK_MINUTES}. Used when a session is finally placed in
     * a specific (possibly smaller) window than the one the weekly plan assumed.
     */
    static int fitToWindow(int plannedMinutes, int windowMinutes) {
        int cap = floorToBlock(windowMinutes);
        if (cap <= 0) {
            // Window shorter than a single block: keep the raw window length as a last resort so the
            // task still fits rather than spilling past the user's availability.
            return Math.max(1, windowMinutes);
        }
        return Math.max(ROUND_BLOCK_MINUTES, Math.min(plannedMinutes, cap));
    }

    static boolean isHumanFriendly(int minutes) {
        return minutes > 0 && minutes % ROUND_BLOCK_MINUTES == 0;
    }

    private static int floorToBlock(int minutes) {
        return (minutes / ROUND_BLOCK_MINUTES) * ROUND_BLOCK_MINUTES;
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }
}
