package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Optional calendar-based season controls and a clock-based Witching Hour window.
 * The feature is deliberately configuration-gated so existing servers retain their
 * current manual on/off behaviour unless they enable it.
 */
public final class HalloweenSeasonManager {
    private static final List<String> AMBIENT_SOUNDS = List.of(
            "minecraft:entity.witch.ambient",
            "minecraft:entity.bat.ambient",
            "minecraft:ambient.cave",
            "minecraft:entity.wither.ambient"
    );

    private final HalloweenCore plugin;
    private boolean started;
    private boolean lastSeasonEnabled;
    private Boolean lastInSeason;
    private boolean lastWitchingHourActive;
    private long lastAmbientSoundAt;
    private long taskId = -1L;

    public HalloweenSeasonManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) return;
        started = true;
        lastSeasonEnabled = plugin.getConfig().getBoolean("season.enabled", false);
        refresh();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1200L, 1200L).getTaskId();
    }

    public void stop() {
        started = false;
        if (taskId >= 0L) {
            Bukkit.getScheduler().cancelTask((int) taskId);
            taskId = -1L;
        }
    }

    /** Re-evaluates current settings immediately after /halloween reload. */
    public void refresh() {
        checkSeasonBoundary();
        updateWitchingHourState(true);
    }

    private void tick() {
        if (!started) return;
        checkSeasonBoundary();
        updateWitchingHourState(false);
        playAmbientCueIfDue();
    }

    private void checkSeasonBoundary() {
        boolean enabled = plugin.getConfig().getBoolean("season.enabled", false);
        if (!enabled) {
            lastSeasonEnabled = false;
            return;
        }

        LocalDate today = LocalDate.now(zone());
        boolean inSeason = isInSeason(today);
        boolean needsInitialSync = !lastSeasonEnabled || lastInSeason == null;
        boolean crossedBoundary = lastInSeason != null && lastInSeason.booleanValue() != inSeason;

        if (needsInitialSync || crossedBoundary) {
            // Persist the state before invoking setEventEnabled(), which also saves config.
            plugin.getConfig().set("season.runtime-last-in-season", inSeason);
            plugin.saveConfig();
            if (plugin.isEventEnabled() != inSeason) {
                plugin.setEventEnabled(inSeason);
            }
        }
        lastInSeason = inSeason;
        lastSeasonEnabled = true;
    }

    private boolean isInSeason(LocalDate date) {
        MonthDay start = parseMonthDay("season.start", "10-01");
        MonthDay end = parseMonthDay("season.end", "11-05");
        MonthDay today = MonthDay.from(date);

        if (start.equals(end)) return true;
        if (!start.isAfter(end)) {
            return !today.isBefore(start) && !today.isAfter(end);
        }
        // A season whose end precedes its start wraps across New Year's Day.
        return !today.isBefore(start) || !today.isAfter(end);
    }

    private MonthDay parseMonthDay(String path, String fallback) {
        String configured = plugin.getConfig().getString(path, fallback);
        try {
            String[] values = (configured == null ? fallback : configured).trim().split("-");
            if (values.length != 2) throw new IllegalArgumentException("Expected MM-DD");
            return MonthDay.of(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Invalid " + path + " value; using " + fallback + ".");
            String[] values = fallback.split("-");
            return MonthDay.of(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
        }
    }

    private ZoneId zone() {
        String configured = plugin.getConfig().getString("season.timezone", "Europe/Prague");
        if (configured == null || configured.isBlank()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(configured.trim());
        } catch (DateTimeException ex) {
            plugin.getLogger().warning("Invalid season.timezone '" + configured + "'; using server timezone.");
            return ZoneId.systemDefault();
        }
    }

    /**
     * Sends a countdown to joining players while the season start is within the
     * configured number of days. This is intentionally independent of eventEnabled.
     */
    public void sendCountdownIfNeeded(Player player) {
        if (player == null || !plugin.getConfig().getBoolean("season.enabled", false)) return;
        int countdownDays = Math.max(0, Math.min(30, plugin.getConfig().getInt("season.countdown-days", 7)));
        if (countdownDays == 0) return;

        LocalDate today = LocalDate.now(zone());
        if (isInSeason(today)) return;
        LocalDate start = nextStartDate(today);
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, start);
        if (days < 0 || days > countdownDays) return;

        String message = plugin.getConfig().getString(
                "season.countdown-message",
                "&6&lHALLOWEEN &8» &eHalloween sezóna začíná za &c%days% &ední."
        );
        player.sendMessage(plugin.color(message.replace("%days%", Long.toString(days))));
    }

    private LocalDate nextStartDate(LocalDate today) {
        MonthDay start = parseMonthDay("season.start", "10-01");
        LocalDate candidate = start.atYear(today.getYear());
        if (candidate.isBefore(today)) candidate = start.atYear(today.getYear() + 1);
        return candidate;
    }

    public double getEventIntervalMultiplier() {
        if (!plugin.getConfig().getBoolean("witching-hour.enabled", false)
                || !isWitchingHourActiveNow()) return 1.0D;
        double configured = plugin.getConfig().getDouble("witching-hour.interval-multiplier", 0.5D);
        if (!Double.isFinite(configured)) return 0.5D;
        return Math.max(0.1D, Math.min(3.0D, configured));
    }

    private boolean isWitchingHourActiveNow() {
        if (!plugin.getConfig().getBoolean("witching-hour.enabled", false)) return false;
        String mode = plugin.getConfig().getString("witching-hour.mode", "real");
        if (mode != null && mode.equalsIgnoreCase("minecraft")) {
            return Bukkit.getWorlds().stream().anyMatch(this::isWitchingHourActive);
        }
        return isWithinClockWindow(LocalTime.now(zone()), "witching-hour.start", "witching-hour.end");
    }

    public boolean isWitchingHourActive(World world) {
        if (world == null || !plugin.getConfig().getBoolean("witching-hour.enabled", false)) return false;
        String mode = plugin.getConfig().getString("witching-hour.mode", "real");
        if (mode != null && mode.equalsIgnoreCase("minecraft")) {
            long start = Math.floorMod(plugin.getConfig().getLong("witching-hour.minecraft-start-tick", 18000L), 24000L);
            long end = Math.floorMod(plugin.getConfig().getLong("witching-hour.minecraft-end-tick", 22000L), 24000L);
            long time = Math.floorMod(world.getTime(), 24000L);
            return isBetween(time, start, end);
        }
        return isWithinClockWindow(LocalTime.now(zone()), "witching-hour.start", "witching-hour.end");
    }

    private boolean isWithinClockWindow(LocalTime now, String startPath, String endPath) {
        LocalTime start = parseTime(startPath, "20:00");
        LocalTime end = parseTime(endPath, "22:00");
        int current = now.toSecondOfDay();
        return isBetween(current, start.toSecondOfDay(), end.toSecondOfDay());
    }

    private boolean isBetween(long current, long start, long end) {
        if (start == end) return true;
        if (start < end) return current >= start && current < end;
        return current >= start || current < end;
    }

    private LocalTime parseTime(String path, String fallback) {
        String configured = plugin.getConfig().getString(path, fallback);
        try {
            return LocalTime.parse(configured == null ? fallback : configured.trim());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Invalid " + path + " value; using " + fallback + ".");
            return LocalTime.parse(fallback);
        }
    }

    private void updateWitchingHourState(boolean force) {
        boolean active = isWitchingHourActiveNow();
        if (!force && active == lastWitchingHourActive) return;

        boolean changed = active != lastWitchingHourActive;
        lastWitchingHourActive = active;
        if (changed && plugin.getConfig().getBoolean("witching-hour.announce", true)) {
            Bukkit.broadcastMessage(plugin.color(active
                    ? "&5&lČARODĚJNICKÁ HODINA &8» &dTemnota sílí. Halloween události přicházejí častěji."
                    : "&5&lČARODĚJNICKÁ HODINA &8» &7Temnota na chvíli ustupuje."));
        }

        if (changed && plugin.getConfig().getBoolean("witching-hour.reschedule-on-transition", true)
                && plugin.getEventManager() != null) {
            plugin.getEventManager().rescheduleForTimeWindow();
        }
    }

    private void playAmbientCueIfDue() {
        if (!plugin.getConfig().getBoolean("witching-hour.enabled", false)
                || !plugin.getConfig().getBoolean("witching-hour.ambient-sounds", false)
                || !isWitchingHourActiveNow()) return;

        long intervalMillis = Math.max(30L,
                Math.min(1800L, plugin.getConfig().getLong("witching-hour.ambient-interval-seconds", 180L))) * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastAmbientSoundAt < intervalMillis) return;
        lastAmbientSoundAt = now;

        String sound = AMBIENT_SOUNDS.get(ThreadLocalRandom.current().nextInt(AMBIENT_SOUNDS.size()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)
                    || !plugin.isAutomaticHalloweenWorld(player.getWorld())
                    || !isWitchingHourActive(player.getWorld())) continue;
            try {
                player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, 0.65f,
                        0.75f + ThreadLocalRandom.current().nextFloat() * 0.5f);
            } catch (RuntimeException ignored) {
                // A missing optional sound must never interrupt the seasonal scheduler.
            }
        }
    }
}
