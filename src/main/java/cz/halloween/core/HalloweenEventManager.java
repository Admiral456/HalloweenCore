package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.Particle;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class HalloweenEventManager {
    private final HalloweenCore plugin;
    private String activeEventId;
    private long activeUntil;
    private long nextEventAt;
    private boolean started;
    private long lastSurgeAt;

    public HalloweenEventManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (started) return;
        started = true;
        if (plugin.getConfig().getBoolean("random-events.enabled", true)) {
            scheduleNextEvent();
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void reloadSchedule() {
        if (activeEventId != null && plugin.getMobManager() != null) {
            plugin.getMobManager().cleanupEventMobs();
        }
        activeEventId = null;
        activeUntil = 0L;
        lastSurgeAt = 0L;
        if (started && plugin.getConfig().getBoolean("random-events.enabled", true)) {
            scheduleNextEvent();
        } else {
            nextEventAt = 0L;
        }
    }

    public void stop() {
        if (activeEventId != null && plugin.getMobManager() != null) {
            plugin.getMobManager().cleanupEventMobs();
        }
        activeEventId = null;
        activeUntil = 0L;
        nextEventAt = 0L;
        lastSurgeAt = 0L;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("random-events.enabled", true)) {
            if (activeEventId != null) endEvent();
            return;
        }
        if (!plugin.isEventEnabled()) return;

        long now = System.currentTimeMillis();
        if (activeEventId != null && now >= activeUntil) {
            endEvent();
            return;
        }

        if (activeEventId != null) {
            runActiveEventEffects(now);
        }

        if (activeEventId == null && now >= nextEventAt && !Bukkit.getOnlinePlayers().isEmpty()) {
            startRandomEvent();
        }
    }

    private void scheduleNextEvent() {
        int phase = getGlobalPhase();
        int baseMin = Math.max(1, plugin.getConfig().getInt("random-events.interval-minutes.min", 20));
        int baseMax = Math.max(baseMin, plugin.getConfig().getInt("random-events.interval-minutes.max", 35));
        int min = Math.max(8, baseMin - phase * 2);
        int max = Math.max(min, baseMax - phase * 2);
        int minutes = ThreadLocalRandom.current().nextInt(min, max + 1);
        nextEventAt = System.currentTimeMillis() + minutes * 60_000L;
    }

    private void startRandomEvent() {
        String[] events = {"soulstorm", "witching-hour", "cursed-harvest"};
        activeEventId = events[ThreadLocalRandom.current().nextInt(events.length)];

        int phase = getGlobalPhase();
        int duration = Math.max(1, plugin.getConfig().getInt("random-events.duration-minutes", 5) + Math.min(3, phase / 2));
        activeUntil = System.currentTimeMillis() + duration * 60_000L;
        lastSurgeAt = 0L;

        String path = "random-events.types." + activeEventId;
        String name = plugin.getConfig().getString(path + ".name", activeEventId);
        String message = plugin.getConfig().getString(path + ".start-message",
                "&6&lHALLOWEEN &8» &f" + name + " začíná!");

        Bukkit.broadcastMessage(plugin.color(message.replace("%duration%", Integer.toString(duration))));
        playEventSound();
    }

    private void endEvent() {
        String path = "random-events.types." + activeEventId;
        String message = plugin.getConfig().getString(path + ".end-message",
                "&6&lHALLOWEEN &8» &7Halloween událost skončila.");
        Bukkit.broadcastMessage(plugin.color(message));
        if (plugin.getMobManager() != null) {
            plugin.getMobManager().cleanupEventMobs();
        }

        activeEventId = null;
        activeUntil = 0L;
        scheduleNextEvent();
    }

    private void playEventSound() {
        String configured = plugin.getConfig().getString("random-events.sound", "");
        if (configured == null || configured.isBlank()) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.playSound(player.getLocation(), configured.toLowerCase(Locale.ROOT), 1.0f, 0.65f);
            } catch (Exception ignored) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.7f);
            }
        }
    }

    private void runActiveEventEffects(long now) {
        long surgeIntervalSeconds = Math.max(10L, plugin.getConfig().getLong("random-events.surge-interval-seconds", 45L));
        if (now - lastSurgeAt < surgeIntervalSeconds * 1000L) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        Player target = players[ThreadLocalRandom.current().nextInt(players.length)];

        if (activeEventId.equalsIgnoreCase("soulstorm")) {
            if (plugin.getMobManager().spawnEventMob(target)) {
                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0, 1, 0), 16, 1.0, 1.0, 1.0, 0.02);
                target.sendMessage(plugin.color("&5Duše se shlukují... &7Nedaleko se objevil prokletý lovec."));
            }
        } else if (activeEventId.equalsIgnoreCase("witching-hour")) {
            if (plugin.getMobManager().spawnEventMob(target)) {
                target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 18, 1.0, 1.0, 1.0, 0.05);
                target.sendMessage(plugin.color("&5Čarodějnická hodina &8» &7něco se k tobě blíží."));
            }
        } else if (activeEventId.equalsIgnoreCase("cursed-harvest")) {
            target.getWorld().spawnParticle(Particle.COMPOSTER, target.getLocation().add(0, 1, 0), 14, 0.8, 0.6, 0.8, 0.03);
        }

        lastSurgeAt = now;
    }

    public int getGlobalPhase() {
        int phase = 0;
        long total = plugin.getService().getServerFragments();
        for (long milestone : plugin.getConfig().getLongList("global-milestones")) {
            if (milestone > 0L && total >= milestone) phase++;
        }
        return phase;
    }

    public boolean isActive(String id) {
        return id != null && id.equalsIgnoreCase(activeEventId);
    }

    public double getMultiplier(String source) {
        if (activeEventId == null) return 1.0D;
        String path = "random-events.types." + activeEventId + ".multipliers." + source;
        return Math.max(1.0D, plugin.getConfig().getDouble(path, 1.0D));
    }

    public String getActiveEventId() {
        return activeEventId;
    }

    public long getRemainingSeconds() {
        if (activeEventId == null) return 0L;
        return Math.max(0L, (activeUntil - System.currentTimeMillis()) / 1000L);
    }

    public long getNextEventSeconds() {
        if (activeEventId != null) return 0L;
        return Math.max(0L, (nextEventAt - System.currentTimeMillis()) / 1000L);
    }
}
