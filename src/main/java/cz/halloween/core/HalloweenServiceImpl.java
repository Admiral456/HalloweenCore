package cz.halloween.core;

import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

public final class HalloweenServiceImpl implements HalloweenService {
    private final HalloweenCore plugin;

    public HalloweenServiceImpl(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public long getFragments(UUID playerId) {
        return plugin.getDataManager().getFragments(playerId);
    }

    @Override
    public long addFragments(UUID playerId, long amount, String source) {
        if (!plugin.isEventEnabled() || amount <= 0L) return getFragments(playerId);

        double multiplier = getFragmentMultiplier(playerId, source);
        long rewardedAmount;
        if (multiplier <= 1.0D) {
            rewardedAmount = amount;
        } else {
            double scaled = amount * multiplier;
            rewardedAmount = scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(scaled));
        }

        long before = getServerFragments();
        long updated = plugin.getDataManager().addFragments(playerId, rewardedAmount);

        if (plugin.getConfig().getBoolean("notifications.fragment-gain", true)) {
            plugin.notifyFragmentGain(playerId, rewardedAmount);
        }

        long after = getServerFragments();
        long goal = getGlobalGoal();
        if (goal > 0L && before < goal && after >= goal) {
            plugin.broadcastGlobalGoalReached();
        }

        return updated;
    }

    @Override
    public double getFragmentMultiplier(UUID playerId, String source) {
        if (source != null && source.equalsIgnoreCase("admin")) return 1.0D;

        double multiplier = 1.0D;

        if (plugin.getEventManager() != null) {
            multiplier *= plugin.getEventManager().getMultiplier(source == null ? "unknown" : source);
        }

        double curseBonus = plugin.getConfig().getDouble("curse.bonus-per-level", 0.05D);
        int level = getCurseLevel(playerId);
        multiplier *= Math.max(0.0D, 1.0D + (level * curseBonus));

        return Math.max(1.0D, multiplier);
    }

    @Override
    public long getServerFragments() {
        return plugin.getDataManager().getServerFragments();
    }

    @Override
    public long getGlobalGoal() {
        return Math.max(0L, plugin.getConfig().getLong("global-goal", 100000L));
    }

    @Override
    public boolean isEventEnabled() {
        return plugin.isEventEnabled();
    }

    @Override
    public double getGlobalProgressPercent() {
        long goal = getGlobalGoal();
        if (goal <= 0L) return 100.0D;
        return Math.min(100.0D, (getServerFragments() * 100.0D) / goal);
    }

    @Override
    public int getCurseLevel(UUID playerId) {
        long fragments = getFragments(playerId);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("curse.levels");
        if (section == null) return 0;

        int highestLevel = 0;
        for (String key : section.getKeys(false)) {
            long threshold = Math.max(0L, section.getLong(key + ".threshold", Long.MAX_VALUE));
            int level = section.getInt(key + ".level", 0);
            if (fragments >= threshold && level >= highestLevel) {
                highestLevel = level;
            }
        }
        return highestLevel;
    }

    @Override
    public String getCurseName(UUID playerId) {
        int level = getCurseLevel(playerId);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("curse.levels");
        if (section == null) return "Beze jména";

        String fallback = "Beze jména";
        String selected = fallback;
        for (String key : section.getKeys(false)) {
            int configuredLevel = section.getInt(key + ".level", 0);
            if (configuredLevel == level) {
                selected = section.getString(key + ".name", fallback);
                break;
            }
        }
        return selected;
    }
}
