package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HalloweenActivityListener implements Listener {
    private static final long RATE_WINDOW_MILLIS = 60_000L;
    private static final long NOTICE_COOLDOWN_MILLIS = 20_000L;

    private final HalloweenCore plugin;
    private final Map<UUID, Map<String, Deque<Long>>> rewardTimes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastLimitNotice = new HashMap<>();

    public HalloweenActivityListener(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isEventEnabled()) return;

        Player player = event.getPlayer();
        if (!plugin.isEligibleGameplayPlayer(player)) return;
        if (!plugin.isEligibleGameplayWorld(player.getWorld())) return;
        Block block = event.getBlock();

        if (isConfiguredBlock(block.getType(), "rewards.mining.blocks")
                && plugin.getConfig().getBoolean("rewards.mining.enabled", true)) {
            long reward = plugin.getConfig().getLong("rewards.mining.fragments", 1L);
            if (allowFragmentReward(player, "mining")) {
                if (plugin.getQuestManager() != null) plugin.getQuestManager().recordAction(player, "mining");
                if (reward > 0L) plugin.getService().addFragments(player.getUniqueId(), reward, "mining");
            }
            // Do not allow a block configured for mining to fall through into the farming reward path.
            return;
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
        if (allowFragmentReward(player, "farming")) {
            if (plugin.getQuestManager() != null) plugin.getQuestManager().recordAction(player, "farming");
            if (reward > 0L) plugin.getService().addFragments(player.getUniqueId(), reward, "farming");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!plugin.isEventEnabled()) return;
        if (!plugin.isEligibleGameplayPlayer(event.getPlayer())) return;
        if (!plugin.isEligibleGameplayWorld(event.getPlayer().getWorld())) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (!plugin.getConfig().getBoolean("rewards.fishing.enabled", true)) return;

        long reward = plugin.getConfig().getLong("rewards.fishing.fragments", 2L);
        if (allowFragmentReward(event.getPlayer(), "fishing")) {
            if (plugin.getQuestManager() != null) plugin.getQuestManager().recordAction(event.getPlayer(), "fishing");
            if (reward > 0L) plugin.getService().addFragments(event.getPlayer().getUniqueId(), reward, "fishing");
        }
    }

    private boolean allowFragmentReward(Player player, String source) {
        String basePath = "rewards." + source;
        int limit = plugin.getConfig().getInt(basePath + ".max-fragment-rewards-per-minute", 0);
        if (limit <= 0) return true; // Backward-compatible: an omitted limit does not disable rewards.

        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Deque<Long> timestamps = rewardTimes
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(source, ignored -> new ArrayDeque<>());

        while (!timestamps.isEmpty() && timestamps.peekFirst() <= now - RATE_WINDOW_MILLIS) {
            timestamps.removeFirst();
        }

        if (timestamps.size() >= limit) {
            Map<String, Long> notices = lastLimitNotice.computeIfAbsent(playerId, ignored -> new HashMap<>());
            long lastNotice = notices.getOrDefault(source, 0L);
            if (now - lastNotice >= NOTICE_COOLDOWN_MILLIS) {
                notices.put(source, now);
                player.sendMessage(plugin.color("&cLimit fragmentů za tuto aktivitu je pro tuto chvíli vyčerpaný. &7Limit se obnovuje průběžně."));
            }
            return false;
        }

        timestamps.addLast(now);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        rewardTimes.remove(playerId);
        lastLimitNotice.remove(playerId);
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
