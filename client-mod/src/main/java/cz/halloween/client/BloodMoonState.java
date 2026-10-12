package cz.halloween.client;

/**
 * Client-only visual state for Iris' standard worldDay/worldTime uniforms.
 *
 * During the real server event the client renderer sees a reserved full-moon day
 * and a synthetic night time. Entry first blends the regular time-of-day to night,
 * then blends the normal sky into Blood Moon red. Exit fades the red while still
 * at night, then blends the sky clock back to the actual server time-of-day.
 *
 * This affects only the value returned to the client renderer; the server clock,
 * gameplay time, mob AI, scheduled tasks and server time commands are unchanged.
 */
public final class BloodMoonState {
    public static final long RESERVED_DAY = 123_456_784L;
    // A second synthetic day, eight days later, has the same full-moon phase but
    // does not trigger the shader's bloodMoon marker.
    private static final long NEUTRAL_DAY = RESERVED_DAY + 8L;
    private static final long TRANSITION_STEP_MS = 100L;
    private static final long TRANSITION_STEPS = 20L;

    private enum Mode {
        INACTIVE,
        ENTERING_NIGHT,
        ENTERING_BLOOD,
        ACTIVE,
        EXITING_BLOOD,
        EXITING_DAY
    }

    private static volatile Mode mode = Mode.INACTIVE;
    private static volatile long transitionStartedAt;
    private static volatile long transitionFromTime = 18_000L;
    private static volatile long lastSyntheticTime = Long.MIN_VALUE;

    private BloodMoonState() {
    }

    public static synchronized void setActive(boolean active) {
        long now = System.currentTimeMillis();
        if (active) {
            if (mode == Mode.ACTIVE || mode == Mode.ENTERING_NIGHT || mode == Mode.ENTERING_BLOOD) {
                return;
            }
            transitionFromTime = lastSyntheticTime == Long.MIN_VALUE
                    ? 18_000L : Math.floorMod(lastSyntheticTime, 24_000L);
            mode = Mode.ENTERING_NIGHT;
            transitionStartedAt = now;
        } else {
            if (mode == Mode.INACTIVE || mode == Mode.EXITING_BLOOD || mode == Mode.EXITING_DAY) {
                return;
            }
            transitionFromTime = lastSyntheticTime == Long.MIN_VALUE
                    ? 18_020L : Math.floorMod(lastSyntheticTime, 24_000L);
            mode = (mode == Mode.ENTERING_NIGHT) ? Mode.EXITING_DAY : Mode.EXITING_BLOOD;
            transitionStartedAt = now;
        }
    }

    public static synchronized void reset() {
        mode = Mode.INACTIVE;
        transitionStartedAt = 0L;
        transitionFromTime = 18_000L;
        lastSyntheticTime = Long.MIN_VALUE;
    }

    /**
     * Called at the RETURN injection of the client world's getTimeOfDay method.
     * Returns null when the client must use Minecraft's real synchronized time.
     */
    public static synchronized Long getSyntheticTimeOfDay(long actualTimeOfDay) {
        Mode current = mode;
        if (current == Mode.INACTIVE) {
            lastSyntheticTime = Long.MIN_VALUE;
            return null;
        }

        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - transitionStartedAt);
        long progress = Math.min(TRANSITION_STEPS, elapsed / TRANSITION_STEP_MS);
        long actualWithinDay = Math.floorMod(actualTimeOfDay, 24_000L);
        long syntheticTime;

        switch (current) {
            case ENTERING_NIGHT -> {
                if (progress >= TRANSITION_STEPS) {
                    mode = Mode.ENTERING_BLOOD;
                    transitionStartedAt = now;
                    syntheticTime = RESERVED_DAY * 24_000L + 18_000L;
                } else {
                    long blendedTime = blendTime(actualWithinDay, 18_000L, progress);
                    syntheticTime = NEUTRAL_DAY * 24_000L + blendedTime;
                }
            }
            case ENTERING_BLOOD -> {
                if (progress >= TRANSITION_STEPS) {
                    mode = Mode.ACTIVE;
                    transitionStartedAt = 0L;
                    syntheticTime = RESERVED_DAY * 24_000L + 18_020L;
                } else {
                    syntheticTime = RESERVED_DAY * 24_000L + 18_000L + progress;
                }
            }
            case ACTIVE -> syntheticTime = RESERVED_DAY * 24_000L + 18_020L;
            case EXITING_BLOOD -> {
                if (progress >= TRANSITION_STEPS) {
                    mode = Mode.EXITING_DAY;
                    transitionStartedAt = now;
                    transitionFromTime = 18_000L;
                    syntheticTime = NEUTRAL_DAY * 24_000L + 18_000L;
                } else {
                    syntheticTime = RESERVED_DAY * 24_000L
                            + blendTime(transitionFromTime, 18_000L, progress);
                }
            }
            case EXITING_DAY -> {
                if (progress >= TRANSITION_STEPS) {
                    mode = Mode.INACTIVE;
                    transitionStartedAt = 0L;
                    lastSyntheticTime = Long.MIN_VALUE;
                    return null;
                }
                syntheticTime = NEUTRAL_DAY * 24_000L
                        + blendTime(transitionFromTime, actualWithinDay, progress);
            }
            default -> {
                lastSyntheticTime = Long.MIN_VALUE;
                return null;
            }
        }

        lastSyntheticTime = syntheticTime;
        return syntheticTime;
    }

    private static long blendTime(long from, long to, long progress) {
        double amount = Math.max(0.0D, Math.min(1.0D, progress / (double) TRANSITION_STEPS));
        return Math.round(from + (to - from) * amount);
    }
}
