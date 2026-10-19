package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.World;
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
    private long nextInvasionWaveAt;
    private int invasionWaveNumber;
    private boolean invasionCaptainSpawned;

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
        cleanupBloodMoonInvasion();
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
        if (!Double.isFinite(multiplier)) return 1.0D;
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
                && Bukkit.getOnlinePlayers().stream()
                    .filter(plugin::isEligibleGameplayPlayer)
                    .anyMatch(player -> plugin.isEligibleGameplayWorld(player.getWorld()))) {
            String id = EVENT_IDS.get(ThreadLocalRandom.current().nextInt(EVENT_IDS.size()));
            beginEvent(id);
        }

        if ("blood-moon".equals(activeEventId)) tickBloodMoonInvasion(now);
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
        nextInvasionWaveAt = 0L;
        invasionWaveNumber = 0;
        invasionCaptainSpawned = false;
        if ("blood-moon".equals(id)
                && plugin.getConfig().getBoolean("vanilla-events.blood-moon.invasion.enabled", true)) {
            int waveInterval = Math.max(10, Math.min(180,
                    plugin.getConfig().getInt("vanilla-events.blood-moon.invasion.wave-interval-seconds", 25)));
            nextInvasionWaveAt = System.currentTimeMillis() + waveInterval * 1000L;
        }

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
        if ("blood-moon".equals(finished)) cleanupBloodMoonInvasion();
        activeEventId = null;
        activeUntil = 0L;
        if (announce && finished != null) {
            Bukkit.broadcastMessage(plugin.color("&6&lHALLOWEEN &8» &7Událost &e"
                    + getDisplayName(finished) + " &7skončila."));
        }
        if (scheduleNext && isEnabled() && plugin.isEventEnabled()) scheduleNextEvent();
        else nextEventAt = 0L;
    }

    private void tickBloodMoonInvasion(long now) {
        if (activeEventId == null || !"blood-moon".equals(activeEventId)
                || !plugin.getConfig().getBoolean("vanilla-events.blood-moon.invasion.enabled", true)
                || !plugin.getConfig().getBoolean("special-mobs.enabled", true)
                || plugin.getMobManager() == null) return;

        if (nextInvasionWaveAt > 0L && now >= nextInvasionWaveAt) {
            spawnBloodMoonWave();
            int interval = Math.max(10, Math.min(180,
                    plugin.getConfig().getInt("vanilla-events.blood-moon.invasion.wave-interval-seconds", 25)));
            nextInvasionWaveAt = now + interval * 1000L;
        }

        if (invasionCaptainSpawned
                || !plugin.getConfig().getBoolean("vanilla-events.blood-moon.invasion.captain-enabled", true)) return;
        int finalSeconds = Math.max(15, Math.min(180, plugin.getConfig().getInt(
                "vanilla-events.blood-moon.invasion.captain-final-seconds", 60)));
        if (activeUntil - now > finalSeconds * 1000L) return;

        List<Player> eligiblePlayers = Bukkit.getOnlinePlayers().stream()
                .filter(plugin::isEligibleGameplayPlayer)
                .filter(player -> plugin.isEligibleGameplayWorld(player.getWorld()))
                .toList();
        if (eligiblePlayers.isEmpty()) return;
        Player target = eligiblePlayers.get(ThreadLocalRandom.current().nextInt(eligiblePlayers.size()));
        if (plugin.getMobManager().spawnInvasionCaptain(target)) {
            invasionCaptainSpawned = true;
            for (Player player : target.getWorld().getPlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)
                        || !plugin.isEligibleGameplayWorld(player.getWorld())) continue;
                player.sendMessage(plugin.color("&4&lKAPITÁN KRVAVÉ INVAZE &8» &cPoslední obrana Krvavého měsíce dorazila!"));
                player.playSound(player.getLocation(), "minecraft:entity.wither.spawn", SoundCategory.HOSTILE, 0.9F, 0.7F);
            }
        }
    }

    private void spawnBloodMoonWave() {
        int mobsPerWave = Math.max(1, Math.min(8,
                plugin.getConfig().getInt("vanilla-events.blood-moon.invasion.mobs-per-wave", 4)));
        int spawnedTotal = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!plugin.isEligibleGameplayWorld(world)) continue;
            var targets = world.getPlayers().stream()
                    .filter(plugin::isEligibleGameplayPlayer)
                    .toList();
            if (targets.isEmpty()) continue;

            int spawnedHere = 0;
            for (int i = 0; i < mobsPerWave; i++) {
                Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
                if (plugin.getMobManager().spawnEventMob(target)) {
                    spawnedHere++;
                    spawnedTotal++;
                }
            }
            if (spawnedHere > 0) {
                for (Player player : targets) {
                    player.sendMessage(plugin.color("&5&lKRVAVÝ MĚSÍC &8» &cDorazila vlna &e"
                            + (invasionWaveNumber + 1) + "&c! &7Nepřátelé se blíží z temnoty."));
                    player.getWorld().playSound(player.getLocation(), "minecraft:entity.phantom.flap",
                            SoundCategory.HOSTILE, 0.6F, 0.6F);
                }
            }
        }
        if (spawnedTotal > 0) invasionWaveNumber++;
    }

    private void cleanupBloodMoonInvasion() {
        if (plugin.getMobManager() != null) plugin.getMobManager().cleanupEventMobs();
        nextInvasionWaveAt = 0L;
        invasionWaveNumber = 0;
        invasionCaptainSpawned = false;
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
        if (!Double.isFinite(multiplier)) multiplier = 1.5D;
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
