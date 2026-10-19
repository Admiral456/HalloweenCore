package cz.halloween.core;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla-only ambient cues. It intentionally uses only built-in sounds and
 * particles; no resource pack, custom audio, shader, or model is required.
 */
public final class HalloweenVanillaAtmosphereManager {
    private final HalloweenCore plugin;
    private BukkitTask task;
    private long nextPulseAt;

    public HalloweenVanillaAtmosphereManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vanilla-atmosphere.enabled", true);
    }

    public boolean isRunning() {
        return task != null;
    }

    public void start() {
        if (task != null || !isEnabled() || !plugin.isEventEnabled()) return;
        scheduleNextPulse();
        // Check every five seconds; actual ambient cues are separated by a much
        // longer randomized interval, keeping the main-thread work predictable.
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 100L);
        plugin.getLogger().info("Vanilla Halloween ambience scheduled.");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        nextPulseAt = 0L;
    }

    public void restart() {
        stop();
        start();
    }

    private void tick() {
        if (!isEnabled() || !plugin.isEventEnabled()) {
            stop();
            return;
        }

        long now = System.currentTimeMillis();
        if (nextPulseAt <= 0L || now < nextPulseAt) return;
        emitAmbientCues();
        scheduleNextPulse();
    }

    private void scheduleNextPulse() {
        int minSeconds = boundedInt("vanilla-atmosphere.min-interval-seconds", 90, 30, 600);
        int maxSeconds = boundedInt("vanilla-atmosphere.max-interval-seconds", 180, minSeconds, 600);
        if (maxSeconds < minSeconds) maxSeconds = minSeconds;
        int delay = ThreadLocalRandom.current().nextInt(minSeconds, maxSeconds + 1);
        nextPulseAt = System.currentTimeMillis() + delay * 1000L;
    }

    private void emitAmbientCues() {
        double configuredChance = plugin.getConfig().getDouble("vanilla-atmosphere.player-chance", 0.35D);
        double chance = Double.isFinite(configuredChance)
                ? Math.max(0.0D, Math.min(1.0D, configuredChance))
                : 0.35D;
        boolean particlesEnabled = plugin.getConfig().getBoolean("vanilla-atmosphere.particles-enabled", true);
        boolean soundsEnabled = plugin.getConfig().getBoolean("vanilla-atmosphere.sounds-enabled", true);
        if (!particlesEnabled && !soundsEnabled) return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)
                    || !plugin.isEligibleGameplayWorld(player.getWorld())
                    || ThreadLocalRandom.current().nextDouble() >= chance) {
                continue;
            }

            Location cueLocation = nearbyLocation(player);
            World world = player.getWorld();
            if (particlesEnabled) {
                world.spawnParticle(Particle.ASH, cueLocation, 5, 0.28D, 0.35D, 0.28D, 0.005D);
                if (ThreadLocalRandom.current().nextBoolean()) {
                    world.spawnParticle(Particle.SOUL, cueLocation, 2, 0.18D, 0.25D, 0.18D, 0.01D);
                }
            }

            if (soundsEnabled) {
                String sound = chooseSound(world);
                float volume = boundedFloat("vanilla-atmosphere.sound-volume", 0.55F, 0.0F, 1.5F);
                float pitch = (float) ThreadLocalRandom.current().nextDouble(0.65D, 1.05D);
                player.playSound(cueLocation, sound, SoundCategory.AMBIENT, volume, pitch);
            }
        }
    }

    private Location nearbyLocation(Player player) {
        Location origin = player.getLocation();
        double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
        double distance = ThreadLocalRandom.current().nextDouble(5.0D, 10.0D);
        double yOffset = ThreadLocalRandom.current().nextDouble(-1.0D, 2.0D);
        return origin.clone().add(Math.cos(angle) * distance, yOffset, Math.sin(angle) * distance);
    }

    private String chooseSound(World world) {
        long time = world.getTime();
        boolean night = time >= 13_000L && time <= 23_000L;
        if (night && ThreadLocalRandom.current().nextBoolean()) {
            return "minecraft:entity.phantom.ambient";
        }
        if (ThreadLocalRandom.current().nextInt(4) == 0) {
            return "minecraft:entity.witch.ambient";
        }
        return "minecraft:ambient.cave";
    }

    private int boundedInt(String path, int fallback, int min, int max) {
        int configured = plugin.getConfig().getInt(path, fallback);
        return Math.max(min, Math.min(max, configured));
    }

    private float boundedFloat(String path, float fallback, float min, float max) {
        double configured = plugin.getConfig().getDouble(path, fallback);
        if (!Double.isFinite(configured)) return fallback;
        return (float) Math.max(min, Math.min(max, configured));
    }
}
