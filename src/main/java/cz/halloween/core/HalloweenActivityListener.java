package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HalloweenActivityListener implements Listener {
    private final HalloweenCore plugin;

    public HalloweenActivityListener(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isEventEnabled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (isConfiguredBlock(block.getType(), "rewards.mining.blocks")
                && plugin.getConfig().getBoolean("rewards.mining.enabled", true)) {
            long reward = plugin.getConfig().getLong("rewards.mining.fragments", 1L);
            if (reward > 0L) {
                plugin.getService().addFragments(player.getUniqueId(), reward, "mining");
                return;
            }
        }

        if (!isConfiguredBlock(block.getType(), "rewards.farming.blocks")
                || !plugin.getConfig().getBoolean("rewards.farming.enabled", true)) {
            return;
        }

        if (plugin.getConfig().getBoolean("rewards.farming.require-mature", true)
                && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }

        long reward = plugin.getConfig().getLong("rewards.farming.fragments", 2L);
        if (reward > 0L) {
            plugin.getService().addFragments(player.getUniqueId(), reward, "farming");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!plugin.isEventEnabled()) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!plugin.getConfig().getBoolean("rewards.fishing.enabled", true)) return;

        long reward = plugin.getConfig().getLong("rewards.fishing.fragments", 2L);
        if (reward <= 0L) return;

        plugin.getService().addFragments(event.getPlayer().getUniqueId(), reward, "fishing");
    }

    private boolean isConfiguredBlock(Material material, String path) {
        List<String> names = plugin.getConfig().getStringList(path);
        if (names.isEmpty()) return false;

        Set<String> configured = new HashSet<>();
        for (String name : names) {
            configured.add(name.toUpperCase(Locale.ROOT));
        }
        return configured.contains(material.name());
    }
}
