package cz.halloween.core;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public final class HalloweenConfigValidator {
    private HalloweenConfigValidator() {
    }

    public static List<String> validate(HalloweenCore plugin) {
        List<String> errors = new ArrayList<>();

        long globalGoal = plugin.getConfig().getLong("global-goal", -1L);
        if (globalGoal <= 0L) {
            errors.add("global-goal must be > 0");
        }

        long previousMilestone = 0L;
        for (long milestone : plugin.getConfig().getLongList("global-milestones")) {
            if (milestone <= previousMilestone) {
                errors.add("global-milestones must be strictly ascending");
                break;
            }
            if (milestone > globalGoal && globalGoal > 0L) {
                errors.add("global milestone " + milestone + " is above global-goal " + globalGoal);
            }
            previousMilestone = milestone;
        }

        double curseBonus = plugin.getConfig().getDouble("curse.bonus-per-level", -1.0D);
        if (curseBonus < 0.0D || curseBonus > 1.0D) {
            errors.add("curse.bonus-per-level must be between 0 and 1");
        }

        int intervalMin = plugin.getConfig().getInt("random-events.interval-minutes.min", -1);
        int intervalMax = plugin.getConfig().getInt("random-events.interval-minutes.max", -1);
        if (intervalMin < 1 || intervalMax < intervalMin) {
            errors.add("random-events.interval-minutes is invalid");
        }

        int duration = plugin.getConfig().getInt("random-events.duration-minutes", -1);
        long surgeInterval = plugin.getConfig().getLong("random-events.surge-interval-seconds", -1L);
        int maxEventMobs = plugin.getConfig().getInt("random-events.max-event-mobs", -1);
        if (duration < 1) errors.add("random-events.duration-minutes must be >= 1");
        if (surgeInterval < 10L) errors.add("random-events.surge-interval-seconds must be >= 10");
        if (maxEventMobs < 1) errors.add("random-events.max-event-mobs must be >= 1");

        String worldMode = plugin.getConfig().getString("gameplay.worlds.mode", "BLACKLIST");
        if (!worldMode.equalsIgnoreCase("BLACKLIST") && !worldMode.equalsIgnoreCase("WHITELIST")) {
            errors.add("gameplay.worlds.mode must be BLACKLIST or WHITELIST");
        }

        ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("rewards.shop");
        if (rewards == null || rewards.getKeys(false).isEmpty()) {
            errors.add("rewards.shop must contain at least one reward");
        } else {
            for (String id : rewards.getKeys(false)) {
                ConfigurationSection reward = rewards.getConfigurationSection(id);
                if (reward == null) {
                    errors.add("rewards.shop." + id + " is not a section");
                    continue;
                }

                if (reward.getLong("cost", -1L) < 0L) {
                    errors.add("reward " + id + " cost must be >= 0");
                }

                int minCurse = reward.getInt("min-curse", 0);
                if (minCurse < 0 || minCurse > 5) {
                    errors.add("reward " + id + " min-curse must be between 0 and 5");
                }

                String itemsAdderId = reward.getString("itemsadder-id", "");
                String material = reward.getString("material", "");
                if (itemsAdderId.isBlank() && material.isBlank()) {
                    errors.add("reward " + id + " needs itemsadder-id or material");
                }
                if (!itemsAdderId.isBlank() && !itemsAdderId.contains(":")) {
                    errors.add("reward " + id + " itemsadder-id should use namespace:id");
                }
            }
        }

        boolean villageEnabled = plugin.getConfig().getBoolean("haunted-village.enabled", false);
        String villageWorld = plugin.getConfig().getString("haunted-village.world", "");
        if (villageEnabled && villageWorld.isBlank()) {
            errors.add("haunted-village.world must be configured when Haunted Village is enabled");
        }
        double mapChance = plugin.getConfig().getDouble("special-mobs.map-drop-chance", -1.0D);
        if (mapChance < 0.0D || mapChance > 1.0D) {
            errors.add("special-mobs.map-drop-chance must be between 0 and 1");
        }
        String mapItemId = plugin.getConfig().getString("special-mobs.map-item-id", "");
        if (mapItemId.isBlank() || !mapItemId.contains(":")) {
            errors.add("special-mobs.map-item-id should use namespace:id");
        }

        ConfigurationSection vampire = plugin.getConfig().getConfigurationSection("bosses.vampire");
        if (vampire != null) {
            if (vampire.getInt("min-height-blocks", 0) < 10) {
                errors.add("vampire min-height-blocks must be >= 10");
            }
            if (vampire.getInt("min-width-with-wings-blocks", 0) < 8) {
                errors.add("vampire min-width-with-wings-blocks must be >= 8");
            }
            if (!vampire.getBoolean("wings-required", false)) {
                errors.add("vampire wings-required must be true");
            }
        }

        return List.copyOf(errors);
    }
}
