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
    private HalloweenRewardManager rewardManager;
    private HalloweenAtmosphere atmosphere;
    private HalloweenMobManager mobManager;
    private HalloweenChallengeManager challengeManager;
    private HalloweenQuestManager questManager;
    private HalloweenBossManager bossManager;
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
        rewardManager = new HalloweenRewardManager(this);
        atmosphere = new HalloweenAtmosphere(this);
        mobManager = new HalloweenMobManager(this);
        challengeManager = new HalloweenChallengeManager(this);
        questManager = new HalloweenQuestManager(this);
        bossManager = new HalloweenBossManager(this);
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
        getServer().getPluginManager().registerEvents(new HalloweenJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenItemListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenRewardMenuListener(this), this);
        getServer().getPluginManager().registerEvents(passiveEffectManager, this);
        getServer().getPluginManager().registerEvents(pumpkinVillagerManager, this);
        getServer().getPluginManager().registerEvents(cacheManager, this);
        getServer().getPluginManager().registerEvents(worldFeatureManager, this);
        // Legacy mob, quest, discovery, ambience and decoration listeners remain
        // unregistered; only the safe vanilla cache and new-chunk feature managers run.

        if (getCommand("halloween") != null) {
            HalloweenCommand halloweenCommand = new HalloweenCommand(this);
            getCommand("halloween").setExecutor(halloweenCommand);
            getCommand("halloween").setTabCompleter(halloweenCommand);
        }

        long saveInterval = 20L * 60L * 5L;
        getServer().getScheduler().runTaskTimer(this, dataManager::save, saveInterval, saveInterval);
        eventManager.start();
        // Retired ambience, world decoration, and boss systems remain stopped.
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
        getConfig().set("special-mobs.enabled", false);
        getConfig().set("special-mobs.custom-models.enabled", false);
        getConfig().set("special-mobs.relic-drop-chance", 0.0D);
        getConfig().set("special-mobs.map-drop-chance", 0.0D);
        getConfig().set("story-quests.enabled", false);
        getConfig().set("haunted-village.enabled", false);
        getConfig().set("secret-discoveries.enabled", false);
        getConfig().set("world-decorations.enabled", false);
        getConfig().set("atmosphere.enabled", false);
        getConfig().set("atmosphere.send-title-on-join", false);
        getConfig().set("bosses.vampire.enabled", false);
        getConfig().set("bosses.vampire.summoning.automatic", false);

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
        getLogger().info("HalloweenCore runtime state reloaded. enabled=" + eventEnabled
                + ", random-events=" + getConfig().getBoolean("random-events.enabled", false)
                + ", atmosphere=" + getConfig().getBoolean("atmosphere.enabled", false)
                + ", world-decorations=" + getConfig().getBoolean("world-decorations.enabled", false));
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
