package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small, vanilla-only timed events. No custom mobs, models, shaders, or sound assets.
 */
public final class HalloweenVanillaEventManager implements Listener {
    private static final List<String> EVENT_IDS = List.of(
            "pumpkin-harvest", "witching-hour", "blood-moon"
    );

    private final HalloweenCore plugin;
    private BukkitTask tickerTask;
    private String activeEventId;
    private long activeUntil;
    private long nextEventAt;

    public HalloweenVanillaEventManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vanilla-events.enabled", true);
    }

    public void start() {
        if (tickerTask != null || !isEnabled() || !plugin.isEventEnabled()) {
            if (!isEnabled() || !plugin.isEventEnabled()) nextEventAt = 0L;
            return;
        }
        scheduleFirstEvent();
        tickerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getLogger().info("Vanilla Halloween events scheduled.");
    }

    public void stop() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        activeEventId = null;
        activeUntil = 0L;
        nextEventAt = 0L;
    }

    public void restart() {
        stop();
        start();
    }

    public boolean startEventNow(String requestedId) {
        if (!isEnabled() || !plugin.isEventEnabled() || activeEventId != null) return false;
        String id = requestedId == null ? "" : requestedId.trim().toLowerCase(Locale.ROOT);
        if (!EVENT_IDS.contains(id)) return false;
        beginEvent(id);
        return true;
    }

    public boolean stopActiveEventNow() {
        if (activeEventId == null) return false;
        finishActiveEvent(true, true);
        return true;
    }

    public List<String> getKnownEventIds() {
        return EVENT_IDS;
    }

    public String getActiveEventId() {
        return activeEventId;
    }

    public long getRemainingSeconds() {
        if (activeEventId == null) return 0L;
        return Math.max(0L, (activeUntil - System.currentTimeMillis() + 999L) / 1000L);
    }

    public long getNextEventSeconds() {
        if (activeEventId != null || nextEventAt <= 0L || !isEnabled() || !plugin.isEventEnabled()) return -1L;
        return Math.max(0L, (nextEventAt - System.currentTimeMillis() + 999L) / 1000L);
    }

    public String getDisplayName(String eventId) {
        if (eventId == null) return "Žádná událost";
        return switch (eventId.toLowerCase(Locale.ROOT)) {
            case "pumpkin-harvest" -> "Dýňová sklizeň";
            case "witching-hour" -> "Čarodějná hodina";
            case "blood-moon" -> "Krvavý měsíc";
            default -> eventId;
        };
    }

    public double getMultiplier(String source) {
        if (activeEventId == null || source == null) return 1.0D;
        String normalized = source.toLowerCase(Locale.ROOT);
        double multiplier = switch (activeEventId) {
            case "pumpkin-harvest" -> normalized.equals("farming")
                    ? plugin.getConfig().getDouble(
                        "vanilla-events.pumpkin-harvest.farming-fragment-multiplier", 2.0D)
                    : 1.0D;
            case "witching-hour" -> normalized.equals("mob-kill")
                    ? plugin.getConfig().getDouble(
                        "vanilla-events.witching-hour.mob-kill-fragment-multiplier", 1.5D)
                    : 1.0D;
            case "blood-moon" -> normalized.equals("mob-kill")
                    ? plugin.getConfig().getDouble(
                        "vanilla-events.blood-moon.mob-kill-fragment-multiplier", 1.25D)
                    : 1.0D;
            default -> 1.0D;
        };
        return Math.max(1.0D, Math.min(4.0D, multiplier));
    }

    private void tick() {
        if (!isEnabled() || !plugin.isEventEnabled()) {
            stop();
            return;
        }

        long now = System.currentTimeMillis();
        if (activeEventId != null && now >= activeUntil) {
            finishActiveEvent(true, true);
        }

        if (activeEventId == null && nextEventAt > 0L && now >= nextEventAt
                && Bukkit.getOnlinePlayers().stream().anyMatch(plugin::isEligibleGameplayPlayer)) {
            String id = EVENT_IDS.get(ThreadLocalRandom.current().nextInt(EVENT_IDS.size()));
            beginEvent(id);
        }
    }

    private void scheduleFirstEvent() {
        int delaySeconds = Math.max(30,
                Math.min(3600, plugin.getConfig().getInt("vanilla-events.first-delay-seconds", 300)));
        nextEventAt = System.currentTimeMillis() + delaySeconds * 1000L;
    }

    private void scheduleNextEvent() {
        int min = Math.max(5, plugin.getConfig().getInt("vanilla-events.interval-minutes.min", 20));
        int max = Math.max(min, plugin.getConfig().getInt("vanilla-events.interval-minutes.max", 35));
        max = Math.min(180, max);
        min = Math.min(min, max);
        int minutes = ThreadLocalRandom.current().nextInt(min, max + 1);
        nextEventAt = System.currentTimeMillis() + minutes * 60_000L;
    }

    private void beginEvent(String id) {
        if (!EVENT_IDS.contains(id) || activeEventId != null) return;
        activeEventId = id;
        int durationMinutes = Math.max(1,
                Math.min(30, plugin.getConfig().getInt("vanilla-events.duration-minutes", 6)));
        activeUntil = System.currentTimeMillis() + durationMinutes * 60_000L;
        nextEventAt = 0L;

        String name = getDisplayName(id);
        Bukkit.broadcastMessage(plugin.color("&6&lHALLOWEEN &8» &eZačíná událost &6&l" + name
                + "&e! &7Délka: " + durationMinutes + " min."));
        String sound = switch (id) {
            case "pumpkin-harvest" -> "minecraft:block.composter.ready";
            case "witching-hour" -> "minecraft:entity.witch.ambient";
            case "blood-moon" -> "minecraft:entity.wither.ambient";
            default -> "minecraft:ambient.cave";
        };
        float pitch = "blood-moon".equals(id) ? 0.65F : 0.9F;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)
                    || !plugin.isEligibleGameplayWorld(player.getWorld())) continue;
            player.sendTitle(plugin.color("&6&l" + name), plugin.color("&7Halloween událost začala."),
                    10, 50, 20);
            player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, 0.7F, pitch);
        }
        plugin.getLogger().info("Vanilla Halloween event started: " + id
                + " (duration=" + durationMinutes + "m)");
    }

    private void finishActiveEvent(boolean announce, boolean scheduleNext) {
        String finished = activeEventId;
        activeEventId = null;
        activeUntil = 0L;
        if (announce && finished != null) {
            Bukkit.broadcastMessage(plugin.color("&6&lHALLOWEEN &8» &7Událost &e"
                    + getDisplayName(finished) + " &7skončila."));
        }
        if (scheduleNext && isEnabled() && plugin.isEventEnabled()) scheduleNextEvent();
        else nextEventAt = 0L;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBloodMoonDamage(EntityDamageByEntityEvent event) {
        if (!"blood-moon".equals(activeEventId) || !plugin.isEventEnabled()) return;
        if (!(event.getEntity() instanceof Player target)
                || !plugin.isEligibleGameplayPlayer(target)
                || !plugin.isEligibleGameplayWorld(target.getWorld())) return;

        LivingEntity attacker = resolveLivingAttacker(event.getDamager());
        if (!(attacker instanceof Monster)) return;

        double multiplier = plugin.getConfig().getDouble(
                "vanilla-events.blood-moon.hostile-damage-multiplier", 1.5D);
        multiplier = Math.max(1.0D, Math.min(3.0D, multiplier));
        event.setDamage(event.getDamage() * multiplier);
    }

    private LivingEntity resolveLivingAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity living) return living;
        return null;
    }
}
