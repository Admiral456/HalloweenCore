package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class HalloweenGraveyardManager implements Listener {
    private final HalloweenCore plugin;

    public HalloweenGraveyardManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public int getConfiguredGraveyardCount() {
        ConfigurationSection sites = plugin.getConfig().getConfigurationSection("story-graveyards.locations");
        if (sites == null || !plugin.getConfig().getBoolean("story-graveyards.enabled", true)) return 0;
        int count = 0;
        for (String id : sites.getKeys(false)) {
            String path = "story-graveyards.locations." + id;
            String worldName = plugin.getConfig().getString(path + ".world", "");
            if (plugin.getConfig().getBoolean(path + ".configured", false)
                    && worldName != null && Bukkit.getWorld(worldName) != null) count++;
        }
        return count;
    }

    public boolean isAtGraveyard(Location location) {
        return findGraveyardId(location) != null;
    }

    public String findGraveyardId(Location location) {
        if (location == null || location.getWorld() == null
                || !plugin.getConfig().getBoolean("story-graveyards.enabled", true)) return null;
        ConfigurationSection sites = plugin.getConfig().getConfigurationSection("story-graveyards.locations");
        if (sites == null) return null;

        String selected = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (String id : sites.getKeys(false)) {
            String path = "story-graveyards.locations." + id;
            if (!plugin.getConfig().getBoolean(path + ".configured", false)) continue;
            String worldName = plugin.getConfig().getString(path + ".world", "");
            World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
            if (world == null || !world.getUID().equals(location.getWorld().getUID())) continue;
            double radius = Math.max(8.0D, plugin.getConfig().getDouble(path + ".radius-blocks",
                    plugin.getConfig().getDouble("story-graveyards.default-radius-blocks", 32.0D)));
            double dx = location.getX() - plugin.getConfig().getDouble(path + ".x");
            double dz = location.getZ() - plugin.getConfig().getDouble(path + ".z");
            double dy = Math.abs(location.getY() - plugin.getConfig().getDouble(path + ".y"));
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= radius * radius && dy <= Math.max(48.0D, radius)
                    && distanceSquared < bestDistanceSquared) {
                selected = id;
                bestDistanceSquared = distanceSquared;
            }
        }
        return selected;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("story-graveyards.enabled", true)) return;
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) return;
        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isEligibleGameplayWorld(player.getWorld())
                || !plugin.getQuestManager().isActiveObjective(player, "graveyard-survey")) return;
        if (plugin.getConfig().getBoolean("story-graveyards.survey-at-night", true)
                && !plugin.getRiftManager().isNight(player.getWorld())) return;

        String id = findGraveyardId(to);
        if (id == null || !plugin.getDataManager().markStoryGraveyardVisited(player.getUniqueId(), id)) return;
        int visited = plugin.getDataManager().getStoryGraveyardVisits(player.getUniqueId()).size();
        String name = plugin.getConfig().getString("story-graveyards.locations." + id + ".name",
                id.replace('-', ' ').replace('_', ' '));
        plugin.getQuestManager().recordAction(player, "graveyard-survey");
        player.sendMessage(plugin.color("&8HALLOWEEN &7» &fZaznamenal jsi hřbitov: &d" + name + " &8(" + visited + "/3)"));
        plugin.getDataManager().save();
    }
}
