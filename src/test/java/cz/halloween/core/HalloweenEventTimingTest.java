package cz.halloween.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HalloweenEventTimingTest {
    @Test
    void defaultPhaseUsesConfiguredFifteenToTwentyFourMinuteRange() {
        assertEquals(new HalloweenEventTiming.Interval(15, 24),
                HalloweenEventTiming.intervalForPhase(0, 15, 24));
    }

    @Test
    void milestonesShortenIntervalsButNeverBelowTwelveMinutes() {
        assertEquals(new HalloweenEventTiming.Interval(12, 21),
                HalloweenEventTiming.intervalForPhase(3, 15, 24));
        assertEquals(new HalloweenEventTiming.Interval(12, 12),
                HalloweenEventTiming.intervalForPhase(40, 15, 24));
    }

    @Test
    void durationGrowsSlowlyAndCapsAtEightMinutesForDefaultDuration() {
        assertEquals(5, HalloweenEventTiming.durationMinutes(0, 5));
        assertEquals(6, HalloweenEventTiming.durationMinutes(2, 5));
        assertEquals(8, HalloweenEventTiming.durationMinutes(100, 5));
    }

    @Test
    void malformedBaseIntervalIsNormalized() {
        assertEquals(new HalloweenEventTiming.Interval(12, 12),
                HalloweenEventTiming.intervalForPhase(-5, -1, -2));
    }
}
