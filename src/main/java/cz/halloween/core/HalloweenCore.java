package cz.halloween.core;

import org.bukkit.Bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.GameMode;
import org.bukkit.World;

import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.HashSet;
import java.util.Set;

public final class HalloweenCore extends JavaPlugin {
    private HalloweenDataManager dataManager;
    private HalloweenServiceImpl service;
    private boolean eventEnabled;
    private HalloweenEventManager eventManager;
    private HalloweenVanillaEventManager vanillaEventManager;
    private HalloweenVanillaAtmosphereManager vanillaAtmosphereManager;
    private HalloweenRewardManager rewardManager;
    private HalloweenAtmosphere atmosphere;
    private HalloweenMobManager mobManager;
    private HalloweenChallengeManager challengeManager;
    private HalloweenQuestManager questManager;
    private HalloweenBossManager bossManager;
    private HalloweenVanillaBossManager vanillaBossManager;
    private HalloweenItemManager itemManager;
    private HalloweenPassiveEffectManager passiveEffectManager;
    private HalloweenPumpkinVillagerManager pumpkinVillagerManager;
    private HalloweenCacheManager cacheManager;
    private HalloweenWorldFeatureManager worldFeatureManager;
    private HalloweenVillageDiscoveryManager villageDiscoveryManager;
    private HalloweenSecretDiscoveryManager secretDiscoveryManager;
    private HalloweenVampireEncounterManager vampireEncounterManager;
    private HalloweenWorldDecorator worldDecorator;
    private final Set<String> reportedConfigErrors = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        disableLegacyContentSystems();

        dataManager = new HalloweenDataManager(this);
        if (!dataManager.load()) {
            getLogger().severe("HalloweenCore is disabling itself because player data could not be loaded safely.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        eventEnabled = getConfig().getBoolean("enabled", true);
        service = new HalloweenServiceImpl(this);
        eventManager = new HalloweenEventManager(this);
        vanillaEventManager = new HalloweenVanillaEventManager(this);
        vanillaAtmosphereManager = new HalloweenVanillaAtmosphereManager(this);
        rewardManager = new HalloweenRewardManager(this);
        atmosphere = new HalloweenAtmosphere(this);
        mobManager = new HalloweenMobManager(this);
        challengeManager = new HalloweenChallengeManager(this);
        questManager = new HalloweenQuestManager(this);
        bossManager = new HalloweenBossManager(this);
        vanillaBossManager = new HalloweenVanillaBossManager(this);
        itemManager = new HalloweenItemManager(this);
        passiveEffectManager = new HalloweenPassiveEffectManager(this);
        pumpkinVillagerManager = new HalloweenPumpkinVillagerManager(this);
        cacheManager = new HalloweenCacheManager(this);
        worldFeatureManager = new HalloweenWorldFeatureManager(this);
        villageDiscoveryManager = new HalloweenVillageDiscoveryManager(this);
        secretDiscoveryManager = new HalloweenSecretDiscoveryManager(this);
        vampireEncounterManager = new HalloweenVampireEncounterManager(this);
        worldDecorator = new HalloweenWorldDecorator(this);

        logConfigValidationIssues();

        getServer().getPluginManager().registerEvents(new HalloweenActivityListener(this), this);
        getServer().getPluginManager().registerEvents(questManager, this);
        getServer().getPluginManager().registerEvents(new HalloweenJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenItemListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenRewardMenuListener(this), this);
        getServer().getPluginManager().registerEvents(passiveEffectManager, this);
        getServer().getPluginManager().registerEvents(pumpkinVillagerManager, this);
        getServer().getPluginManager().registerEvents(cacheManager, this);
        getServer().getPluginManager().registerEvents(worldFeatureManager, this);
        getServer().getPluginManager().registerEvents(secretDiscoveryManager, this);
        getServer().getPluginManager().registerEvents(vanillaEventManager, this);
        getServer().getPluginManager().registerEvents(mobManager, this);
        getServer().getPluginManager().registerEvents(vanillaBossManager, this);
        vanillaBossManager.resumeLoadedBosses();
        // Custom mob models, Haunted Village, custom ambience, and decoration listeners
        // remain retired. The elite mob listener now only marks existing vanilla entity
        // types and supplies particle/effect abilities, with all model APIs removed.

        if (getCommand("halloween") != null) {
            HalloweenCommand halloweenCommand = new HalloweenCommand(this);
            getCommand("halloween").setExecutor(halloweenCommand);
            getCommand("halloween").setTabCompleter(halloweenCommand);
        }

        long saveInterval = 20L * 60L * 5L;
        getServer().getScheduler().runTaskTimer(this, dataManager::save, saveInterval, saveInterval);
        eventManager.start();
        vanillaEventManager.start();
        vanillaAtmosphereManager.start();
        // Retired custom soundtrack, world decoration, and boss systems remain stopped.
        worldDecorator.stop();
        atmosphere.stopPlayback();
        vampireEncounterManager.stopNaturalSummoningMonitor();
        passiveEffectManager.start();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (new HalloweenPlaceholderExpansion(this).register()) {
                getLogger().info("PlaceholderAPI expansion registered: %halloween_*%");
            } else {
                getLogger().warning("PlaceholderAPI is present, but Halloween expansion could not be registered.");
            }
        }

        getLogger().info("HalloweenCore enabled. Event=" + eventEnabled);
    }

    @Override
    public void onDisable() {
        if (eventManager != null) eventManager.stop();
        if (vanillaBossManager != null) vanillaBossManager.shutdown();
        if (vanillaEventManager != null) vanillaEventManager.stop();
        if (vanillaAtmosphereManager != null) vanillaAtmosphereManager.stop();
        if (vampireEncounterManager != null) {
            vampireEncounterManager.stopNaturalSummoningMonitor();
            vampireEncounterManager.stopEncounter();
        }
        if (atmosphere != null) atmosphere.stopPlayback();
        if (worldDecorator != null) worldDecorator.stop();
        if (passiveEffectManager != null) passiveEffectManager.stop();
        if (dataManager != null) dataManager.save();
    }

    /**
     * Removes configuration left by the retired custom-asset implementation.
     * This deliberately runs on every startup and config reload so an old server
     * config cannot silently reactivate the systems while they are being replaced.
     */
    private void disableLegacyContentSystems() {
        getConfig().set("random-events.enabled", false);
        migrateVanillaBloodMoonInvasion();
        migrateVanillaEliteMobs();
        // Custom mob models and Haunted Village maps stay retired even when vanilla
        // elite creatures are enabled. Nothing here requires MythicMobs or ModelEngine.
        getConfig().set("special-mobs.custom-models.enabled", false);
        getConfig().set("special-mobs.map-drop-chance", 0.0D);
        // Upgrade configs from the vanilla-first cleanup that had story quests disabled.
        // After this one-time migration, admins can still disable the campaign in config.yml.
        if (!getConfig().getBoolean("migrations.vanilla-story-quests-v1", false)) {
            getConfig().set("story-quests.enabled", true);
            getConfig().set("migrations.vanilla-story-quests-v1", true);
        }
        getConfig().set("haunted-village.enabled", false);
        // Keep vanilla hidden discoveries while retiring the old village system.
        migrateVanillaSecretDiscoveries();
        getConfig().set("world-decorations.enabled", false);
        getConfig().set("atmosphere.enabled", false);
        getConfig().set("atmosphere.send-title-on-join", false);
        getConfig().set("bosses.vampire.enabled", false);
        getConfig().set("bosses.vampire.summoning.automatic", false);
        migrateVanillaBossConfig();

        // One-time migration for servers that already have a config.yml from an
        // older release. saveDefaultConfig() does not merge new reward entries.
        if (!getConfig().getBoolean("migrations.cursed-candy-reward-v1", false)) {
            if (!getConfig().contains("rewards.shop.cursed-candy", true)) {
                getConfig().set("rewards.shop.cursed-candy.cost", 500L);
                getConfig().set("rewards.shop.cursed-candy.min-curse", 0);
                getConfig().set("rewards.shop.cursed-candy.itemsadder-id",
                        "warriorland_halloween:cursed_candy");
                getConfig().set("rewards.shop.cursed-candy.material", "PUMPKIN_PIE");
                getConfig().set("rewards.shop.cursed-candy.amount", 1);
                getConfig().set("rewards.shop.cursed-candy.name",
                        "&6&lProkleté cukroví &8[Halloween 2026]");
                getConfig().set("rewards.shop.cursed-candy.lore", List.of(
                        "&7Limitovaná sladkost pro Halloween.",
                        "&7Po snězení získáš krátké posílení.",
                        "&8Jednorázová odměna."
                ));
            }
            getConfig().set("migrations.cursed-candy-reward-v1", true);
        }

        for (String id : List.of("crimson-warden-helmet", "crimson-warden-chestplate",
                "crimson-warden-leggings", "crimson-warden-boots", "crimson-warden-sword",
                "crimson-warden-pickaxe", "crimson-warden-axe", "crimson-warden-shovel",
                "crimson-warden-hoe")) {
            getConfig().set("rewards.shop." + id, null);
        }
        for (String id : List.of("hunter-mask", "cursed-talisman", "halloween-token")) {
            getConfig().set("rewards.shop." + id + ".itemsadder-id", null);
        }
        getConfig().set("migrations.legacy-content-cleanup-v1", true);
        saveConfig();
    }


    /**
     * Migrates the old invasion tuning onto the vanilla Blood Moon scheduler. The
     * retired random-event engine itself remains disabled and is never started.
     */
    private void migrateVanillaBloodMoonInvasion() {
        if (getConfig().getBoolean("migrations.vanilla-blood-moon-invasion-v1", false)) return;
        int oldWaveSize = Math.max(1, Math.min(8,
                getConfig().getInt("random-events.invasion-mobs-per-surge", 4)));
        int oldMobCap = Math.max(1, Math.min(80,
                getConfig().getInt("random-events.max-event-mobs", 48)));
        setConfigDefaultIfMissing("vanilla-events.blood-moon.invasion.enabled", true);
        setConfigDefaultIfMissing("vanilla-events.blood-moon.invasion.wave-interval-seconds", 25);
        setConfigDefaultIfMissing("vanilla-events.blood-moon.invasion.mobs-per-wave", oldWaveSize);
        setConfigDefaultIfMissing("vanilla-events.blood-moon.invasion.max-event-mobs", oldMobCap);
        setConfigDefaultIfMissing("vanilla-events.blood-moon.invasion.captain-enabled", true);
        setConfigDefaultIfMissing("vanilla-events.blood-moon.invasion.captain-final-seconds", 60);
        getConfig().set("migrations.vanilla-blood-moon-invasion-v1", true);
    }

    /**
     * Adds defaults for the optional vanilla final boss while keeping the old
     * model-required Vampire King encounter permanently disabled.
     */
    private void migrateVanillaBossConfig() {
        if (getConfig().getBoolean("migrations.vanilla-boss-v1", false)) return;
        setConfigDefaultIfMissing("vanilla-boss.enabled", true);
        setConfigDefaultIfMissing("vanilla-boss.max-health", 240.0D);
        setConfigDefaultIfMissing("vanilla-boss.tracking-radius-blocks", 96.0D);
        setConfigDefaultIfMissing("vanilla-boss.pulse.interval-seconds", 18);
        setConfigDefaultIfMissing("vanilla-boss.pulse.radius-blocks", 8.0D);
        setConfigDefaultIfMissing("vanilla-boss.pulse.damage", 4.0D);
        setConfigDefaultIfMissing("vanilla-boss.pulse.windup-ticks", 30);
        setConfigDefaultIfMissing("vanilla-boss.pulse.dodge-radius-blocks", 2.0D);
        setConfigDefaultIfMissing("vanilla-boss.pulse.wither-ticks", 60);
        setConfigDefaultIfMissing("vanilla-boss.rewards.minimum-participation-seconds", 120);
        setConfigDefaultIfMissing("vanilla-boss.rewards.minimum-damage-percent", 0.025D);
        setConfigDefaultIfMissing("vanilla-boss.rewards.participation-fragments", 300L);
        setConfigDefaultIfMissing("vanilla-boss.rewards.top-contributor-bonus-fragments", 250L);
        getConfig().set("migrations.vanilla-boss-v1", true);
    }

    /**
     * Restores rare elite variants of vanilla hostile mobs without reactivating any
     * MythicMobs/ModelEngine model or old Haunted Village reward path.
     */
    private void migrateVanillaEliteMobs() {
        if (getConfig().getBoolean("migrations.vanilla-elite-mobs-v1", false)) return;

        getConfig().set("special-mobs.enabled", true);
        setConfigDefaultIfMissing("special-mobs.chance", 0.04D);
        setConfigDefaultIfMissing("special-mobs.event-chance-multiplier", 1.5D);
        setConfigDefaultIfMissing("special-mobs.bonus-fragments", 12L);
        // The cleanup build forced this to zero; restore a restrained candy drop.
        getConfig().set("special-mobs.relic-drop-chance", 0.08D);
        getConfig().set("special-mobs.relic-item-id", "warriorland_halloween:cursed_candy");
        getConfig().set("special-mobs.map-drop-chance", 0.0D);
        getConfig().set("special-mobs.map-item-id", "minecraft:map");
        getConfig().set("special-mobs.custom-models.enabled", false);

        addDefaultEliteProfile("cursed-zombie", "&5&lProkletý zombie", 1.35D, 0, 0, 12L);
        addDefaultEliteProfile("void-reaper", "&5&lŽenec prázdnoty", 1.65D, 0, 1, 13L);
        addDefaultEliteProfile("gravekeeper", "&8&lHrobník", 1.45D, 0, 0, 9L);
        addDefaultEliteProfile("frost-stalker", "&b&lLedový stopař", 1.40D, 1, 0, 10L);
        addDefaultEliteProfile("blood-spider", "&c&lKrvavý pavouk", 1.25D, 1, 0, 8L);
        addDefaultEliteProfile("nightmare", "&d&lNoční můra", 1.75D, 1, 1, 12L);
        addDefaultEliteProfile("pumpkin-wraith", "&6&lDýňový přízrak", 1.60D, 0, 1, 10L);
        addDefaultEliteProfile("hex-witch", "&5&lHexová čarodějka", 1.30D, 0, 0, 10L);

        setConfigDefaultIfMissing("special-mobs.types.cursed-zombie.soul-siphon-blindness-ticks", 35);
        setConfigDefaultIfMissing("special-mobs.types.cursed-zombie.soul-siphon-weakness-ticks", 80);
        setConfigDefaultIfMissing("special-mobs.types.void-reaper.gloom-ticks", 70);
        setConfigDefaultIfMissing("special-mobs.types.gravekeeper.grave-mark-delay-ticks", 24);
        setConfigDefaultIfMissing("special-mobs.types.gravekeeper.grave-mark-radius-blocks", 1.5D);
        setConfigDefaultIfMissing("special-mobs.types.gravekeeper.grave-mark-damage", 3.0D);
        setConfigDefaultIfMissing("special-mobs.types.frost-stalker.frostbite-ticks", 80);
        setConfigDefaultIfMissing("special-mobs.types.blood-spider.blood-web-poison-ticks", 80);
        setConfigDefaultIfMissing("special-mobs.types.blood-spider.blood-web-slowness-ticks", 45);
        setConfigDefaultIfMissing("special-mobs.types.nightmare.night-terror-ticks", 60);
        setConfigDefaultIfMissing("special-mobs.types.pumpkin-wraith.explosion-radius", 3);
        setConfigDefaultIfMissing("special-mobs.types.pumpkin-wraith.ash-flash-radius-blocks", 5.0D);
        setConfigDefaultIfMissing("special-mobs.types.pumpkin-wraith.ash-flash-nausea-ticks", 70);
        setConfigDefaultIfMissing("special-mobs.types.pumpkin-wraith.ash-flash-blindness-ticks", 30);
        setConfigDefaultIfMissing("special-mobs.types.hex-witch.random-hex-duration-ticks", 65);
        getConfig().set("migrations.vanilla-elite-mobs-v1", true);
    }

    private void addDefaultEliteProfile(String id, String name, double healthMultiplier,
                                        int speedLevel, int strengthLevel, long cooldownSeconds) {
        String path = "special-mobs.types." + id;
        setConfigDefaultIfMissing(path + ".name", name);
        setConfigDefaultIfMissing(path + ".glowing", true);
        setConfigDefaultIfMissing(path + ".health-multiplier", healthMultiplier);
        setConfigDefaultIfMissing(path + ".speed-level", speedLevel);
        setConfigDefaultIfMissing(path + ".strength-level", strengthLevel);
        setConfigDefaultIfMissing(path + ".ability-cooldown-seconds", cooldownSeconds);
    }

    /**
     * One-time restore of the vanilla-only hidden discovery system. The migration
     * merges the three stock hidden discoveries into existing configurations while
     * preserving any administrator-defined locations and coordinates.
     */
    private void migrateVanillaSecretDiscoveries() {
        // v2 intentionally runs even when v1 was already marked: it merges missing
        // stock entries into configurations that previously contained only custom IDs.
        if (getConfig().getBoolean("migrations.vanilla-secret-discoveries-v2", false)) return;

        getConfig().set("secret-discoveries.enabled", true);
        setConfigDefaultIfMissing("secret-discoveries.default-radius-blocks", 4.5D);
        setConfigDefaultIfMissing("secret-discoveries.default-reward-fragments", 250L);

        addDefaultSecret("blood-altar", "Krvavý oltář",
                "Někde poblíž starých kamenů se v noci ozývá tlukot.", 250L);
        addDefaultSecret("witch-den", "Doupě hexové čarodějky",
                "Hledej místo, kde popel zůstává teplý i po dešti.", 300L);
        addDefaultSecret("forgotten-grave", "Zapomenutý hrob",
                "Za posledním náhrobkem může něco čekat pod zemí.", 350L);

        getConfig().set("migrations.vanilla-secret-discoveries-v1", true);
        getConfig().set("migrations.vanilla-secret-discoveries-v2", true);
    }

    private void addDefaultSecret(String id, String name, String hint, long reward) {
        String path = "secret-discoveries.locations." + id;
        // Fill only missing values. If an existing server already configured
        // this ID or added custom coordinates, its values must never be reset.
        setConfigDefaultIfMissing(path + ".name", name);
        setConfigDefaultIfMissing(path + ".hint", hint);
        setConfigDefaultIfMissing(path + ".configured", false);
        setConfigDefaultIfMissing(path + ".world", "");
        setConfigDefaultIfMissing(path + ".x", 0.0D);
        setConfigDefaultIfMissing(path + ".y", 0.0D);
        setConfigDefaultIfMissing(path + ".z", 0.0D);
        setConfigDefaultIfMissing(path + ".radius-blocks", 4.5D);
        setConfigDefaultIfMissing(path + ".reward-fragments", reward);
    }

    private void setConfigDefaultIfMissing(String path, Object value) {
        // ignoreDefault=true checks the server's explicit config rather than values
        // inherited from the plugin JAR; this is what makes the migration safe.
        if (!getConfig().contains(path, true)) {
            getConfig().set(path, value);
        }
    }

    /**
     * Reload runtime state after config edits. This cannot load changed Java classes;
     * updating the plugin JAR itself still requires one server restart.
     */
    public void reloadEventConfig() {
        reloadConfig();
        disableLegacyContentSystems();
        eventEnabled = getConfig().getBoolean("enabled", true);

        if (eventManager != null) eventManager.stop();
        if (vampireEncounterManager != null) {
            vampireEncounterManager.stopNaturalSummoningMonitor();
            vampireEncounterManager.stopEncounter();
        }
        if (bossManager != null) bossManager.stopVampireBossBar();
        if (atmosphere != null) atmosphere.stopPlayback();
        if (worldDecorator != null) worldDecorator.stop();

        logConfigValidationIssues();
        if (vanillaEventManager != null) vanillaEventManager.restart();
        if (vanillaAtmosphereManager != null) vanillaAtmosphereManager.restart();
        getLogger().info("HalloweenCore runtime state reloaded. enabled=" + eventEnabled
                + ", random-events=" + getConfig().getBoolean("random-events.enabled", false)
                + ", atmosphere=" + getConfig().getBoolean("atmosphere.enabled", false)
                + ", world-decorations=" + getConfig().getBoolean("world-decorations.enabled", false));
    }

    public void setVanillaAtmosphereEnabled(boolean enabled) {
        boolean changed = getConfig().getBoolean("vanilla-atmosphere.enabled", true) != enabled;
        getConfig().set("vanilla-atmosphere.enabled", enabled);
        if (changed) saveConfig();

        if (vanillaAtmosphereManager != null) {
            vanillaAtmosphereManager.restart();
        }
    }

    public void setEventEnabled(boolean enabled) {
        boolean changed = eventEnabled != enabled;
        eventEnabled = enabled;
        if (changed) {
            getConfig().set("enabled", enabled);
            saveConfig();
        }

        if (eventManager != null) eventManager.stop();
        if (atmosphere != null) atmosphere.stopPlayback();
        if (worldDecorator != null) worldDecorator.stop();
        if (vampireEncounterManager != null) {
            vampireEncounterManager.stopNaturalSummoningMonitor();
            vampireEncounterManager.stopEncounter();
        }
        if (bossManager != null) bossManager.stopVampireBossBar();

        if (vanillaEventManager != null) {
            if (enabled) vanillaEventManager.start();
            else vanillaEventManager.stop();
        }
        if (vanillaAtmosphereManager != null) {
            if (enabled) vanillaAtmosphereManager.start();
            else vanillaAtmosphereManager.stop();
        }

        if (changed) {
            Bukkit.broadcastMessage(color(enabled
                    ? "&6&lHALLOWEEN &8» &eHalloween je zapnutý. Získávej fragmenty a plň výzvy!"
                    : "&8[HALLOWEEN] &7Halloween systém byl vypnut."));
        } else if (enabled) {
            getLogger().info("Halloween progression is already enabled; legacy random events remain disabled.");
        }
    }


    private void logConfigValidationIssues() {
        var errors = HalloweenConfigValidator.validate(this);
        Set<String> currentErrors = new HashSet<>(errors);
        for (String error : errors) {
            if (reportedConfigErrors.add(error)) {
                getLogger().severe("[CONFIG] " + error);
            }
        }
        // If an admin fixes an issue, allow the same error to be reported again
        // if it comes back later, without printing the same error every reload.
        reportedConfigErrors.retainAll(currentErrors);
    }

    public boolean isEventEnabled() {
        return eventEnabled;
    }

    public HalloweenEventManager getEventManager() {
        return eventManager;
    }

    public HalloweenVanillaEventManager getVanillaEventManager() {
        return vanillaEventManager;
    }

    public HalloweenVanillaAtmosphereManager getVanillaAtmosphereManager() {
        return vanillaAtmosphereManager;
    }

    public HalloweenRewardManager getRewardManager() {
        return rewardManager;
    }

    public HalloweenAtmosphere getAtmosphere() {
        return atmosphere;
    }

    public HalloweenMobManager getMobManager() {
        return mobManager;
    }

    public HalloweenChallengeManager getChallengeManager() {
        return challengeManager;
    }

    public HalloweenPumpkinVillagerManager getPumpkinVillagerManager() {
        return pumpkinVillagerManager;
    }

    public HalloweenCacheManager getCacheManager() {
        return cacheManager;
    }

    public HalloweenWorldFeatureManager getWorldFeatureManager() {
        return worldFeatureManager;
    }

    public HalloweenQuestManager getQuestManager() {
        return questManager;
    }

    public HalloweenBossManager getBossManager() {
        return bossManager;
    }

    public HalloweenVanillaBossManager getVanillaBossManager() {
        return vanillaBossManager;
    }

    public HalloweenItemManager getItemManager() {
        return itemManager;
    }

    public HalloweenDataManager getDataManager() {
        return dataManager;
    }

    public HalloweenService getService() {
        return service;
    }

    public HalloweenVampireEncounterManager getVampireEncounterManager() {
        return vampireEncounterManager;
    }

    public HalloweenSecretDiscoveryManager getSecretDiscoveryManager() {
        return secretDiscoveryManager;
    }

    public void notifyFragmentGain(UUID playerId, long amount) {
        Player player = getServer().getPlayer(playerId);
        if (player == null) return;

        String currency = getConfig().getString("currency.name", "fragmentů");
        player.sendActionBar(Component.text("+" + amount + " " + currency).color(NamedTextColor.GOLD));
    }

    public void broadcastGlobalGoalReached() {
        String message = getConfig().getString("messages.global-goal-reached",
                "&6&lHALLOWEEN &8» &fServer společně dosáhl Halloween cíle! Něco se probouzí...");
        getServer().broadcastMessage(color(message));
    }

    public String message(String key) {
        FileConfiguration config = getConfig();
        String prefix = config.getString("messages.prefix", "");
        return color(prefix + config.getString(key, ""));
    }

    public boolean isEligibleGameplayWorld(World world) {
        if (world == null) return false;

        java.util.List<String> worlds = getConfig().getStringList("gameplay.worlds.names");
        if (worlds.isEmpty()) return true;

        boolean listed = worlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
        String mode = getConfig().getString("gameplay.worlds.mode", "BLACKLIST");
        if ("WHITELIST".equalsIgnoreCase(mode)) {
            return listed;
        }
        return !listed;
    }

    public boolean isEligibleGameplayPlayer(Player player) {
        if (player == null) return false;

        java.util.List<String> allowed = getConfig().getStringList("gameplay.allowed-gamemodes");
        if (allowed.isEmpty()) {
            return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
        }

        for (String mode : allowed) {
            try {
                if (player.getGameMode() == GameMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid config entries.
            }
        }
        return false;
    }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
