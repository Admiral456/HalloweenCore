package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
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

    @EventHandler
    public void onMobKill(EntityDeathEvent event) {
        if (!plugin.isEventEnabled()) return;
        // Player-versus-player deaths must never mint gameplay currency.
        if (event.getEntity() instanceof Player) return;
        if (plugin.getVampireEncounterManager() != null
                && plugin.getVampireEncounterManager().isTrackedVampireBoss(event.getEntity())) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null
                || !plugin.isEligibleGameplayPlayer(killer)
                || !plugin.isEligibleGameplayWorld(killer.getWorld())
                || !plugin.getConfig().getBoolean("rewards.mob-kill.enabled", true)) return;

        long reward = plugin.getConfig().getLong("rewards.mob-kill.fragments", 1L);
        if (reward <= 0L || !allowFragmentReward(killer, "mob-kill")) return;

        if (plugin.getQuestManager() != null) {
            plugin.getQuestManager().recordAction(killer, "mob-kill");
        }
        plugin.getService().addFragments(killer.getUniqueId(), reward, "mob-kill");
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
        // Old server configs may not yet contain the newly introduced mob-kill limit.
        // Keep legacy sources unlimited when their limit is omitted, but never let
        // a missing mob-kill key silently bypass the new anti-farming guard.
        int fallbackLimit = "mob-kill".equalsIgnoreCase(source) ? 60 : 0;
        int limit = plugin.getConfig().getInt(basePath + ".max-fragment-rewards-per-minute", fallbackLimit);
        if (limit <= 0) return true; // Backward-compatible for the legacy activity sources.

        long now = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Deque<Long> timestamps = rewardTimes
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(source, ignored -> new ArrayDeque<>());

        pruneExpiredRewardTimes(now);
        // Re-fetch after pruning: pruning may have removed this player's old source bucket.
        Deque<Long> activeTimestamps = rewardTimes
                .computeIfAbsent(playerId, ignored -> new HashMap<>())
                .computeIfAbsent(source, ignored -> new ArrayDeque<>());
        while (!activeTimestamps.isEmpty() && activeTimestamps.peekFirst() <= now - RATE_WINDOW_MILLIS) {
            activeTimestamps.removeFirst();
        }

        if (activeTimestamps.size() >= limit) {
            Map<String, Long> notices = lastLimitNotice.computeIfAbsent(playerId, ignored -> new HashMap<>());
            long lastNotice = notices.getOrDefault(source, 0L);
            if (now - lastNotice >= NOTICE_COOLDOWN_MILLIS) {
                notices.put(source, now);
                player.sendMessage(plugin.color("&cLimit fragmentů za tuto aktivitu je pro tuto chvíli vyčerpaný. &7Limit se obnovuje průběžně."));
            }
            return false;
        }

        activeTimestamps.addLast(now);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep the rolling one-minute history across reconnects so quitting cannot reset anti-farm limits.
        lastLimitNotice.remove(event.getPlayer().getUniqueId());
    }

    private void pruneExpiredRewardTimes(long now) {
        long cutoff = now - RATE_WINDOW_MILLIS;
        var playerIterator = rewardTimes.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map<String, Deque<Long>> bySource = playerIterator.next().getValue();
            var sourceIterator = bySource.entrySet().iterator();
            while (sourceIterator.hasNext()) {
                Deque<Long> timestamps = sourceIterator.next().getValue();
                while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                    timestamps.removeFirst();
                }
                if (timestamps.isEmpty()) sourceIterator.remove();
            }
            if (bySource.isEmpty()) playerIterator.remove();
        }
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
