package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

public final class HalloweenVillageDiscoveryManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey structureTypeKey;
    private final NamespacedKey structureXKey;
    private final NamespacedKey structureYKey;
    private final NamespacedKey structureZKey;

    public HalloweenVillageDiscoveryManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.structureTypeKey = new NamespacedKey(plugin, "halloween_structure_type");
        this.structureXKey = new NamespacedKey(plugin, "halloween_structure_x");
        this.structureYKey = new NamespacedKey(plugin, "halloween_structure_y");
        this.structureZKey = new NamespacedKey(plugin, "halloween_structure_z");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.isEventEnabled()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;

        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isEligibleGameplayWorld(player.getWorld())) return;

        // Generated village/graveyard sites are discovered from chunk persistent data.
        // They need no admin-entered coordinates and remain discoverable after restart.
        if (discoverGeneratedStructure(player)) return;

        // Backwards compatibility for servers that already configured a manual village.
        if (!plugin.getConfig().getBoolean("haunted-village.enabled", false)
                || plugin.getDataManager().hasDiscoveredVillage(player.getUniqueId())) return;
        String worldName = plugin.getConfig().getString("haunted-village.world", "");
        World villageWorld = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (villageWorld == null || !player.getWorld().getUID().equals(villageWorld.getUID())) return;

        double x = plugin.getConfig().getDouble("haunted-village.x", 0.0D);
        double y = plugin.getConfig().getDouble("haunted-village.y", 100.0D);
        double z = plugin.getConfig().getDouble("haunted-village.z", 0.0D);
        double radius = Math.max(2.0D,
                plugin.getConfig().getDouble("haunted-village.discovery-radius", 12.0D));
        Location target = new Location(villageWorld, x, y, z);
        if (player.getLocation().distanceSquared(target) > radius * radius) return;
        rewardVillage(player, x, y, z);
    }

    private boolean discoverGeneratedStructure(Player player) {
        if (!plugin.getConfig().getBoolean("random-structures.enabled", true)) return false;
        World world = player.getWorld();
        double radius = Math.max(3.0D,
                plugin.getConfig().getDouble("random-structures.discovery-radius-blocks", 10.0D));
        int chunkRadius = Math.max(1, (int) Math.ceil(radius / 16.0D) + 1);
        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;
        Location playerLocation = player.getLocation();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                if (!world.isChunkLoaded(cx, cz)) continue;
                Chunk chunk = world.getChunkAt(cx, cz);
                var data = chunk.getPersistentDataContainer();
                String kind = data.get(structureTypeKey, PersistentDataType.STRING);
                if (kind == null) continue;

                Integer x = data.get(structureXKey, PersistentDataType.INTEGER);
                Integer y = data.get(structureYKey, PersistentDataType.INTEGER);
                Integer z = data.get(structureZKey, PersistentDataType.INTEGER);
                if (x == null || y == null || z == null) continue;
                Location location = new Location(world, x + 0.5D, y, z + 0.5D);
                if (playerLocation.distanceSquared(location) > radius * radius) continue;

                if (kind.equals("village") && !plugin.getDataManager().hasDiscoveredVillage(player.getUniqueId())) {
                    rewardVillage(player, x, y, z);
                    return true;
                }
                if (kind.equals("graveyard")) {
                    String discoveryId = "random-graveyard:" + world.getUID() + ":" + x + ":" + z;
                    if (!plugin.getDataManager().hasSecretDiscovery(player.getUniqueId(), discoveryId)) {
                        plugin.getDataManager().markSecretDiscovered(player.getUniqueId(), discoveryId);
                        long reward = Math.max(0L, plugin.getConfig().getLong(
                                "random-structures.graveyard-discovery-reward-fragments", 200L));
                        if (reward > 0L) plugin.getService().addFragments(player.getUniqueId(), reward, "graveyard-discovery");
                        plugin.getDataManager().save();
                        player.sendTitle(
                                plugin.color("&8&lPROKLETÝ HŘBITOV"),
                                plugin.color("&7Objeveno • &e+" + reward + " fragmentů"),
                                10, 60, 20
                        );
                        player.sendMessage(plugin.color("&6HALLOWEEN &8» &fNašel jsi náhodně vygenerovaný hřbitov. &e+" + reward + " &ffragmentů."));
                        player.playSound(player.getLocation(), "minecraft:block.amethyst_block.resonate", 0.9f, 0.65f);
                        world.spawnParticle(Particle.SOUL, playerLocation.clone().add(0, 1, 0),
                                24, 1.0D, 0.8D, 1.0D, 0.04D);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void rewardVillage(Player player, double x, double y, double z) {
        plugin.getDataManager().markVillageDiscovered(player.getUniqueId());
        long reward = Math.max(1L,
                plugin.getConfig().getLong("haunted-village.discovery-reward-fragments", 250L));
        plugin.getService().addFragments(player.getUniqueId(), reward, "village-discovery");
        plugin.getDataManager().save();

        player.sendTitle(
                plugin.color("&5&lHAUNTED VILLAGE"),
                plugin.color("&6Objeveno • &e+" + reward + " fragmentů"),
                10, 60, 20
        );
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &fNašel jsi náhodně vygenerovanou Haunted Village. &e+" + reward + " &ffragmentů."));
        player.playSound(player.getLocation(), "minecraft:block.amethyst_block.resonate", 0.9f, 0.7f);
        player.getWorld().spawnParticle(
                Particle.SOUL,
                player.getLocation().add(0, 1, 0),
                30,
                1.2D, 1.0D, 1.2D, 0.05D
        );
    }
}
