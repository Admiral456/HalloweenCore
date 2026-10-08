package cz.halloween.core;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HalloweenDataManager {
    private final HalloweenCore plugin;
    private final File file;
    private final Map<UUID, Long> fragments = new HashMap<>();
    private final Map<UUID, Long> lastJoin = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Set<String>> claimedRewards = new HashMap<>();
    private long serverFragments;

    public HalloweenDataManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        fragments.clear();
        lastJoin.clear();
        streaks.clear();
        claimedRewards.clear();
        serverFragments = 0L;
        if (!file.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        serverFragments = Math.max(0L, data.getLong("server.total-fragments", 0L));

        if (data.isConfigurationSection("players")) {
            for (String key : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String base = "players." + key;

                    long amount = Math.max(0L, data.getLong(base + ".fragments", 0L));
                    long join = Math.max(0L, data.getLong(base + ".last-join", 0L));
                    int streak = Math.max(0, data.getInt(base + ".streak", 0));

                    if (amount > 0L) fragments.put(uuid, amount);
                    if (join > 0L) lastJoin.put(uuid, join);
                    if (streak > 0) streaks.put(uuid, streak);

                    java.util.List<String> rewards = data.getStringList(base + ".claimed-rewards");
                    if (!rewards.isEmpty()) {
                        claimedRewards.put(uuid, new HashSet<>(rewards));
                    }
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid player UUID in data.yml: " + key);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("server.total-fragments", serverFragments);

        Set<UUID> players = new HashSet<>();
        players.addAll(fragments.keySet());
        players.addAll(lastJoin.keySet());
        players.addAll(streaks.keySet());
        players.addAll(claimedRewards.keySet());

        for (UUID uuid : players) {
            String base = "players." + uuid;
            long amount = fragments.getOrDefault(uuid, 0L);
            long join = lastJoin.getOrDefault(uuid, 0L);
            int streak = streaks.getOrDefault(uuid, 0);

            if (amount > 0L) data.set(base + ".fragments", amount);
            if (join > 0L) data.set(base + ".last-join", join);
            if (streak > 0) data.set(base + ".streak", streak);

            Set<String> rewards = claimedRewards.get(uuid);
            if (rewards != null && !rewards.isEmpty()) {
                data.set(base + ".claimed-rewards", new ArrayList<>(rewards));
            }
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder.");
            }

            File temp = new File(plugin.getDataFolder(), "data.yml.tmp");
            data.save(temp);

            if (file.exists() && !file.delete()) {
                throw new IOException("Could not replace old data.yml.");
            }
            if (!temp.renameTo(file)) {
                throw new IOException("Could not move temporary data.yml into place.");
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public long getFragments(UUID uuid) {
        return fragments.getOrDefault(uuid, 0L);
    }

    public long addFragments(UUID uuid, long amount) {
        if (amount <= 0L) return getFragments(uuid);

        long current = getFragments(uuid);
        long updated;
        try {
            updated = Math.addExact(current, amount);
            serverFragments = Math.addExact(serverFragments, amount);
        } catch (ArithmeticException ex) {
            updated = Long.MAX_VALUE;
            serverFragments = Long.MAX_VALUE;
        }

        fragments.put(uuid, updated);
        return updated;
    }

    public boolean removeFragments(UUID uuid, long amount) {
        if (amount <= 0L) return false;

        long current = getFragments(uuid);
        if (current < amount) return false;

        long updated = current - amount;
        if (updated == 0L) fragments.remove(uuid);
        else fragments.put(uuid, updated);
        return true;
    }

    public long getServerFragments() {
        return serverFragments;
    }

    public Map<UUID, Long> getAllFragments() {
        return Map.copyOf(fragments);
    }

    public long getLastJoin(UUID uuid) {
        return lastJoin.getOrDefault(uuid, 0L);
    }

    public int getStreak(UUID uuid) {
        return streaks.getOrDefault(uuid, 0);
    }

    public void recordJoin(UUID uuid, long timestamp, int streak) {
        lastJoin.put(uuid, timestamp);
        streaks.put(uuid, Math.max(1, streak));
    }

    public boolean hasClaimedReward(UUID uuid, String rewardId) {
        return claimedRewards.getOrDefault(uuid, Collections.emptySet()).contains(rewardId);
    }

    public void markRewardClaimed(UUID uuid, String rewardId) {
        claimedRewards.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(rewardId);
    }
}
