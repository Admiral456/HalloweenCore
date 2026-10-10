package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Renders the configured story rift and connects it to two real story actions:
 * investigating it at night and sealing a player's connection after the final hunt.
 * It deliberately never loads the rift chunk just to draw particles.
 */
public final class HalloweenRiftManager implements Listener {
    private final HalloweenCore plugin;
    private final Set<UUID> playersInsideRift = new HashSet<>();
    private BukkitTask visualTask;
    private long animationTick;
    private long sealedUntilMillis;

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
        playersInsideRift.clear();
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
        World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
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
        UUID playerId = player.getUniqueId();
        if (!isNearRift(to, 0.0D)) {
            playersInsideRift.remove(playerId);
            return;
        }

        if (!plugin.isEligibleGameplayPlayer(player)
                || !plugin.isEligibleGameplayWorld(player.getWorld())
                || !isNight(player.getWorld())
                || !plugin.getQuestManager().isActiveObjective(player, "rift-investigation")) return;

        // One discovery per entry. The objective is a single, real visit to the
        // configured location at night, not a counter that grows with movement.
        if (playersInsideRift.add(playerId)) {
            plugin.getQuestManager().recordAction(player, "rift-investigation");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRiftSealAttempt(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                || !plugin.isEventEnabled()) return;

        Player player = event.getPlayer();
        if (!isConfigured() || !isNearRift(player.getLocation(), 0.0D)
                || !plugin.getQuestManager().isActiveObjective(player, "rift-seal")
                || event.getItem() == null || event.getItem().getType() != Material.ECHO_SHARD) return;

        event.setCancelled(true);
        if (!plugin.isEligibleGameplayPlayer(player)
                || !plugin.isEligibleGameplayWorld(player.getWorld())) return;

        if (!isNight(player.getWorld())) {
            player.sendMessage(plugin.color("&5HALLOWEEN &8» &cRituál funguje pouze v noci, kdy je spojení nejsilnější."));
            return;
        }

        if (!player.getInventory().containsAtLeast(new ItemStack(Material.ECHO_SHARD), 4)
                || !player.getInventory().containsAtLeast(new ItemStack(Material.CRYING_OBSIDIAN), 1)) {
            player.sendMessage(plugin.color("&5HALLOWEEN &8» &7K uzavření potřebuješ &d4 Echo Shardy &7a &51 Crying Obsidian&7."));
            player.sendMessage(plugin.color("&7Drž Echo Shard v hlavní ruce a klikni pravým do vzduchu přímo u trhliny."));
            return;
        }

        removeMaterial(player, Material.ECHO_SHARD, 4);
        removeMaterial(player, Material.CRYING_OBSIDIAN, 1);
        playSealEffect(player);
        plugin.getQuestManager().recordAction(player, "rift-seal");
    }

    private void removeMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack == null || stack.getType() != material) continue;

            int removed = Math.min(stack.getAmount(), remaining);
            remaining -= removed;
            int left = stack.getAmount() - removed;
            if (left <= 0) player.getInventory().setItem(slot, null);
            else stack.setAmount(left);
        }
        player.updateInventory();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersInsideRift.remove(event.getPlayer().getUniqueId());
    }

    public void playSealEffect(Player player) {
        Location rift = getRiftLocation();
        if (rift == null) return;

        World world = rift.getWorld();
        Location center = rift.clone().add(0.0D, 1.2D, 0.0D);
        int collapseSeconds = Math.max(5, Math.min(300,
                plugin.getConfig().getInt("story-rift.collapse-seconds", 45)));
        sealedUntilMillis = System.currentTimeMillis() + collapseSeconds * 1000L;

        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.35D, 0.55D, 0.35D, 0.0D);
        world.spawnParticle(Particle.SOUL, center, 80, 0.8D, 1.2D, 0.8D, 0.04D);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 120, 1.0D, 1.4D, 1.0D, 0.08D);
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.65f);
        player.sendTitle(plugin.color("&5&lTRHLINA UZAVŘENA"),
                plugin.color("&7Její spojení s tebou bylo přerušeno."), 10, 70, 20);
        player.sendMessage(plugin.color("&5HALLOWEEN &8» &dTrhlina vyšlehne a na okamžik se zhroutí do sebe."));
        player.sendMessage(plugin.color("&7Tvoje spojení je přerušeno. Po chvíli se ozvěna vrátí, aby ji mohli uzavřít i další lovci."));
    }

    private void renderRift() {
        animationTick++;
        if (!plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("story-rift.visuals-enabled", true)
                || System.currentTimeMillis() < sealedUntilMillis) return;

        Location rift = getRiftLocation();
        if (rift == null) return;

        World world = rift.getWorld();
        if (world == null || !world.isChunkLoaded(rift.getBlockX() >> 4, rift.getBlockZ() >> 4)) return;

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
