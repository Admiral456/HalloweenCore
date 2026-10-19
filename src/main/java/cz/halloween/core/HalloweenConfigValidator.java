package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        if (!Double.isFinite(curseBonus) || curseBonus < 0.0D || curseBonus > 1.0D) {
            errors.add("curse.bonus-per-level must be between 0 and 1");
        }

        boolean randomEventsEnabled = plugin.getConfig().getBoolean("random-events.enabled", false);
        if (randomEventsEnabled) {
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
            int invasionMobsPerSurge = plugin.getConfig().getInt("random-events.invasion-mobs-per-surge", 2);
            if (invasionMobsPerSurge < 1 || invasionMobsPerSurge > 4) {
                errors.add("random-events.invasion-mobs-per-surge must be between 1 and 4");
            }
        }

        String worldMode = plugin.getConfig().getString("gameplay.worlds.mode", "BLACKLIST");
        if (!worldMode.equalsIgnoreCase("BLACKLIST") && !worldMode.equalsIgnoreCase("WHITELIST")) {
            errors.add("gameplay.worlds.mode must be BLACKLIST or WHITELIST");
        }

        for (String source : List.of("mob-kill", "mining", "farming", "fishing")) {
            String key = "rewards." + source + ".max-fragment-rewards-per-minute";
            if (plugin.getConfig().contains(key)) {
                int limit = plugin.getConfig().getInt(key, -1);
                if (limit < 1 || limit > 600) {
                    errors.add(key + " must be between 1 and 600 when configured");
                }
            }
        }

        boolean vanillaAtmosphereEnabled = plugin.getConfig().getBoolean("vanilla-atmosphere.enabled", true);
        if (vanillaAtmosphereEnabled) {
            int ambientMin = plugin.getConfig().getInt("vanilla-atmosphere.min-interval-seconds", 90);
            int ambientMax = plugin.getConfig().getInt("vanilla-atmosphere.max-interval-seconds", 180);
            double playerChance = plugin.getConfig().getDouble("vanilla-atmosphere.player-chance", 0.35D);
            double soundVolume = plugin.getConfig().getDouble("vanilla-atmosphere.sound-volume", 0.55D);
            if (ambientMin < 30 || ambientMax < ambientMin || ambientMax > 600) {
                errors.add("vanilla-atmosphere interval must be ordered between 30 and 600 seconds");
            }
            if (!Double.isFinite(playerChance) || playerChance < 0.0D || playerChance > 1.0D) {
                errors.add("vanilla-atmosphere.player-chance must be between 0 and 1");
            }
            if (!Double.isFinite(soundVolume) || soundVolume < 0.0D || soundVolume > 1.5D) {
                errors.add("vanilla-atmosphere.sound-volume must be between 0 and 1.5");
            }
        }

        boolean vanillaEventsEnabled = plugin.getConfig().getBoolean("vanilla-events.enabled", true);
        if (vanillaEventsEnabled) {
            int firstDelay = plugin.getConfig().getInt("vanilla-events.first-delay-seconds", 300);
            int eventIntervalMin = plugin.getConfig().getInt("vanilla-events.interval-minutes.min", 20);
            int eventIntervalMax = plugin.getConfig().getInt("vanilla-events.interval-minutes.max", 35);
            int eventDuration = plugin.getConfig().getInt("vanilla-events.duration-minutes", 6);
            double harvestMultiplier = plugin.getConfig().getDouble(
                    "vanilla-events.pumpkin-harvest.farming-fragment-multiplier", 2.0D);
            double witchingMultiplier = plugin.getConfig().getDouble(
                    "vanilla-events.witching-hour.mob-kill-fragment-multiplier", 1.5D);
            double bloodMoonMultiplier = plugin.getConfig().getDouble(
                    "vanilla-events.blood-moon.mob-kill-fragment-multiplier", 1.25D);
            double bloodMoonDamage = plugin.getConfig().getDouble(
                    "vanilla-events.blood-moon.hostile-damage-multiplier", 1.5D);

            if (firstDelay < 30 || firstDelay > 3600) {
                errors.add("vanilla-events.first-delay-seconds must be between 30 and 3600");
            }
            if (eventIntervalMin < 5 || eventIntervalMax < eventIntervalMin || eventIntervalMax > 180) {
                errors.add("vanilla-events.interval-minutes must be ordered between 5 and 180");
            }
            if (eventDuration < 1 || eventDuration > 30) {
                errors.add("vanilla-events.duration-minutes must be between 1 and 30");
            }
            if (!Double.isFinite(harvestMultiplier) || harvestMultiplier < 1.0D || harvestMultiplier > 4.0D) {
                errors.add("vanilla-events.pumpkin-harvest.farming-fragment-multiplier must be between 1 and 4");
            }
            if (!Double.isFinite(witchingMultiplier) || witchingMultiplier < 1.0D || witchingMultiplier > 4.0D) {
                errors.add("vanilla-events.witching-hour.mob-kill-fragment-multiplier must be between 1 and 4");
            }
            if (!Double.isFinite(bloodMoonMultiplier) || bloodMoonMultiplier < 1.0D || bloodMoonMultiplier > 4.0D) {
                errors.add("vanilla-events.blood-moon.mob-kill-fragment-multiplier must be between 1 and 4");
            }
            if (!Double.isFinite(bloodMoonDamage) || bloodMoonDamage < 1.0D || bloodMoonDamage > 3.0D) {
                errors.add("vanilla-events.blood-moon.hostile-damage-multiplier must be between 1 and 3");
            }
        }

        boolean worldFeaturesEnabled = plugin.getConfig().getBoolean("world-features.enabled", true);
        if (worldFeaturesEnabled
                && plugin.getConfig().getBoolean("world-features.generation.enabled", true)) {
            double pumpkinChance = plugin.getConfig().getDouble(
                    "world-features.generation.pumpkin-patch-chance-per-chunk", 0.06D);
            double webChance = plugin.getConfig().getDouble(
                    "world-features.generation.cave-web-patch-chance-per-chunk", 0.04D);
            int featureAttempts = plugin.getConfig().getInt(
                    "world-features.generation.attempts-per-feature", 24);
            int pumpkinsPerPatch = plugin.getConfig().getInt(
                    "world-features.generation.max-pumpkins-per-patch", 4);
            int websPerPatch = plugin.getConfig().getInt(
                    "world-features.generation.max-cave-webs-per-patch", 2);
            int caveMinY = plugin.getConfig().getInt("world-features.generation.cave-webs-min-y", -48);
            int caveMaxY = plugin.getConfig().getInt("world-features.generation.cave-webs-max-y", 24);

            if (!Double.isFinite(pumpkinChance) || pumpkinChance < 0.0D || pumpkinChance > 1.0D) {
                errors.add("world-features.generation.pumpkin-patch-chance-per-chunk must be between 0 and 1");
            }
            if (!Double.isFinite(webChance) || webChance < 0.0D || webChance > 1.0D) {
                errors.add("world-features.generation.cave-web-patch-chance-per-chunk must be between 0 and 1");
            }
            if (featureAttempts < 1 || featureAttempts > 64) {
                errors.add("world-features.generation.attempts-per-feature must be between 1 and 64");
            }
            if (pumpkinsPerPatch < 2 || pumpkinsPerPatch > 6) {
                errors.add("world-features.generation.max-pumpkins-per-patch must be between 2 and 6");
            }
            if (websPerPatch < 1 || websPerPatch > 3) {
                errors.add("world-features.generation.max-cave-webs-per-patch must be between 1 and 3");
            }
            if (caveMinY >= caveMaxY) {
                errors.add("world-features.generation.cave-webs-min-y must be below cave-webs-max-y");
            }
        }

        boolean cachesEnabled = plugin.getConfig().getBoolean("underground-caches.enabled", true);
        if (cachesEnabled) {
            if (plugin.getConfig().getBoolean("underground-caches.generation.enabled", true)) {
                double chunkChance = plugin.getConfig().getDouble(
                        "underground-caches.generation.chance-per-new-chunk", 0.10D);
                int minY = plugin.getConfig().getInt("underground-caches.generation.min-y", -48);
                int maxY = plugin.getConfig().getInt("underground-caches.generation.max-y", 24);
                int attempts = plugin.getConfig().getInt(
                        "underground-caches.generation.attempts-per-chunk", 32);
                if (!Double.isFinite(chunkChance) || chunkChance < 0.0D || chunkChance > 1.0D) {
                    errors.add("underground-caches.generation.chance-per-new-chunk must be between 0 and 1");
                }
                if (minY >= maxY) {
                    errors.add("underground-caches.generation.min-y must be below max-y");
                }
                if (attempts < 1 || attempts > 64) {
                    errors.add("underground-caches.generation.attempts-per-chunk must be between 1 and 64");
                }
            }

            if (plugin.getConfig().getBoolean("underground-caches.key-drop.enabled", true)) {
                double keyChance = plugin.getConfig().getDouble(
                        "underground-caches.key-drop.chance-per-hostile-kill", 0.006D);
                if (!Double.isFinite(keyChance) || keyChance < 0.0D || keyChance > 0.05D) {
                    errors.add("underground-caches.key-drop.chance-per-hostile-kill must be between 0 and 0.05");
                }
            }
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

                String itemsAdderId = plugin.getConfig().getString("rewards.shop." + id + ".itemsadder-id", "");
                String material = reward.getString("material", "");
                int amount = reward.getInt("amount", 1);
                if (itemsAdderId.isBlank() && material.isBlank()) {
                    errors.add("reward " + id + " needs itemsadder-id or material");
                }
                if (!itemsAdderId.isBlank()
                        && !itemsAdderId.matches("[a-z0-9._-]+:[a-z0-9._-]+")) {
                    errors.add("reward " + id + " itemsadder-id must use a valid lowercase namespace:id");
                }
                if (reward.contains("bonus-multiplier")) {
                    double bonusMultiplier = reward.getDouble("bonus-multiplier", 0.0D);
                    if (!Double.isFinite(bonusMultiplier) || bonusMultiplier < 0.0D || bonusMultiplier > 5.0D) {
                        errors.add("reward " + id + " bonus-multiplier must be finite and between 0 and 5");
                    }
                }
                if (reward.contains("bonus-health")) {
                    double bonusHealth = reward.getDouble("bonus-health", 2.0D);
                    if (!Double.isFinite(bonusHealth) || bonusHealth < 0.0D || bonusHealth > 20.0D) {
                        errors.add("reward " + id + " bonus-health must be finite and between 0 and 20");
                    }
                }
                if (amount < 1 || amount > 64) {
                    errors.add("reward " + id + " amount must be between 1 and 64");
                }
                if (!material.isBlank()) {
                    try {
                        Material fallbackMaterial = Material.valueOf(material.toUpperCase(Locale.ROOT));
                        if (fallbackMaterial.isAir() || !fallbackMaterial.isItem()) {
                            errors.add("reward " + id + " fallback material must be a valid inventory item: " + fallbackMaterial);
                        } else if (amount >= 1 && amount > fallbackMaterial.getMaxStackSize()) {
                            errors.add("reward " + id + " amount exceeds the stack size of fallback material " + fallbackMaterial);
                        }
                    } catch (IllegalArgumentException ex) {
                        errors.add("reward " + id + " has an invalid fallback material: " + material);
                    }
                }
            }
        }

        boolean secretDiscoveriesEnabled = plugin.getConfig().getBoolean("secret-discoveries.enabled", false);
        ConfigurationSection secrets = plugin.getConfig().getConfigurationSection("secret-discoveries.locations");
        if (secretDiscoveriesEnabled && (secrets == null || secrets.getKeys(false).isEmpty())) {
            errors.add("secret-discoveries.locations must contain at least one configured secret definition");
        }
        if (secretDiscoveriesEnabled && secrets != null) {
            for (String id : secrets.getKeys(false)) {
                ConfigurationSection secret = secrets.getConfigurationSection(id);
                if (secret == null) {
                    errors.add("secret-discoveries.locations." + id + " must be a section");
                    continue;
                }
                if (!id.matches("[a-z0-9][a-z0-9_-]*")) {
                    errors.add("secret-discoveries.locations." + id + " id must use lowercase letters, digits, '-' or '_'");
                }
                String name = secret.getString("name", "");
                String hint = secret.getString("hint", "");
                if (name.isBlank()) errors.add("secret-discoveries.locations." + id + ".name must not be blank");
                if (hint.isBlank()) errors.add("secret-discoveries.locations." + id + ".hint must not be blank");
                if (secret.getBoolean("configured", false)) {
                    String world = secret.getString("world", "");
                    if (world.isBlank()) errors.add("secret-discoveries.locations." + id + ".world must be set");
                    double x = secret.getDouble("x", Double.NaN);
                    double y = secret.getDouble("y", Double.NaN);
                    double z = secret.getDouble("z", Double.NaN);
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                        errors.add("secret-discoveries.locations." + id + " coordinates must be finite numbers");
                    }
                    double radius = secret.getDouble("radius-blocks",
                            plugin.getConfig().getDouble("secret-discoveries.default-radius-blocks", 4.5D));
                    if (!Double.isFinite(radius) || radius < 1.0D || radius > 32.0D) {
                        errors.add("secret-discoveries.locations." + id + ".radius-blocks must be finite and between 1 and 32");
                    }
                    long reward = secret.getLong("reward-fragments",
                            plugin.getConfig().getLong("secret-discoveries.default-reward-fragments", 250L));
                    if (reward <= 0L) {
                        errors.add("secret-discoveries.locations." + id + ".reward-fragments must be > 0");
                    }
                }
            }
        }

        boolean villageEnabled = plugin.getConfig().getBoolean("haunted-village.enabled", false);
        String villageWorld = plugin.getConfig().getString("haunted-village.world", "");
        if (villageEnabled && villageWorld.isBlank()) {
            errors.add("haunted-village.world must be configured when Haunted Village is enabled");
        }
        boolean specialMobsEnabled = plugin.getConfig().getBoolean("special-mobs.enabled", false);
        double mapChance = plugin.getConfig().getDouble("special-mobs.map-drop-chance", -1.0D);
        if (specialMobsEnabled && (mapChance < 0.0D || mapChance > 1.0D)) {
            errors.add("special-mobs.map-drop-chance must be between 0 and 1");
        }
        ConfigurationSection specialMobTypes = plugin.getConfig().getConfigurationSection("special-mobs.types");
        if (specialMobsEnabled && specialMobTypes != null) {
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
        if (specialMobsEnabled && (mapItemId.isBlank() || !mapItemId.contains(":"))) {
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
        if (vampire != null && vampire.getBoolean("enabled", false)) {
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

        long claimDelayHours = plugin.getConfig().getLong("return-rewards.claim-delay-hours", 20L);
        long streakWindowHours = plugin.getConfig().getLong("return-rewards.streak-window-hours", 48L);
        long comebackAfterHours = plugin.getConfig().getLong("return-rewards.comeback-after-hours", 72L);
        if (claimDelayHours < 1L || claimDelayHours > 87_600L) {
            errors.add("return-rewards.claim-delay-hours must be between 1 and 87600");
        }
        if (streakWindowHours < 1L || streakWindowHours > 87_600L) {
            errors.add("return-rewards.streak-window-hours must be between 1 and 87600");
        }
        if (comebackAfterHours < 1L || comebackAfterHours > 87_600L) {
            errors.add("return-rewards.comeback-after-hours must be between 1 and 87600");
        }

        return List.copyOf(errors);
    }
}
