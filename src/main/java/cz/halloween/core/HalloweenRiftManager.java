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
    private long collapseStartedAtMillis;
    private boolean collapseBurstPlayed;

    public HalloweenRiftManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (visualTask != null) return;
        visualTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderRift, 5L, 5L);
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
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_AIR
                || !plugin.isEventEnabled()) return;
        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isEligibleGameplayWorld(player.getWorld())) return;

        // Intercept only the intended ritual item. Other right-click uses (food, pearls,
        // shields, etc.) must remain available while this chapter is active.
        if (plugin.getQuestManager().isActiveObjective(player, "vampire-awakening")
                && event.getItem() != null && event.getItem().getType() == Material.NETHER_STAR) {
            event.setCancelled(true);
            tryVampireAwakening(player, event.getItem());
            return;
        }
        if (!isConfigured() || !isNearRift(player.getLocation(), 0.0D)
                || !plugin.getQuestManager().isActiveObjective(player, "rift-seal")
                || event.getItem() == null || event.getItem().getType() != Material.ECHO_SHARD) return;
        event.setCancelled(true);
        if (!isNight(player.getWorld())) {
            player.sendMessage(plugin.color("&5HALLOWEEN &8» &cRituál funguje pouze v noci, kdy je spojení nejsilnější."));
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.ECHO_SHARD), 8)
                || !player.getInventory().containsAtLeast(new ItemStack(Material.CRYING_OBSIDIAN), 2)) {
            player.sendMessage(plugin.color("&5HALLOWEEN &8» &7Potřebuješ &d8 Echo Shardů &7a &52 Crying Obsidiany&7."));
            return;
        }
        removeMaterial(player, Material.ECHO_SHARD, 8);
        removeMaterial(player, Material.CRYING_OBSIDIAN, 2);
        playSealEffect(player);
        plugin.getQuestManager().recordAction(player, "rift-seal");
    }

    private void tryVampireAwakening(Player player, ItemStack heldItem) {
        if (heldItem == null || heldItem.getType() != Material.NETHER_STAR) {
            player.sendMessage(plugin.color("&4HALLOWEEN &8» &cDrž v hlavní ruce Nether Star a klikni pravým do vzduchu u arény."));
            return;
        }
        Location arena = getVampireArenaLocation();
        if (arena == null) {
            player.sendMessage(plugin.color("&4HALLOWEEN &8» &cUpíří aréna není nastavená. Správce musí použít /halloween setvampirearena."));
            return;
        }
        double radius = Math.max(8.0D, plugin.getConfig().getDouble("story-rift.arena-ritual-radius-blocks", 24.0D));
        Location here = player.getLocation();
        if (here.getWorld() == null || !here.getWorld().getUID().equals(arena.getWorld().getUID())
                || Math.pow(here.getX() - arena.getX(), 2.0D) + Math.pow(here.getZ() - arena.getZ(), 2.0D) > radius * radius
                || Math.abs(here.getY() - arena.getY()) > 48.0D) {
            player.sendMessage(plugin.color("&4HALLOWEEN &8» &cRituál lze provést pouze uvnitř nastavené upíří arény."));
            return;
        }
        if (plugin.getVampireEncounterManager().isActive()) {
            player.sendMessage(plugin.color("&4HALLOWEEN &8» &7Král upírů už bojuje. Pomoz ostatním v aréně!"));
            return;
        }
        if (plugin.getDataManager().isVampireDefeated()) {
            player.sendMessage(plugin.color("&4HALLOWEEN &8» &cKrál upírů už byl poražen; tento jednorázový rituál nelze znovu spustit. Suroviny nebyly odebrány."));
            return;
        }
        if (!plugin.getBossManager().isVampireConfiguredReady()) {
            player.sendMessage(plugin.color("&4HALLOWEEN &8» &cKrál upírů zatím není připravený. Suroviny nebyly odebrány."));
            player.sendMessage(plugin.color("&7Správce musí zkontrolovat /halloween debug a nastavit bosse, arénu, MythicMobs/ModelEngine, model.ready a požadovaný serverový progress."));
            return;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(Material.ECHO_SHARD), 8)
                || !player.getInventory().containsAtLeast(new ItemStack(Material.CRYING_OBSIDIAN), 4)
                || !player.getInventory().containsAtLeast(new ItemStack(Material.GHAST_TEAR), 4)
                || !player.getInventory().containsAtLeast(new ItemStack(Material.NETHER_STAR), 1)) {
            player.sendMessage(plugin.color("&4HALLOWEEN &8» &cChybí ti obětní suroviny pro probuzení Krále upírů."));
            player.sendMessage(plugin.color("&7Potřebuješ: &d8 Echo Shardů&7, &54 Crying Obsidian&7, &e4 Ghast Tears &7a &61 Nether Star&7."));
            return;
        }
        removeMaterial(player, Material.ECHO_SHARD, 8);
        removeMaterial(player, Material.CRYING_OBSIDIAN, 4);
        removeMaterial(player, Material.GHAST_TEAR, 4);
        removeMaterial(player, Material.NETHER_STAR, 1);
        playVampireAwakeningEffect(arena);
        plugin.getQuestManager().recordAction(player, "vampire-awakening");
    }

    private Location getVampireArenaLocation() {
        if (!plugin.getConfig().getBoolean("bosses.vampire.arena.configured", false)) return null;
        String worldName = plugin.getConfig().getString("bosses.vampire.arena.world", "");
        World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, plugin.getConfig().getDouble("bosses.vampire.arena.x"),
                plugin.getConfig().getDouble("bosses.vampire.arena.y"),
                plugin.getConfig().getDouble("bosses.vampire.arena.z"));
    }

    private void playVampireAwakeningEffect(Location arena) {
        World world = arena.getWorld();
        Location center = arena.clone().add(0.0D, 1.5D, 0.0D);
        world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticle(Particle.EXPLOSION, center, 20, 5.0D, 1.5D, 5.0D, 0.08D);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 260, 4.0D, 2.5D, 4.0D, 0.18D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 150, 3.0D, 2.0D, 3.0D, 0.06D);
        world.spawnParticle(Particle.DRAGON_BREATH, center, 100, 4.0D, 1.8D, 4.0D, 0.025D);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 100, 4.0D, 2.0D, 4.0D, 0.03D);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 4.0f, 0.6f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.65f);
        Bukkit.broadcastMessage(plugin.color("&4&lHALLOWEEN &8» &cV upíří aréně byl proveden obětní rituál. Král upírů se probouzí."));
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
        Location center = rift.clone().add(0.0D, 1.5D, 0.0D);
        int collapseSeconds = Math.max(15, Math.min(600, plugin.getConfig().getInt("story-rift.collapse-seconds", 60)));
        long now = System.currentTimeMillis();
        sealedUntilMillis = now + collapseSeconds * 1000L;
        collapseStartedAtMillis = now;
        collapseBurstPlayed = false;
        world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        world.spawnParticle(Particle.EXPLOSION, center, 14, 1.5D, 1.6D, 1.5D, 0.08D);
        world.spawnParticle(Particle.REVERSE_PORTAL, center, 200, 1.8D, 2.1D, 1.8D, 0.14D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 100, 1.5D, 1.8D, 1.5D, 0.05D);
        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.7f);
        world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0f, 0.6f);
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.7f, 0.6f);
        player.sendTitle(plugin.color("&5&lTRHLINA SE HROUTÍ"),
                plugin.color("&7Prostor se láme a vtahuje vše dovnitř…"), 5, 70, 20);
        player.sendMessage(plugin.color("&5HALLOWEEN &8» &dSpustil jsi kolaps trhliny. Portál se stáhne do sebe a na chvíli zmizí."));
    }

    private void renderRift() {
        animationTick++;
        if (!plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("story-rift.visuals-enabled", true)) return;
        Location rift = getRiftLocation();
        if (rift == null) return;
        World world = rift.getWorld();
        if (world == null || !world.isChunkLoaded(rift.getBlockX() >> 4, rift.getBlockZ() >> 4)) return;

        long now = System.currentTimeMillis();
        if (collapseStartedAtMillis > 0L) {
            long duration = Math.max(2500L, Math.min(8000L,
                    Math.round(plugin.getConfig().getDouble("story-rift.collapse-animation-seconds", 4.5D) * 1000.0D)));
            long elapsed = now - collapseStartedAtMillis;
            if (elapsed < duration) {
                renderCollapse(world, rift, elapsed, duration);
                return;
            }
            collapseStartedAtMillis = 0L;
            if (now < sealedUntilMillis) return;
        } else if (now < sealedUntilMillis) return;

        double phase = animationTick * 0.16D;
        Location center = rift.clone().add(0.0D, 1.5D, 0.0D);
        for (int i = 0; i < 32; i++) {
            double angle = i * (Math.PI * 2.0D / 32.0D) + phase;
            double x = center.getX() + Math.cos(angle) * (0.8D + 0.10D * Math.sin(phase + i));
            double y = center.getY() + Math.sin(angle) * 1.45D;
            double z = center.getZ() + Math.sin(angle * 2.0D + phase) * 0.18D;
            Location point = new Location(world, x, y, z);
            world.spawnParticle(Particle.REVERSE_PORTAL, point, 2, 0.025D, 0.035D, 0.025D, 0.01D);
            if (i % 3 == 0) world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.02D, 0.04D, 0.02D, 0.002D);
            if (i % 4 == 0) world.spawnParticle(Particle.END_ROD, point, 1, 0.015D, 0.025D, 0.015D, 0.0D);
        }
        world.spawnParticle(Particle.PORTAL, center, 24, 0.18D, 0.65D, 0.18D, 0.13D);
        world.spawnParticle(Particle.DRAGON_BREATH, center, 5, 0.16D, 0.55D, 0.16D, 0.015D);
    }

    private void renderCollapse(World world, Location rift, long elapsed, long duration) {
        double progress = Math.max(0.0D, Math.min(1.0D, (double) elapsed / duration));
        double shrink = 1.0D - progress;
        double phase = animationTick * 0.42D;
        Location center = rift.clone().add(0.0D, 1.5D, 0.0D);
        for (int i = 0; i < 40; i++) {
            double angle = i * (Math.PI * 2.0D / 40.0D) + phase * (1.0D + progress * 3.0D);
            double radiusX = (0.95D + 0.25D * Math.sin(phase + i)) * shrink;
            double radiusY = 1.55D * shrink;
            Location point = new Location(world,
                    center.getX() + Math.cos(angle) * radiusX,
                    center.getY() + Math.sin(angle) * radiusY,
                    center.getZ() + Math.sin(angle * 2.0D + phase) * 0.20D * shrink);
            world.spawnParticle(Particle.REVERSE_PORTAL, point, 3, 0.025D, 0.035D, 0.025D, 0.04D + progress * 0.10D);
            if (i % 2 == 0) world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0.02D, 0.03D, 0.02D, 0.01D);
            if (progress > 0.45D && i % 3 == 0) world.spawnParticle(Particle.DRAGON_BREATH, point, 1, 0.04D, 0.05D, 0.04D, 0.01D);
        }
        world.spawnParticle(Particle.SOUL, center, 24, 0.18D * shrink, 0.55D * shrink, 0.18D * shrink, 0.02D);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 9, 0.28D, 0.50D, 0.28D, 0.025D);
        if (progress >= 0.88D && !collapseBurstPlayed) {
            collapseBurstPlayed = true;
            world.spawnParticle(Particle.SONIC_BOOM, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            world.spawnParticle(Particle.EXPLOSION, center, 25, 0.35D, 0.45D, 0.35D, 0.12D);
            world.spawnParticle(Particle.FLASH, center, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 90, 0.35D, 0.75D, 0.35D, 0.13D);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.4f, 0.55f);
            world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 0.55f);
        }
    }
}
