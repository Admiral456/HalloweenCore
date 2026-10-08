package cz.halloween.core;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    private final Map<UUID, Long> lifetimeFragments = new HashMap<>();
    private final Map<UUID, Long> lastJoin = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<UUID, Set<String>> claimedRewards = new HashMap<>();
    private final Map<UUID, String> challengeDay = new HashMap<>();
    private final Map<UUID, String> challengeType = new HashMap<>();
    private final Map<UUID, Integer> challengeProgress = new HashMap<>();
    private final Set<UUID> challengeClaimed = new HashSet<>();
    private long serverFragments;

    public HalloweenDataManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        fragments.clear();
        lifetimeFragments.clear();
        lastJoin.clear();
        streaks.clear();
        claimedRewards.clear();
        challengeDay.clear();
        challengeType.clear();
        challengeProgress.clear();
        challengeClaimed.clear();
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
                    long lifetime = Math.max(0L, data.getLong(base + ".lifetime-fragments", amount));
                    long join = Math.max(0L, data.getLong(base + ".last-join", 0L));
                    int streak = Math.max(0, data.getInt(base + ".streak", 0));

                    if (amount > 0L) fragments.put(uuid, amount);
                    if (lifetime > 0L) lifetimeFragments.put(uuid, lifetime);
                    if (join > 0L) lastJoin.put(uuid, join);
                    if (streak > 0) streaks.put(uuid, streak);

                    java.util.List<String> rewards = data.getStringList(base + ".claimed-rewards");
                    if (!rewards.isEmpty()) {
                        claimedRewards.put(uuid, new HashSet<>(rewards));
                    }

                    String day = data.getString(base + ".challenge.day", "");
                    String type = data.getString(base + ".challenge.type", "");
                    int progress = Math.max(0, data.getInt(base + ".challenge.progress", 0));
                    boolean claimed = data.getBoolean(base + ".challenge.claimed", false);
                    if (!day.isBlank()) challengeDay.put(uuid, day);
                    if (!type.isBlank()) challengeType.put(uuid, type);
                    if (progress > 0) challengeProgress.put(uuid, progress);
                    if (claimed) challengeClaimed.add(uuid);
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
        players.addAll(lifetimeFragments.keySet());
        players.addAll(lastJoin.keySet());
        players.addAll(streaks.keySet());
        players.addAll(claimedRewards.keySet());
        players.addAll(challengeDay.keySet());
        players.addAll(challengeType.keySet());
        players.addAll(challengeProgress.keySet());
        players.addAll(challengeClaimed);

        for (UUID uuid : players) {
            String base = "players." + uuid;
            long amount = fragments.getOrDefault(uuid, 0L);
            long join = lastJoin.getOrDefault(uuid, 0L);
            int streak = streaks.getOrDefault(uuid, 0);

            if (amount > 0L) data.set(base + ".fragments", amount);
            long lifetime = lifetimeFragments.getOrDefault(uuid, 0L);
            if (lifetime > 0L) data.set(base + ".lifetime-fragments", lifetime);
            if (join > 0L) data.set(base + ".last-join", join);
            if (streak > 0) data.set(base + ".streak", streak);

            Set<String> rewards = claimedRewards.get(uuid);
            if (rewards != null && !rewards.isEmpty()) {
                data.set(base + ".claimed-rewards", new ArrayList<>(rewards));
            }

            if (challengeDay.containsKey(uuid)) data.set(base + ".challenge.day", challengeDay.get(uuid));
            if (challengeType.containsKey(uuid)) data.set(base + ".challenge.type", challengeType.get(uuid));
            if (challengeProgress.containsKey(uuid)) data.set(base + ".challenge.progress", challengeProgress.get(uuid));
            if (challengeClaimed.contains(uuid)) data.set(base + ".challenge.claimed", true);
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder.");
            }

            File temp = new File(plugin.getDataFolder(), "data.yml.tmp");
            data.save(temp);

            try {
                Files.move(temp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
        }
    }

    public long getFragments(UUID uuid) {
        return fragments.getOrDefault(uuid, 0L);
    }

    public long addFragments(UUID uuid, long amount) {
        return addFragments(uuid, amount, true, true);
    }

    public long addFragments(UUID uuid, long amount, boolean countGlobal, boolean countLifetime) {
        if (amount <= 0L) return getFragments(uuid);

        long current = getFragments(uuid);
        long updated = current;
        try {
            updated = Math.addExact(current, amount);
        } catch (ArithmeticException ex) {
            updated = Long.MAX_VALUE;
        }
        fragments.put(uuid, updated);

        if (countGlobal) {
            try {
                serverFragments = Math.addExact(serverFragments, amount);
            } catch (ArithmeticException ex) {
                serverFragments = Long.MAX_VALUE;
            }
        }

        if (countLifetime) {
            long currentLifetime = lifetimeFragments.getOrDefault(uuid, current);
            try {
                lifetimeFragments.put(uuid, Math.addExact(currentLifetime, amount));
            } catch (ArithmeticException ex) {
                lifetimeFragments.put(uuid, Long.MAX_VALUE);
            }
        }

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

    public long getLifetimeFragments(UUID uuid) {
        return lifetimeFragments.getOrDefault(uuid, getFragments(uuid));
    }

    public long getServerFragments() {
        return serverFragments;
    }

    public Map<UUID, Long> getAllFragments() {
        return Map.copyOf(fragments);
    }

    public Map<UUID, Long> getAllLifetimeFragments() {
        return Map.copyOf(lifetimeFragments);
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

    public String getChallengeDay(UUID uuid) {
        return challengeDay.getOrDefault(uuid, "");
    }

    public String getChallengeType(UUID uuid) {
        return challengeType.getOrDefault(uuid, "");
    }

    public int getChallengeProgress(UUID uuid) {
        return challengeProgress.getOrDefault(uuid, 0);
    }

    public boolean isChallengeClaimed(UUID uuid) {
        return challengeClaimed.contains(uuid);
    }

    public void setChallenge(UUID uuid, String day, String type) {
        challengeDay.put(uuid, day);
        challengeType.put(uuid, type);
        challengeProgress.put(uuid, 0);
        challengeClaimed.remove(uuid);
    }

    public int incrementChallengeProgress(UUID uuid, int amount) {
        if (amount <= 0) return getChallengeProgress(uuid);
        int updated = Math.min(Integer.MAX_VALUE, getChallengeProgress(uuid) + amount);
        challengeProgress.put(uuid, updated);
        return updated;
    }

    public void markChallengeClaimed(UUID uuid) {
        challengeClaimed.add(uuid);
    }

    public boolean hasClaimedReward(UUID uuid, String rewardId) {
        return claimedRewards.getOrDefault(uuid, Collections.emptySet()).contains(rewardId);
    }

    public void markRewardClaimed(UUID uuid, String rewardId) {
        claimedRewards.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(rewardId);
    }
}
