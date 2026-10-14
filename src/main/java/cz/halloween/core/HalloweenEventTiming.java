package cz.halloween.core;

/** Pure timing rules for ordinary Halloween event scheduling. */
public final class HalloweenEventTiming {
    public record Interval(int minimumMinutes, int maximumMinutes) {
        public Interval {
            if (minimumMinutes < 1 || maximumMinutes < minimumMinutes) {
                throw new IllegalArgumentException("Invalid interval bounds");
            }
        }
    }

    private HalloweenEventTiming() {
    }

    public static Interval intervalForPhase(int phase, int baseMinimum, int baseMaximum) {
        int safePhase = Math.max(0, phase);
        int safeMinimum = Math.max(1, baseMinimum);
        int safeMaximum = Math.max(safeMinimum, baseMaximum);
        // Milestones shorten the start-to-start interval gradually, but never below
        // twelve minutes. Longest event duration is capped at eight minutes.
        int minimum = Math.max(12, safeMinimum - safePhase);
        int maximum = Math.max(minimum, safeMaximum - safePhase);
        return new Interval(minimum, maximum);
    }

    public static int durationMinutes(int phase, int baseDuration) {
        return Math.max(1, Math.max(1, baseDuration) + Math.min(3, Math.max(0, phase) / 2));
    }
}
