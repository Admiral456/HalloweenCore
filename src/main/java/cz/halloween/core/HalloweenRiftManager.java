package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Renders the story rift and handles the real investigation/sealing conditions.
 * The rift is shared scenery, but story progress and the final ritual are per-player.
 */
public final class HalloweenRiftManager implements Listener {
    private final HalloweenCore plugin;
    private BukkitTask visualTask;
    private long animationTick;

    public HalloweenRiftManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (visualTask != null) return;
        visualTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderRift, 10L, 10L);
    }

    public void stop() {
        if (visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
    }

    public boolean isConfigured() {
        if (!plugin.getConfig().getBoolean("story-rift.enabled", true)
                || !plugin.getConfig().getBoolean("story-rift.configured", false)) return false;
        String worldName = plugin.getConfig().getString("story-rift.world", "");
        return worldName != null && !worldName.isBlank() && Bukkit.getWorld(worldName) != null;
    }

    public Location getRiftLocation() {
        if (!isConfigured()) return null;
        String worldName = plugin.getConfig().getString("story-rift.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                plugin.getConfig().getDouble("story-rift.x"),
                plugin.getConfig().getDouble("story-rift.y"),
                plugin.getConfig().getDouble("story-rift.z"));
    }

    public boolean isNight(World world) {
        if (world == null) return false;
        long time = world.getTime() % 24000L;
        return time >= 13000L && time <= 23000L;
    }

    public boolean isNearRift(Location location, double extraRadius) {
        Location rift = getRiftLocation();
        if (location == null || rift == null || location.getWorld() == null
                || !location.getWorld().getUID().equals(rift.getWorld().getUID())) return false;

        double radius = Math.max(1.0D, plugin.getConfig().getDouble("story-rift.radius-blocks", 5.0D)
                + Math.max(0.0D, extraRadius));
        double dx = location.getX() - rift.getX();
        double dz = location.getZ() - rift.getZ();
        double dy = Math.abs(location.getY() - rift.getY());
        return dx * dx + dz * dz <= radius * radius && dy <= radius + 3.0D;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.isEventEnabled() || !isConfigured()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;

        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player)
                || !plugin.isEligibleGameplayWorld(player.getWorld())
                || !isNight(player.getWorld())
                || !isNearRift(to, 0.0D)) return;

        // Visiting the configured rift at night is a genuine story objective.
        plugin.getQuestManager().recordAction(player, "rift-investigation");
    }

    public void playSealEffect(Player player) {
        if (player == null) return;
        Location rift = getRiftLocation();
        if (rift == null) return;

        World world = rift.getWorld();
        Location center = rift.clone().add(0.0D, 1.2D, 0.0D);
        world.spawnParticle(Particle.EXPLOSION, center, 2, 0.35D, 0.55D, 0.35D, 0.0D);
        world.spawnParticle(Particle.SOUL, center, 70, 0.8D, 1.2D, 0.8D, 0.04D);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 100, 1.0D, 1.4D, 1.0D, 0.08D);
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.65f);
        player.sendTitle(plugin.color("&5&lTRHLINA OSLABLA"),
                plugin.color("&7Její spojení bylo přerušeno."), 10, 70, 20);
        player.sendMessage(plugin.color("&5HALLOWEEN &8» &dTrhlina kolem tebe na okamžik vyšlehne a její spojení se přeruší."));
        player.sendMessage(plugin.color("&7Její ozvěna zůstává pro další lovce, kteří ještě neuzavřeli svůj příběh."));
    }

    private void renderRift() {
        animationTick++;
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("story-rift.visuals-enabled", true)) return;
        Location rift = getRiftLocation();
        if (rift == null) return;

        World world = rift.getWorld();
        double phase = animationTick * 0.12D;
        for (int i = 0; i < 16; i++) {
            double angle = i * (Math.PI * 2.0D / 16.0D) + phase;
            double radius = 0.65D + 0.12D * Math.sin(phase * 1.7D + i);
            double x = rift.getX() + Math.cos(angle) * radius;
            double z = rift.getZ() + Math.sin(angle) * radius;
            double y = rift.getY() + 0.35D + ((i % 6) * 0.38D);
            Location point = new Location(world, x, y, z);
            world.spawnParticle(Particle.REVERSE_PORTAL, point, 2, 0.04D, 0.06D, 0.04D, 0.01D);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.02D, 0.04D, 0.02D, 0.005D);
            }
        }
        world.spawnParticle(Particle.PORTAL, rift.clone().add(0.0D, 1.1D, 0.0D),
                18, 0.25D, 0.75D, 0.25D, 0.12D);
    }
}
