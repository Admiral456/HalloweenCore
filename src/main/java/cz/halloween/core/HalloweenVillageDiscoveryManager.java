package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HalloweenVillageDiscoveryManager implements Listener {
    private final HalloweenCore plugin;

    public HalloweenVillageDiscoveryManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.isEventEnabled()) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player)) return;
        if (!plugin.isEligibleGameplayWorld(player.getWorld())) return;
        if (plugin.getDataManager().hasDiscoveredVillage(player.getUniqueId())) return;

        double radius = Math.max(2.0D,
                plugin.getConfig().getDouble("haunted-village.discovery-radius", 12.0D));
        List<Location> villageLocations = new ArrayList<>();

        if (plugin.getConfig().getBoolean("haunted-village.enabled", false)) {
            String worldName = plugin.getConfig().getString("haunted-village.world", "");
            World villageWorld = Bukkit.getWorld(worldName);
            if (villageWorld != null && player.getWorld().getUID().equals(villageWorld.getUID())) {
                villageLocations.add(new Location(villageWorld,
                        plugin.getConfig().getDouble("haunted-village.x", 0.0D),
                        plugin.getConfig().getDouble("haunted-village.y", 100.0D),
                        plugin.getConfig().getDouble("haunted-village.z", 0.0D)));
            }
        }

        // Automatically generated hamlets are indexed only after their build completes.
        // New-format index: world UUID;center X;player-level Y;center Z;structure ID.
        for (String entry : plugin.getConfig().getStringList("world-generation.generated-structure-index")) {
            String[] parts = entry.split(";");
            if (parts.length != 5 || !parts[4].equalsIgnoreCase("AUTO_VILLAGE")) continue;
            try {
                UUID worldId = UUID.fromString(parts[0]);
                if (!player.getWorld().getUID().equals(worldId)) continue;
                World world = Bukkit.getWorld(worldId);
                if (world == null) continue;
                double x = Double.parseDouble(parts[1]) + 0.5D;
                double y = Double.parseDouble(parts[2]);
                double z = Double.parseDouble(parts[3]) + 0.5D;
                villageLocations.add(new Location(world, x, y, z));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring malformed automatic village index entry: " + entry);
            }
        }

        boolean isNearVillage = villageLocations.stream()
                .anyMatch(location -> player.getLocation().distanceSquared(location) <= radius * radius);
        if (!isNearVillage) return;

        plugin.getDataManager().markVillageDiscovered(player.getUniqueId());

        long reward = Math.max(1L,
                plugin.getConfig().getLong("haunted-village.discovery-reward-fragments", 150L));
        plugin.getService().addFragments(player.getUniqueId(), reward, "village-discovery");
        plugin.getDataManager().save();

        player.sendTitle(
                plugin.color("&5&lHAUNTED VILLAGE"),
                plugin.color("&6Objeveno • &e+" + reward + " fragmentů"),
                10, 60, 20
        );
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &fNašel jsi Haunted Village. &e+" + reward + " &ffragmentů."));
        player.playSound(player.getLocation(), "minecraft:block.amethyst_block.resonate", 0.9f, 0.7f);
        player.getWorld().spawnParticle(
                Particle.SOUL,
                player.getLocation().add(0, 1, 0),
                30,
                1.2D, 1.0D, 1.2D, 0.05D
        );
    }
}
