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
        ConfigurationSection specialMobTypes = plugin.getConfig().getConfigurationSection("special-mobs.types");
        if (specialMobTypes != null) {
            for (String id : specialMobTypes.getKeys(false)) {
                ConfigurationSection mob = specialMobTypes.getConfigurationSection(id);
                if (mob == null) continue;
                long cooldown = mob.getLong("ability-cooldown-seconds", 8L);
                if (cooldown < 1L || cooldown > 300L) {
                    errors.add("special-mobs.types." + id + ".ability-cooldown-seconds must be between 1 and 300");
                }
                if (mob.contains("grave-mark-delay-ticks")) {
                    long delay = mob.getLong("grave-mark-delay-ticks", 24L);
                    double radius = mob.getDouble("grave-mark-radius-blocks", 1.5D);
                    if (delay < 10L || delay > 100L) {
                        errors.add("special-mobs.types." + id + ".grave-mark-delay-ticks must be between 10 and 100");
                    }
                    if (radius < 0.5D || radius > 8.0D) {
                        errors.add("special-mobs.types." + id + ".grave-mark-radius-blocks must be between 0.5 and 8");
                    }
                }
                if (mob.contains("ash-flash-radius-blocks")) {
                    double radius = mob.getDouble("ash-flash-radius-blocks", 5.0D);
                    if (radius < 2.0D || radius > 16.0D) {
                        errors.add("special-mobs.types." + id + ".ash-flash-radius-blocks must be between 2 and 16");
                    }
                }
            }
        }

        String mapItemId = plugin.getConfig().getString("special-mobs.map-item-id", "");
        if (mapItemId.isBlank() || !mapItemId.contains(":")) {
            errors.add("special-mobs.map-item-id should use namespace:id");
        }

        if (villageEnabled) {
            double radius = plugin.getConfig().getDouble("haunted-village.discovery-radius", -1.0D);
            long reward = plugin.getConfig().getLong("haunted-village.discovery-reward-fragments", -1L);
            if (radius < 2.0D || radius > 128.0D) {
                errors.add("haunted-village.discovery-radius must be between 2 and 128");
            }
            if (reward <= 0L) {
                errors.add("haunted-village.discovery-reward-fragments must be > 0");
            }
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
            if (vampire.getBoolean("arena.configured", false)
                    && vampire.getString("arena.world", "").isBlank()) {
                errors.add("vampire arena is marked configured but arena.world is blank");
            }
            if (vampire.getBoolean("arena.configured", false)) {
                String arenaWorld = vampire.getString("arena.world", "");
                double arenaX = vampire.getDouble("arena.x", Double.NaN);
                double arenaY = vampire.getDouble("arena.y", Double.NaN);
                double arenaZ = vampire.getDouble("arena.z", Double.NaN);
                if (arenaWorld.isBlank() || Double.isNaN(arenaX) || Double.isNaN(arenaY) || Double.isNaN(arenaZ)) {
                    errors.add("vampire arena coordinates must be configured when arena.configured is true");
                }
            }

            ConfigurationSection encounter = vampire.getConfigurationSection("encounter");
            if (encounter != null) {
                int maxDuration = encounter.getInt("max-duration-minutes", -1);
                double arenaRadius = encounter.getDouble("arena-radius-blocks", -1.0D);
                double participationRadius = encounter.getDouble("participation-radius-blocks", -1.0D);
                int minParticipation = encounter.getInt("min-participation-seconds", -1);
                long participationReward = encounter.getLong("participation-reward-fragments", -1L);
                long victoryReward = encounter.getLong("victory-reward-fragments", -1L);
                long topBonus = encounter.getLong("top-contributor-bonus-fragments", -1L);
                if (maxDuration < 1 || maxDuration > 120) {
                    errors.add("vampire encounter max-duration-minutes must be between 1 and 120");
                }
                if (arenaRadius < 12.0D || arenaRadius > 128.0D) {
                    errors.add("vampire encounter arena-radius-blocks must be between 12 and 128");
                }
                if (participationRadius < arenaRadius || participationRadius > 256.0D) {
                    errors.add("vampire encounter participation-radius-blocks must be >= arena radius and <= 256");
                }
                if (minParticipation < 0 || minParticipation > maxDuration * 60) {
                    errors.add("vampire encounter min-participation-seconds is outside encounter duration");
                }
                if (participationReward < 0L || victoryReward < 0L || topBonus < 0L) {
                    errors.add("vampire encounter rewards must be >= 0");
                }
                double minimumDamagePercent = encounter.getDouble("minimum-damage-percent", 2.5D);
                if (minimumDamagePercent < 0.0D || minimumDamagePercent > 100.0D) {
                    errors.add("vampire encounter minimum-damage-percent must be between 0 and 100");
                }

                ConfigurationSection summoning = vampire.getConfigurationSection("summoning");
                if (summoning != null) {
                    long delaySeconds = summoning.getLong("delay-after-readiness-seconds", 300L);
                    long retryMinutes = summoning.getLong("retry-delay-minutes", 20L);
                    int minimumPlayers = summoning.getInt("minimum-online-players", 1);
                    if (delaySeconds < 180L || delaySeconds > 3600L) {
                        errors.add("vampire summoning delay-after-readiness-seconds must be between 180 and 3600");
                    }
                    if (retryMinutes < 1L || retryMinutes > 180L) {
                        errors.add("vampire summoning retry-delay-minutes must be between 1 and 180");
                    }
                    if (minimumPlayers < 1 || minimumPlayers > 100) {
                        errors.add("vampire summoning minimum-online-players must be between 1 and 100");
                    }
                }

                ConfigurationSection abilities = encounter.getConfigurationSection("abilities");
                if (abilities != null) {
                    long phase1Cooldown = abilities.getLong("phase-1-cooldown-seconds", 14L);
                    long phase2Cooldown = abilities.getLong("phase-2-cooldown-seconds", -1L);
                    long phase3Cooldown = abilities.getLong("phase-3-cooldown-seconds", -1L);
                    long phase4Cooldown = abilities.getLong("phase-4-cooldown-seconds", -1L);
                    if (phase1Cooldown < 8L || phase2Cooldown < 6L || phase3Cooldown < 6L || phase4Cooldown < 6L) {
                        errors.add("vampire encounter ability cooldowns are too low");
                    }
                    if (abilities.getDouble("target-radius-blocks", -1.0D) < 16.0D) {
                        errors.add("vampire encounter abilities target-radius-blocks must be >= 16");
                    }
                }
            }

            ConfigurationSection model = vampire.getConfigurationSection("model");
            if (model != null) {
                String provider = model.getString("provider", "");
                String id = model.getString("id", "");
                boolean required = model.getBoolean("required", true);
                if (required && !provider.equalsIgnoreCase("MODEL_ENGINE")) {
                    errors.add("vampire model provider must be MODEL_ENGINE for the current contract");
                }
                if (required && provider.isBlank()) {
                    errors.add("vampire model provider must be configured");
                }
                if (required && id.isBlank()) {
                    errors.add("vampire model id must be configured");
                }
                if (required && model.getInt("min-height-blocks", 0) < 10) {
                    errors.add("vampire model min-height-blocks must be >= 10");
                }
                if (required && model.getInt("min-width-with-wings-blocks", 0) < 8) {
                    errors.add("vampire model min-width-with-wings-blocks must be >= 8");
                }
                if (required && !model.getBoolean("wings-required", false)) {
                    errors.add("vampire model wings-required must be true");
                }
            }

            ConfigurationSection bossBar = vampire.getConfigurationSection("boss-bar");
            if (bossBar != null) {
                double radius = bossBar.getDouble("radius-blocks", -1.0D);
                if (radius < 16.0D || radius > 256.0D) {
                    errors.add("vampire boss-bar radius-blocks must be between 16 and 256");
                }
                String color = bossBar.getString("color", "RED");
                try {
                    org.bukkit.boss.BarColor.valueOf(color.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    errors.add("vampire boss-bar color is invalid");
                }
                String style = bossBar.getString("style", "SEGMENTED_20");
                try {
                    org.bukkit.boss.BarStyle.valueOf(style.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    errors.add("vampire boss-bar style is invalid");
                }
            }
        }

        return List.copyOf(errors);
    }
}
