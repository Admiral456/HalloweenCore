package cz.halloween.client;

/**
 * Client-only visual state. The synthetic day marker is intentionally far outside
 * realistic server day counts and divisible by eight so the shader can distinguish
 * it from regular midnight and the moon uses the full-moon phase.
 *
 * World.getTimeOfDay() is overridden only on the client and only while an actual
 * server event message is active or its two-second transition is being rendered.
 * The real server clock, mob AI, and gameplay time are not modified.
 */
public final class BloodMoonState {
    public static final long RESERVED_DAY = 123_456_784L;
    private static final long TRANSITION_MS = 2_000L;

    private enum Mode {
        INACTIVE,
        ENTERING,
        ACTIVE,
        EXITING
    }

    private static volatile Mode mode = Mode.INACTIVE;
    private static volatile long transitionStartedAt;

    private BloodMoonState() {
    }

    public static synchronized void setActive(boolean active) {
        long now = System.currentTimeMillis();
        if (active) {
            if (mode == Mode.ACTIVE || mode == Mode.ENTERING) return;
            mode = Mode.ENTERING;
            transitionStartedAt = now;
        } else {
            if (mode == Mode.INACTIVE || mode == Mode.EXITING) return;
            mode = Mode.EXITING;
            transitionStartedAt = now;
        }
    }

    public static synchronized void reset() {
        mode = Mode.INACTIVE;
        transitionStartedAt = 0L;
    }

    /**
     * Called by a mixin on the client-only World.getTimeOfDay method.
     * The time-of-day remainder stays at night (around 18000), while division by
     * 24000 exposes RESERVED_DAY to Iris' worldDay uniform.
     */
    public static synchronized Long getSyntheticTimeOfDay() {
        Mode current = mode;
        if (current == Mode.INACTIVE) return null;

        long elapsed = Math.max(0L, System.currentTimeMillis() - transitionStartedAt);
        long progress = Math.min(20L, elapsed / 100L);
        long timeMarker;

        if (current == Mode.ENTERING) {
            if (progress >= 20L) {
                mode = Mode.ACTIVE;
                timeMarker = 18_020L;
            } else {
                timeMarker = 18_000L + progress;
            }
        } else if (current == Mode.ACTIVE) {
            timeMarker = 18_020L;
        } else {
            timeMarker = 18_020L - progress;
            if (progress >= 20L) {
                mode = Mode.INACTIVE;
                transitionStartedAt = 0L;
                return null;
            }
        }

        return RESERVED_DAY * 24_000L + timeMarker;
    }
}
