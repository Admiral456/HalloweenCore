package cz.halloween.core;

import org.bukkit.Bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
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

public final class HalloweenCore extends JavaPlugin implements Listener {
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
    private HalloweenVillageDiscoveryManager villageDiscoveryManager;
    private HalloweenSecretDiscoveryManager secretDiscoveryManager;
    private HalloweenVampireEncounterManager vampireEncounterManager;
    private HalloweenWorldDecorator worldDecorator;
    private HalloweenStructureGenerator structureGenerator;
    private final Set<String> reportedConfigErrors = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateAtmosphereVolume();
        migrateAtmospherePlaylistDefaults();
        migrateCrimsonWardenShopDefaults();
        migrateCrimsonWardenShieldDefaults();
        migrateCrimsonWardenArmorStats();
        migrateCrimsonWardenLoreV4();
        migrateCrimsonWardenLoreV5();
        migrateCrimsonWardenCustomArmorV1();
        migrateHalloween3dDecorationsV1();
        migrateHalloweenDecorationReliabilityV2();
        migrateGraveyardChanceTripledV1();
        migrateHalloweenEventOverhaul();
        migrateAdditionalMobAndQuestDefaults();
        migrateNightNickAndVisualDefaults();

        dataManager = new HalloweenDataManager(this);
        dataManager.load();

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
        villageDiscoveryManager = new HalloweenVillageDiscoveryManager(this);
        secretDiscoveryManager = new HalloweenSecretDiscoveryManager(this);
        vampireEncounterManager = new HalloweenVampireEncounterManager(this);
        worldDecorator = new HalloweenWorldDecorator(this);
        structureGenerator = new HalloweenStructureGenerator(this);

        logConfigValidationIssues();
        bossManager.validateConfiguration();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new HalloweenActivityListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenItemListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenRewardMenuListener(this), this);
        getServer().getPluginManager().registerEvents(villageDiscoveryManager, this);
        getServer().getPluginManager().registerEvents(secretDiscoveryManager, this);
        getServer().getPluginManager().registerEvents(vampireEncounterManager, this);
        getServer().getPluginManager().registerEvents(mobManager, this);
        getServer().getPluginManager().registerEvents(questManager, this);
        getServer().getPluginManager().registerEvents(worldDecorator, this);
        getServer().getPluginManager().registerEvents(structureGenerator, this);
        getServer().getPluginManager().registerEvents(passiveEffectManager, this);

        if (getCommand("halloween") != null) {
            HalloweenCommand halloweenCommand = new HalloweenCommand(this);
            getCommand("halloween").setExecutor(halloweenCommand);
            getCommand("halloween").setTabCompleter(halloweenCommand);
        }

        long saveInterval = 20L * 60L * 5L;
        getServer().getScheduler().runTaskTimer(this, dataManager::save, saveInterval, saveInterval);
        eventManager.start();
        mobManager.startNightPatrol();
        vampireEncounterManager.startNaturalSummoningMonitor();
        atmosphere.start();
        worldDecorator.start();
        structureGenerator.start();
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
        if (mobManager != null) mobManager.stopNightPatrol();
        if (vampireEncounterManager != null) {
            vampireEncounterManager.stopNaturalSummoningMonitor();
            vampireEncounterManager.stopEncounter();
        }
        if (atmosphere != null) atmosphere.stopPlayback();
        if (worldDecorator != null) worldDecorator.stop();
        if (structureGenerator != null) structureGenerator.stop();
        if (passiveEffectManager != null) passiveEffectManager.stop();
        if (dataManager != null) dataManager.save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!eventEnabled) return;
        if (vampireEncounterManager != null && vampireEncounterManager.isTrackedVampireBoss(event.getEntity())) return;
        if (mobManager != null && mobManager.isTestMob(event.getEntity())) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (!isEligibleGameplayPlayer(killer)) return;
        if (!isEligibleGameplayWorld(killer.getWorld())) return;

        if (!getConfig().getBoolean("rewards.mob-kill.enabled", true)) return;

        long reward = getConfig().getLong("rewards.mob-kill.fragments", 1L);
        if (reward <= 0L) return;

        service.addFragments(killer.getUniqueId(), reward, "mob-kill");
    }

    /**
     * Reload runtime state after config edits. This cannot load changed Java classes;
     * updating the plugin JAR itself still requires one server restart.
     */
    public void reloadEventConfig() {
        reloadConfig();
        migrateAtmosphereVolume();
        migrateCrimsonWardenShopDefaults();
        migrateCrimsonWardenShieldDefaults();
        migrateCrimsonWardenArmorStats();
        migrateCrimsonWardenLoreV4();
        migrateCrimsonWardenLoreV5();
        migrateCrimsonWardenCustomArmorV1();
        migrateHalloween3dDecorationsV1();
        migrateHalloweenDecorationReliabilityV2();
        migrateGraveyardChanceTripledV1();
        migrateHalloweenEventOverhaul();
        migrateAdditionalMobAndQuestDefaults();
        migrateNightNickAndVisualDefaults();
        eventEnabled = getConfig().getBoolean("enabled", true);

        if (eventManager != null) {
            if (eventEnabled && getConfig().getBoolean("random-events.enabled", true)) {
                // Make the refreshed state visible shortly after /halloween reload.
                eventManager.scheduleFirstEvent();
            } else {
                eventManager.stop();
            }
        }
        if (vampireEncounterManager != null) vampireEncounterManager.stopEncounter();
        if (bossManager != null) bossManager.stopVampireBossBar();

        if (atmosphere != null) atmosphere.refreshPlayback();
        if (worldDecorator != null) {
            if (getConfig().getBoolean("world-decorations.enabled", true)) {
                // World ambience is independent of the event gameplay toggle. Never
                // re-scan all chunks on the standard reload path; avoid packet bursts.
                worldDecorator.resumeWithoutScanning();
            } else {
                worldDecorator.stop();
            }
        }
        if (structureGenerator != null) {
            if (getConfig().getBoolean("random-structures.enabled", true)) {
                // The generator only processes chunks without its persistent marker;
                // already generated/checked chunks are never rebuilt.
                structureGenerator.scanLoadedChunks();
            } else {
                structureGenerator.stop();
            }
        }

        logConfigValidationIssues();
        if (bossManager != null) bossManager.validateConfiguration();
        getLogger().info("HalloweenCore runtime state reloaded. enabled=" + eventEnabled
                + ", random-events=" + getConfig().getBoolean("random-events.enabled", true)
                + ", atmosphere=" + getConfig().getBoolean("atmosphere.enabled", true)
                + ", world-decorations=" + getConfig().getBoolean("world-decorations.enabled", true));
    }

    private void migrateNightNickAndVisualDefaults() {
        if (getConfig().getBoolean("migrations.halloween-night-nick-visuals-v1", false)) return;
        if (!getConfig().isSet("random-events.types.witching-hour.darkness-duration-ticks")) getConfig().set("random-events.types.witching-hour.darkness-duration-ticks", 180);
        if (!getConfig().isSet("random-events.types.blood-moon-invasion.red-moon-enabled")) getConfig().set("random-events.types.blood-moon-invasion.red-moon-enabled", true);
        if (!getConfig().isSet("random-events.types.blood-moon-invasion.red-moon-refresh-interval-ticks")) getConfig().set("random-events.types.blood-moon-invasion.red-moon-refresh-interval-ticks", 30);
        if (!getConfig().isSet("special-mobs.headless-nick.enabled")) getConfig().set("special-mobs.headless-nick.enabled", true);
        if (!getConfig().isSet("special-mobs.headless-nick.spawn-check-interval-seconds")) getConfig().set("special-mobs.headless-nick.spawn-check-interval-seconds", 30);
        if (!getConfig().isSet("special-mobs.headless-nick.spawn-chance-per-check")) getConfig().set("special-mobs.headless-nick.spawn-chance-per-check", 0.12D);
        if (!getConfig().isSet("special-mobs.headless-nick.world-cooldown-minutes")) getConfig().set("special-mobs.headless-nick.world-cooldown-minutes", 8);
        if (!getConfig().isSet("special-mobs.types.headless-nick.name")) getConfig().set("special-mobs.types.headless-nick.name", "&4&lBEZHLAVÝ NICK");
        if (!getConfig().isSet("special-mobs.types.headless-nick.glowing")) getConfig().set("special-mobs.types.headless-nick.glowing", false);
        if (!getConfig().isSet("special-mobs.types.headless-nick.health-multiplier")) getConfig().set("special-mobs.types.headless-nick.health-multiplier", 2.8D);
        if (!getConfig().isSet("special-mobs.types.headless-nick.ability-cooldown-seconds")) getConfig().set("special-mobs.types.headless-nick.ability-cooldown-seconds", 10);
        if (!getConfig().isSet("special-mobs.types.headless-nick.charge-slow-ticks")) getConfig().set("special-mobs.types.headless-nick.charge-slow-ticks", 100);
        if (!getConfig().isSet("special-mobs.types.headless-nick.charge-wither-ticks")) getConfig().set("special-mobs.types.headless-nick.charge-wither-ticks", 60);
        if (!getConfig().isSet("world-decorations.haunted-soil-patches-per-chunk")) getConfig().set("world-decorations.haunted-soil-patches-per-chunk", 3);
        getConfig().set("migrations.halloween-night-nick-visuals-v1", true);
        saveConfig();
        getLogger().info("Halloween night-Nick, Blood Moon visual and haunted soil defaults migrated.");
    }

    public void setEventEnabled(boolean enabled) {
        boolean changed = eventEnabled != enabled;
        eventEnabled = enabled;
        if (changed) {
            getConfig().set("enabled", enabled);
            saveConfig();
        }

        if (eventManager != null) {
            if (enabled) {
                // Even if the switch was already ON, give the administrator a visible,
                // near-term event instead of silently retaining a 20–35 minute timer.
                eventManager.scheduleFirstEvent();
            } else {
                eventManager.stop();
                if (vampireEncounterManager != null) vampireEncounterManager.stopEncounter();
                if (bossManager != null) bossManager.stopVampireBossBar();
            }
        }
        if (atmosphere != null) atmosphere.refreshPlayback();
        // Ambient world generation isn't toggled off by /halloween off.
        if (worldDecorator != null) {
            if (getConfig().getBoolean("world-decorations.enabled", true)) worldDecorator.scanLoadedChunks();
            else worldDecorator.stop();
        }
        if (structureGenerator != null) {
            if (getConfig().getBoolean("random-structures.enabled", true)) structureGenerator.scanLoadedChunks();
            else structureGenerator.stop();
        }

        if (changed) {
            Bukkit.broadcastMessage(color(enabled
                    ? "&6&lHALLOWEEN &8» &eHalloween je zapnutý. První událost dorazí za chvíli!"
                    : "&8[HALLOWEEN] &7Halloween event byl vypnut."));
        } else if (enabled) {
            getLogger().info("Halloween event is already enabled; next event scheduled shortly.");
        }
    }


    private void migrateAtmospherePlaylistDefaults() {
        if (getConfig().getBoolean("migrations.atmosphere-playlist-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged config.yml unavailable; atmosphere playlist migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String sectionPath : List.of("atmosphere.playlist", "atmosphere.track-loop-milliseconds")) {
                ConfigurationSection section = defaults.getConfigurationSection(sectionPath);
                if (section == null) continue;
                for (String key : section.getKeys(false)) {
                    String path = sectionPath + "." + key;
                    if (!getConfig().contains(path)) {
                        getConfig().set(path, defaults.get(path));
                    }
                }
            }
            getConfig().set("migrations.atmosphere-playlist-v1", true);
            saveConfig();
            getLogger().info("Halloween night/cave audio playlist settings applied; existing atmosphere settings were preserved.");
        } catch (IOException ex) {
            getLogger().warning("Atmosphere playlist migration failed: " + ex.getClass().getSimpleName());
        }
    }

    private void migrateAdditionalMobAndQuestDefaults() {
        if (getConfig().getBoolean("migrations.additional-mobs-quests-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged config.yml unavailable; additional mob/quest migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String sectionPath : List.of(
                    "story-quests",
                    "special-mobs.types.void-reaper",
                    "special-mobs.types.frost-stalker",
                    "special-mobs.types.nightmare")) {
                ConfigurationSection section = defaults.getConfigurationSection(sectionPath);
                if (section == null) continue;
                for (String key : section.getKeys(true)) {
                    if (section.isConfigurationSection(key)) continue;
                    String path = sectionPath + "." + key;
                    if (!getConfig().contains(path)) getConfig().set(path, section.get(key));
                }
            }
            getConfig().set("migrations.additional-mobs-quests-v1", true);
            saveConfig();
            getLogger().info("Additional Halloween mob and story-quest defaults applied without overwriting existing settings.");
        } catch (IOException ex) {
            getLogger().warning("Additional mob/quest migration failed: " + ex.getClass().getSimpleName());
        }
    }

    private void migrateAtmosphereVolume() {
        // One-time migration for servers that already have the older 0.35/1.0 settings.
        // Do not overwrite an administrator's deliberate custom value above 1.0.
        if (getConfig().getBoolean("atmosphere.volume-tripled-v2-migrated", false)) return;
        double existing = getConfig().getDouble("atmosphere.volume", 0.35D);
        if (existing <= 1.0D) getConfig().set("atmosphere.volume", 3.0D);
        getConfig().set("atmosphere.volume-tripled-v2-migrated", true);
        saveConfig();
        getLogger().info("Halloween soundtrack gain migration complete; volume=" +
                getConfig().getDouble("atmosphere.volume", 3.0D) + ".");
    }

    /**
     * Existing plugin configs are deliberately not replaced by saveDefaultConfig().
     * On the first run of this gear release, migrate only shop item data so existing
     * arena coordinates, event settings and other server customizations survive.
     */
    private void migrateCrimsonWardenShopDefaults() {
        if (getConfig().getBoolean("migrations.crimson-warden-gear-v2", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged default config.yml is unavailable; Crimson Warden shop migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection defaultShop = defaults.getConfigurationSection("rewards.shop");
            if (defaultShop == null) {
                getLogger().warning("Default rewards.shop section is missing; Crimson Warden shop migration was skipped.");
                return;
            }

            String root = "rewards.shop.";
            for (String id : defaultShop.getKeys(false)) {
                ConfigurationSection source = defaultShop.getConfigurationSection(id);
                if (source == null) continue;
                String targetPath = root + id;
                if (!getConfig().isConfigurationSection(targetPath)) {
                    for (String key : source.getKeys(true)) {
                        if (!source.isConfigurationSection(key)) {
                            getConfig().set(targetPath + "." + key, source.get(key));
                        }
                    }
                    continue;
                }

                // For the new gear, update presentation/IA identity but preserve any
                // owner-defined price and minimum curse requirement where already set.
                if (id.startsWith("crimson-warden-")) {
                    for (String key : List.of("itemsadder-id", "material", "amount", "name", "lore")) {
                        if (source.contains(key)) {
                            getConfig().set(targetPath + "." + key, source.get(key));
                        }
                    }
                    for (String key : List.of("cost", "min-curse")) {
                        if (!getConfig().contains(targetPath + "." + key) && source.contains(key)) {
                            getConfig().set(targetPath + "." + key, source.get(key));
                        }
                    }
                }
            }

            // Explicit user-requested talisman balance. This one-time migration does
            // not keep forcing it on later reloads if the administrator later changes it.
            getConfig().set(root + "cursed-talisman.cost", 1_000_000L);
            getConfig().set(root + "cursed-talisman.bonus-health", 20.0D);
            getConfig().set(root + "cursed-talisman.bonus-multiplier", 0.10D);
            ConfigurationSection defaultTalisman = defaultShop.getConfigurationSection("cursed-talisman");
            if (defaultTalisman != null) {
                for (String key : List.of("name", "lore", "itemsadder-id", "material", "amount")) {
                    if (defaultTalisman.contains(key)) {
                        getConfig().set(root + "cursed-talisman." + key, defaultTalisman.get(key));
                    }
                }
            }

            getConfig().set("migrations.crimson-warden-gear-v2", true);
            saveConfig();
            getLogger().info("Crimson Warden shop defaults migrated once; existing arena and server settings were preserved.");
        } catch (IOException ex) {
            getLogger().warning("Crimson Warden shop migration failed: " + ex.getClass().getSimpleName());
        }
    }


    /**
     * Add the Crimson Warden shield to existing installations without resetting shop balance.
     */
    private void migrateCrimsonWardenShieldDefaults() {
        if (getConfig().getBoolean("migrations.crimson-warden-shield-v1", false)) return;
        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged default config.yml unavailable; Crimson Warden shield migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection source = defaults.getConfigurationSection("rewards.shop.crimson-warden-shield");
            if (source == null) {
                getLogger().warning("Default Crimson Warden shield shop entry is missing; migration was skipped.");
                return;
            }
            String target = "rewards.shop.crimson-warden-shield";
            if (!getConfig().isConfigurationSection(target)) {
                for (String key : source.getKeys(true)) {
                    if (!source.isConfigurationSection(key)) getConfig().set(target + "." + key, source.get(key));
                }
            } else {
                for (String key : List.of("itemsadder-id", "material", "amount", "name", "lore")) {
                    if (source.contains(key)) getConfig().set(target + "." + key, source.get(key));
                }
                for (String key : List.of("cost", "min-curse")) {
                    if (!getConfig().contains(target + "." + key) && source.contains(key)) {
                        getConfig().set(target + "." + key, source.get(key));
                    }
                }
            }
            getConfig().set("migrations.crimson-warden-shield-v1", true);
            saveConfig();
            getLogger().info("Crimson Warden shield shop entry added while preserving custom price and curse requirements.");
        } catch (IOException ex) {
            getLogger().warning("Crimson Warden shield migration failed: " + ex.getClass().getSimpleName());
        }
    }

    private void migrateCrimsonWardenArmorStats() {
        if (getConfig().getBoolean("migrations.crimson-warden-armor-stats-v3", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged default config.yml is unavailable; Crimson Warden armor-stat migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String root = "rewards.shop.";
            for (String id : List.of(
                    "crimson-warden-helmet",
                    "crimson-warden-chestplate",
                    "crimson-warden-leggings",
                    "crimson-warden-boots")) {
                ConfigurationSection source = defaults.getConfigurationSection(root + id);
                String targetPath = root + id;
                if (source != null && getConfig().isConfigurationSection(targetPath)) {
                    // Update only displayed armour stats, keeping prices, curse requirements,
                    // arena coordinates, and unrelated server settings intact.
                    getConfig().set(targetPath + ".lore", source.getStringList("lore"));
                }
            }

            getConfig().set("migrations.crimson-warden-armor-stats-v3", true);
            saveConfig();
            getLogger().info("Crimson Warden armor stats and shop lore migrated once; other server settings were preserved.");
        } catch (IOException ex) {
            getLogger().warning("Crimson Warden armor-stat migration failed: " + ex.getClass().getSimpleName());
        }
    }


    /**
     * Remove obsolete Crimson Warden shop lore on existing installations once.
     * Only presentation text is changed; prices, curse gates and other settings remain untouched.
     */
    private void migrateCrimsonWardenLoreV4() {
        if (getConfig().getBoolean("migrations.crimson-warden-lore-v4", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged default config.yml is unavailable; Crimson Warden lore migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String root = "rewards.shop.";
            for (String id : List.of(
                    "crimson-warden-helmet",
                    "crimson-warden-chestplate",
                    "crimson-warden-leggings",
                    "crimson-warden-boots")) {
                ConfigurationSection source = defaults.getConfigurationSection(root + id);
                String targetPath = root + id;
                if (source != null && getConfig().isConfigurationSection(targetPath)) {
                    getConfig().set(targetPath + ".lore", source.getStringList("lore"));
                }
            }
            getConfig().set("migrations.crimson-warden-lore-v4", true);
            saveConfig();
            getLogger().info("Crimson Warden armor lore cleaned up once; shop prices and curse requirements were preserved.");
        } catch (IOException ex) {
            getLogger().warning("Crimson Warden lore migration failed: " + ex.getClass().getSimpleName());
        }
    }


    /**
     * Replace the old duplicated "+Armor" shop lore with real equipment-tooltip modifiers.
     * Only presentation lore changes; prices and curse requirements are preserved.
     */
    private void migrateCrimsonWardenLoreV5() {
        if (getConfig().getBoolean("migrations.crimson-warden-lore-v5", false)) return;
        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Default config.yml is unavailable; Crimson Warden armor lore v5 migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String root = "rewards.shop.";
            for (String id : List.of(
                    "crimson-warden-helmet",
                    "crimson-warden-chestplate",
                    "crimson-warden-leggings",
                    "crimson-warden-boots")) {
                ConfigurationSection source = defaults.getConfigurationSection(root + id);
                String target = root + id;
                if (source != null && getConfig().isConfigurationSection(target)) {
                    getConfig().set(target + ".lore", source.getStringList("lore"));
                }
            }
            getConfig().set("migrations.crimson-warden-lore-v5", true);
            saveConfig();
            getLogger().info("Crimson Warden armor shop lore now relies on real equipment modifiers; prices and curse gates preserved.");
        } catch (IOException ex) {
            getLogger().warning("Crimson Warden armor lore v5 migration failed: " + ex.getClass().getSimpleName());
        }
    }

    /**
     * Migrate existing server configs to the custom Crimson Warden armor base material.
     * ItemsAdder supplies the actual modern/legacy wear textures; LEATHER_* is only the
     * compatible base material and avoids presenting the custom set as Netherite gear.
     * Unrelated shop prices, curse gates, and other server settings are preserved.
     */
    private void migrateCrimsonWardenCustomArmorV1() {
        if (getConfig().getBoolean("migrations.crimson-warden-custom-armor-v1", false)) return;

        for (String[] piece : new String[][]{
                {"crimson-warden-helmet", "LEATHER_HELMET"},
                {"crimson-warden-chestplate", "LEATHER_CHESTPLATE"},
                {"crimson-warden-leggings", "LEATHER_LEGGINGS"},
                {"crimson-warden-boots", "LEATHER_BOOTS"}
        }) {
            String path = "rewards.shop." + piece[0];
            if (getConfig().isConfigurationSection(path)) {
                getConfig().set(path + ".material", piece[1]);
            }
        }
        if (!getConfig().contains("rewards.passive-effects.crimson-warden-set-bonus.enabled")) {
            getConfig().set("rewards.passive-effects.crimson-warden-set-bonus.enabled", true);
        }
        if (!getConfig().contains("rewards.passive-effects.crimson-warden-set-bonus.resistance-seconds")) {
            getConfig().set("rewards.passive-effects.crimson-warden-set-bonus.resistance-seconds", 3);
        }
        if (!getConfig().contains("rewards.passive-effects.crimson-warden-set-bonus.fire-resistance-seconds")) {
            getConfig().set("rewards.passive-effects.crimson-warden-set-bonus.fire-resistance-seconds", 3);
        }
        if (!getConfig().contains("rewards.passive-effects.crimson-warden-set-bonus.aura-particles")) {
            getConfig().set("rewards.passive-effects.crimson-warden-set-bonus.aura-particles", true);
        }

        getConfig().set("migrations.crimson-warden-custom-armor-v1", true);
        saveConfig();
        getLogger().info("Crimson Warden custom armor migrated to leather bases with modern/legacy custom renderers and full-set effects.");
    }

    /**
     * Reduce the add-only decoration density when upgrading to physical 3D graves and mushrooms.
     * This migration only changes the three decoration counts, preserving all unrelated settings.
     */
    private void migrateHalloween3dDecorationsV1() {
        if (getConfig().getBoolean("migrations.halloween-3d-decorations-v1", false)) return;
        getConfig().set("world-decorations.gravestones-per-chunk", 1);
        getConfig().set("world-decorations.dead-plants-per-chunk", 1);
        if (!getConfig().contains("world-decorations.large-mushrooms-per-chunk")) {
            getConfig().set("world-decorations.large-mushrooms-per-chunk", 1);
        }
        // New structure-generation options must also be written into existing
        // config.yml files; saveDefaultConfig() does not merge new keys on upgrades.
        if (!getConfig().contains("random-structures.enabled")) getConfig().set("random-structures.enabled", true);
        if (!getConfig().contains("random-structures.village-chance-per-thousand")) {
            getConfig().set("random-structures.village-chance-per-thousand", 18);
        }
        if (!getConfig().contains("random-structures.graveyard-chance-per-thousand")) {
            getConfig().set("random-structures.graveyard-chance-per-thousand", 135);
        }
        if (!getConfig().contains("random-structures.chunks-per-tick")) getConfig().set("random-structures.chunks-per-tick", 1);
        if (!getConfig().contains("random-structures.minimum-distance-from-world-spawn-blocks")) {
            getConfig().set("random-structures.minimum-distance-from-world-spawn-blocks", 256);
        }
        if (!getConfig().contains("random-structures.discovery-radius-blocks")) getConfig().set("random-structures.discovery-radius-blocks", 10);
        if (!getConfig().contains("random-structures.village-discovery-reward-fragments")) {
            getConfig().set("random-structures.village-discovery-reward-fragments", 250);
        }
        if (!getConfig().contains("random-structures.graveyard-discovery-reward-fragments")) {
            getConfig().set("random-structures.graveyard-discovery-reward-fragments", 200);
        }
        // Legacy manual coordinates stay intact; generated village discovery is enabled.
        getConfig().set("haunted-village.enabled", true);
        getConfig().set("migrations.halloween-3d-decorations-v1", true);
        saveConfig();
        getLogger().info("Halloween 3D graves/mushrooms enabled with low per-chunk density; unrelated world settings preserved.");
    }

    private void migrateHalloweenDecorationReliabilityV2() {
        if (getConfig().getBoolean("migrations.halloween-decoration-reliability-v2", false)) return;
        // 48 attempts was the previous default and often missed eligible sites in sloped/forested chunks.
        // Preserve a custom value above the old default.
        int attempts = getConfig().getInt("world-decorations.attempts-per-placement", 48);
        if (attempts <= 48) getConfig().set("world-decorations.attempts-per-placement", 96);
        getConfig().set("migrations.halloween-decoration-reliability-v2", true);
        saveConfig();
        getLogger().info("Halloween decoration reliability migration applied.");
    }

    /**
     * Apply the intentional Halloween event overhaul to existing installations once.
     * It updates event timings/balance/cues and world decoration density, while keeping
     * the administrator's global event enable switch, arena coordinates, prices and
     * all unrelated gameplay settings intact.
     */
    /** Triples the graveyard generation chance once and preserves an explicit zero. */
    private void migrateGraveyardChanceTripledV1() {
        if (getConfig().getBoolean("migrations.halloween-graveyard-chance-tripled-v1", false)) return;
        int chance = Math.max(0, getConfig().getInt("random-structures.graveyard-chance-per-thousand", 45));
        if (chance > 0 && chance < 135) {
            getConfig().set("random-structures.graveyard-chance-per-thousand", Math.min(1000, chance * 3));
        }
        getConfig().set("migrations.halloween-graveyard-chance-tripled-v1", true);
        saveConfig();
        getLogger().info("Halloween graveyard generation chance migration applied.");
    }

    private void migrateHalloweenEventOverhaul() {
        if (getConfig().getBoolean("migrations.halloween-event-overhaul-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged default config.yml is unavailable; Halloween event migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));

            String eventsRoot = "random-events.";
            for (String key : List.of(
                    "start-delay-seconds",
                    "interval-minutes.min",
                    "interval-minutes.max",
                    "duration-minutes",
                    "surge-interval-seconds",
                    "max-event-mobs",
                    "event-mobs-enabled",
                    "invasion-mobs-per-surge",
                    "blood-moon-damage-multiplier",
                    "special-mob-chance-multiplier")) {
                String sourcePath = eventsRoot + key;
                if (defaults.contains(sourcePath)) {
                    getConfig().set(sourcePath, defaults.get(sourcePath));
                }
            }

            for (String id : List.of(
                    "soulstorm",
                    "witching-hour",
                    "cursed-harvest",
                    "blood-moon-invasion",
                    "pumpkin-apocalypse",
                    "graveyard-rising")) {
                String sourcePath = eventsRoot + "types." + id;
                ConfigurationSection source = defaults.getConfigurationSection(sourcePath);
                if (source == null) continue;
                String targetPath = sourcePath;
                for (String key : List.of("name", "sound", "start-message", "end-message")) {
                    if (source.contains(key)) {
                        getConfig().set(targetPath + "." + key, source.get(key));
                    }
                }
                ConfigurationSection multipliers = source.getConfigurationSection("multipliers");
                if (multipliers != null) {
                    for (String key : multipliers.getKeys(false)) {
                        getConfig().set(targetPath + ".multipliers." + key, multipliers.get(key));
                    }
                }
            }

            for (String key : List.of(
                    "pumpkins-per-chunk",
                    "cobwebs-per-chunk",
                    "red-candles-per-chunk",
                    "attempts-per-placement")) {
                String sourcePath = "world-decorations." + key;
                if (defaults.contains(sourcePath)) {
                    getConfig().set(sourcePath, defaults.get(sourcePath));
                }
            }
            getConfig().set("special-mobs.custom-models.enabled",
                    defaults.getBoolean("special-mobs.custom-models.enabled", true));
            getConfig().set("migrations.halloween-event-overhaul-v1", true);
            saveConfig();
            getLogger().info("Halloween event overhaul applied once: six event definitions, denser Blood Moon waves, 3x hostile-mob damage and refreshed world decorations; unrelated settings were preserved.");
        } catch (IOException ex) {
            getLogger().warning("Halloween event overhaul migration failed: " + ex.getClass().getSimpleName());
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

    public HalloweenStructureGenerator getStructureGenerator() {
        return structureGenerator;
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
