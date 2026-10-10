package cz.halloween.core;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class HalloweenVillageDiscoveryManager implements Listener {
    private final HalloweenCore plugin;

    public HalloweenVillageDiscoveryManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("haunted-village.enabled", true)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;

        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isEligibleGameplayWorld(player.getWorld())) return;
        if (plugin.getDataManager().hasDiscoveredVillage(player.getUniqueId())) return;

        double radius = Math.max(4.0D, plugin.getConfig().getDouble("haunted-village.discovery-radius", 12.0D));
        HalloweenStructureGenerator generator = plugin.getStructureGenerator();
        if (generator == null || !generator.isGeneratedVillageNear(to, radius)) return;

        plugin.getDataManager().markVillageDiscovered(player.getUniqueId());
        long reward = Math.max(1L, plugin.getConfig().getLong("haunted-village.discovery-reward-fragments", 150L));
        plugin.getService().addFragments(player.getUniqueId(), reward, "village-discovery");
        plugin.getDataManager().save();

        player.sendTitle(plugin.color("&5&lHAUNTED VILLAGE"),
                plugin.color("&6Objeveno • &e+" + reward + " fragmentů"), 10, 60, 20);
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &fObjevil jsi náhodně vygenerovanou Haunted Village. &e+" + reward + " &ffragmentů."));
        player.playSound(player.getLocation(), "minecraft:block.amethyst_block.resonate", 0.9f, 0.7f);
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0),
                30, 1.2D, 1.0D, 1.2D, 0.05D);
    }
}
