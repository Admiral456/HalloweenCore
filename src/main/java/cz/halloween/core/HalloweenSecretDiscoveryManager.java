package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Locale;

public final class HalloweenSecretDiscoveryManager implements Listener {
    private final HalloweenCore plugin;

    public HalloweenSecretDiscoveryManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("secret-discoveries.enabled", true)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID())) {
            checkLocations(event.getPlayer(), to);
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) return;
        checkLocations(event.getPlayer(), to);
    }

    private void checkLocations(Player player, Location playerLocation) {
        if (player == null || !player.isOnline() || player.isDead()
                || !plugin.isEligibleGameplayPlayer(player)
                || !plugin.isEligibleGameplayWorld(player.getWorld())) return;
        ConfigurationSection locations = plugin.getConfig().getConfigurationSection("secret-discoveries.locations");
        if (locations == null) return;

        for (String id : locations.getKeys(false)) {
            String path = "secret-discoveries.locations." + id;
            if (!plugin.getConfig().getBoolean(path + ".configured", false)) continue;

            String worldName = plugin.getConfig().getString(path + ".world", "");
            World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
            if (world == null || !world.getUID().equals(playerLocation.getWorld().getUID())) continue;
            if (plugin.getDataManager().hasDiscoveredSecret(player.getUniqueId(), id)) continue;

            Location target = new Location(world,
                    plugin.getConfig().getDouble(path + ".x"),
                    plugin.getConfig().getDouble(path + ".y"),
                    plugin.getConfig().getDouble(path + ".z"));
            double radius = Math.max(1.0D,
                    plugin.getConfig().getDouble(path + ".radius-blocks",
                            plugin.getConfig().getDouble("secret-discoveries.default-radius-blocks", 4.5D)));
            if (playerLocation.distanceSquared(target) > radius * radius) continue;

            // Persist the flag before awarding fragments, so the same location cannot pay twice.
            if (!plugin.getDataManager().markSecretDiscovered(player.getUniqueId(), id)) continue;
            long reward = Math.max(1L,
                    plugin.getConfig().getLong(path + ".reward-fragments",
                            plugin.getConfig().getLong("secret-discoveries.default-reward-fragments", 250L)));
            plugin.getService().addFragments(player.getUniqueId(), reward, "secret-discovery");

            String name = plugin.getConfig().getString(path + ".name", id);
            player.sendTitle(plugin.color("&5&lTAJNÝ OBJEV"),
                    plugin.color("&d" + name + " &8• &e+" + reward + " fragmentů"), 8, 55, 14);
            player.sendMessage(plugin.color("&5HALLOWEEN &8» &fObjevil jsi skryté místo: &d" + name + "&f."));
            player.sendMessage(plugin.color("&7Tento objev už můžeš odměnou získat jen jednou."));
            player.playSound(playerLocation, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 0.65f);
            world.spawnParticle(Particle.SOUL, target.clone().add(0, 1.0D, 0),
                    28, 0.65D, 0.8D, 0.65D, 0.035D);
            world.spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 0.6D, 0),
                    36, 0.7D, 0.6D, 0.7D, 0.02D);
        }
    }

    public void showSecrets(Player player) {
        if (player == null || !player.isOnline()) return;
        ConfigurationSection locations = plugin.getConfig().getConfigurationSection("secret-discoveries.locations");
        if (locations == null) {
            player.sendMessage(plugin.color("&7Zatím nejsou připravena žádná tajná místa."));
            return;
        }

        int total = 0;
        int found = 0;
        for (String id : locations.getKeys(false)) {
            String path = "secret-discoveries.locations." + id;
            if (!plugin.getConfig().getBoolean(path + ".configured", false)) continue;
            total++;
            if (plugin.getDataManager().hasDiscoveredSecret(player.getUniqueId(), id)) found++;
        }

        player.sendMessage(plugin.color("&5&lTAJNÉ OBJEVY &8» &7Postup: &d" + found + " &8/ &d" + total));
        if (total == 0) {
            player.sendMessage(plugin.color("&7Tajemná místa ještě nebyla na tomto serveru umístěna."));
            return;
        }

        for (String id : locations.getKeys(false)) {
            String path = "secret-discoveries.locations." + id;
            if (!plugin.getConfig().getBoolean(path + ".configured", false)) continue;
            String name = plugin.getConfig().getString(path + ".name", id);
            if (plugin.getDataManager().hasDiscoveredSecret(player.getUniqueId(), id)) {
                player.sendMessage(plugin.color("&a✓ &f" + name + " &8— &aobjeveno"));
            } else {
                String hint = plugin.getConfig().getString(path + ".hint", "Nápověda zatím chybí.");
                player.sendMessage(plugin.color("&8? &f" + name + " &8— &7" + hint));
            }
        }
    }
}
