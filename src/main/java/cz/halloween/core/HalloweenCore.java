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
    private HalloweenContentRegistry contentRegistry;
    private HalloweenPassiveEffectManager passiveEffectManager;
    private HalloweenVillageDiscoveryManager villageDiscoveryManager;
    private HalloweenSecretDiscoveryManager secretDiscoveryManager;
    private HalloweenVampireEncounterManager vampireEncounterManager;
    private HalloweenWorldDecorator worldDecorator;
    private HalloweenStructureManager structureManager;
    private HalloweenRelicAbilityManager relicAbilityManager;
    private HalloweenHeadlessNickManager headlessNickManager;
    private HalloweenSeasonManager seasonManager;
    private HalloweenGraveEncounterManager graveEncounterManager;
    private final Set<String> reportedConfigErrors = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateAtmosphereVolume();
        migrateAtmospherePlaylistDefaults();
        migrateCrimsonWardenShopDefaults();
        migrateCrimsonWardenShieldDefaults();
        migrateStevHalloweenCandyShopDefaults();
        migrateCrimsonWardenArmorStats();
        migrateCursedTalismanHealth();
        migrateHalloweenEventOverhaul();
        migrateEventSpawnRules();
        migrateAdditionalMobAndQuestDefaults();
        migrateAtmosphericWorldGenerationDefaults();
        migrateSeasonAndWitchingDefaults();
        migrateWitchingBatDefaults();
        migrateGraveEncounterDefaults();

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
        contentRegistry = new HalloweenContentRegistry(this);
        itemManager = new HalloweenItemManager(this);
        passiveEffectManager = new HalloweenPassiveEffectManager(this);
        villageDiscoveryManager = new HalloweenVillageDiscoveryManager(this);
        secretDiscoveryManager = new HalloweenSecretDiscoveryManager(this);
        vampireEncounterManager = new HalloweenVampireEncounterManager(this);
        worldDecorator = new HalloweenWorldDecorator(this);
        structureManager = new HalloweenStructureManager(this);
        structureManager.recoverStaleNaturalReservations();
        relicAbilityManager = new HalloweenRelicAbilityManager(this);
        headlessNickManager = new HalloweenHeadlessNickManager(this);
        seasonManager = new HalloweenSeasonManager(this);
        graveEncounterManager = new HalloweenGraveEncounterManager(this);

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
        getServer().getPluginManager().registerEvents(structureManager, this);
        getServer().getPluginManager().registerEvents(relicAbilityManager, this);
        getServer().getPluginManager().registerEvents(headlessNickManager, this);
        getServer().getPluginManager().registerEvents(passiveEffectManager, this);
        getServer().getPluginManager().registerEvents(graveEncounterManager, this);

        if (getCommand("halloween") != null) {
            HalloweenCommand halloweenCommand = new HalloweenCommand(this);
            getCommand("halloween").setExecutor(halloweenCommand);
            getCommand("halloween").setTabCompleter(halloweenCommand);
            getServer().getPluginManager().registerEvents(halloweenCommand, this);
        }

        long saveInterval = 20L * 60L * 5L;
        getServer().getScheduler().runTaskTimer(this, dataManager::save, saveInterval, saveInterval);
        eventManager.start();
        seasonManager.start();
        vampireEncounterManager.startNaturalSummoningMonitor();
        atmosphere.start();
        worldDecorator.start();
        relicAbilityManager.start();
        headlessNickManager.start();
        passiveEffectManager.start();
        graveEncounterManager.start();

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
        if (seasonManager != null) seasonManager.stop();
        if (graveEncounterManager != null) graveEncounterManager.stop();
        if (vampireEncounterManager != null) {
            vampireEncounterManager.stopNaturalSummoningMonitor();
            vampireEncounterManager.stopEncounter();
        }
        if (atmosphere != null) atmosphere.stopPlayback();
        if (worldDecorator != null) worldDecorator.stop();
        if (structureManager != null) structureManager.stop();
        if (relicAbilityManager != null) relicAbilityManager.stop();
        if (headlessNickManager != null) headlessNickManager.stop();
        if (passiveEffectManager != null) passiveEffectManager.stop();
        if (dataManager != null) dataManager.save();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!eventEnabled) return;
        if (vampireEncounterManager != null && vampireEncounterManager.isTrackedVampireBoss(event.getEntity())) return;

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
        migrateAtmosphericWorldGenerationDefaults();
        migrateSeasonAndWitchingDefaults();
        migrateWitchingBatDefaults();
        migrateGraveEncounterDefaults();
        migrateCrimsonWardenShopDefaults();
        migrateCrimsonWardenShieldDefaults();
        migrateStevHalloweenCandyShopDefaults();
        migrateCrimsonWardenArmorStats();
        migrateCursedTalismanHealth();
        migrateHalloweenEventOverhaul();
        migrateEventSpawnRules();
        migrateAdditionalMobAndQuestDefaults();
        eventEnabled = getConfig().getBoolean("enabled", true);

        if (eventManager != null) {
            if (eventEnabled && getConfig().getBoolean("random-events.enabled", true)) {
                // Make the refreshed state visible shortly after /halloween reload.
                eventManager.scheduleFirstEvent();
            } else {
                eventManager.stop();
            }
        }
        if (seasonManager != null) seasonManager.refresh();
        if (vampireEncounterManager != null) vampireEncounterManager.stopEncounter();
        if (bossManager != null) bossManager.stopVampireBossBar();

        if (atmosphere != null) atmosphere.refreshPlayback();
        if (worldDecorator != null) {
            worldDecorator.stop();
            if (eventEnabled && getConfig().getBoolean("world-decorations.enabled", true)) {
                worldDecorator.scanLoadedChunks();
            }
        }

        logConfigValidationIssues();
        if (bossManager != null) bossManager.validateConfiguration();
        getLogger().info("HalloweenCore runtime state reloaded. enabled=" + eventEnabled
                + ", random-events=" + getConfig().getBoolean("random-events.enabled", true)
                + ", atmosphere=" + getConfig().getBoolean("atmosphere.enabled", true)
                + ", world-decorations=" + getConfig().getBoolean("world-decorations.enabled", true));
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
        if (worldDecorator != null) {
            if (enabled && getConfig().getBoolean("world-decorations.enabled", true)) {
                worldDecorator.scanLoadedChunks();
            } else {
                worldDecorator.stop();
            }
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

    private void migrateSeasonAndWitchingDefaults() {
        if (getConfig().getBoolean("migrations.season-witching-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged config.yml unavailable; season/Witching Hour defaults were not migrated.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String sectionPath : List.of("season", "witching-hour")) {
                ConfigurationSection section = defaults.getConfigurationSection(sectionPath);
                if (section == null) continue;
                for (String key : section.getKeys(true)) {
                    if (section.isConfigurationSection(key)) continue;
                    String path = sectionPath + "." + key;
                    if (!getConfig().contains(path)) getConfig().set(path, section.get(key));
                }
            }
            getConfig().set("migrations.season-witching-v1", true);
            saveConfig();
            getLogger().info("Season scheduler and Witching Hour defaults migrated without overwriting existing settings.");
        } catch (IOException ex) {
            getLogger().warning("Season/Witching Hour migration failed: " + ex.getClass().getSimpleName());
        }
    }

    private void migrateWitchingBatDefaults() {
        if (getConfig().getBoolean("migrations.witching-bat-transform-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged config.yml unavailable; Witching Hour bat defaults were not migrated.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection section = defaults.getConfigurationSection("witching-hour");
            if (section != null) {
                for (String key : section.getKeys(true)) {
                    if (section.isConfigurationSection(key)) continue;
                    String path = "witching-hour." + key;
                    if (!getConfig().contains(path)) getConfig().set(path, section.get(key));
                }
            }
            getConfig().set("migrations.witching-bat-transform-v1", true);
            saveConfig();
            getLogger().info("Witching Hour bat transformation defaults migrated without overwriting existing settings.");
        } catch (IOException ex) {
            getLogger().warning("Witching Hour bat migration failed: " + ex.getClass().getSimpleName());
        }
    }

    private void migrateGraveEncounterDefaults() {
        if (getConfig().getBoolean("migrations.grave-encounters-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged config.yml unavailable; grave encounter defaults were not migrated.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection section = defaults.getConfigurationSection("grave-encounters");
            if (section != null) {
                for (String key : section.getKeys(true)) {
                    if (section.isConfigurationSection(key)) continue;
                    String path = "grave-encounters." + key;
                    if (!getConfig().contains(path)) getConfig().set(path, section.get(key));
                }
            }
            getConfig().set("migrations.grave-encounters-v1", true);
            saveConfig();
            getLogger().info("Grave encounter defaults migrated without overwriting existing settings.");
        } catch (IOException ex) {
            getLogger().warning("Grave encounter config migration failed: " + ex.getClass().getSimpleName());
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
    /**
     * Adds the licensed Stev's Halloween candy rewards to existing installs without
     * replacing owner-defined prices or curse requirements.
     */
    private void migrateStevHalloweenCandyShopDefaults() {
        if (getConfig().getBoolean("migrations.stev-halloween-candy-shop-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged default config.yml is unavailable; Stev candy shop migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection defaultShop = defaults.getConfigurationSection("rewards.shop");
            if (defaultShop == null) {
                getLogger().warning("Default rewards.shop section is missing; Stev candy shop migration was skipped.");
                return;
            }

            String root = "rewards.shop.";
            for (String id : defaultShop.getKeys(false)) {
                if (!id.startsWith("stev-")) continue;
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

                // Keep a server owner's balance tweaks while refreshing the new
                // item identity and copy/lore on an existing partial definition.
                for (String key : List.of("itemsadder-id", "material", "amount", "name", "lore")) {
                    if (source.contains(key)) getConfig().set(targetPath + "." + key, source.get(key));
                }
                for (String key : List.of("cost", "min-curse")) {
                    if (!getConfig().contains(targetPath + "." + key) && source.contains(key)) {
                        getConfig().set(targetPath + "." + key, source.get(key));
                    }
                }
            }

            getConfig().set("migrations.stev-halloween-candy-shop-v1", true);
            saveConfig();
            getLogger().info("Stev Halloween candy shop items were added/migrated while preserving configured prices and curse gates.");
        } catch (IOException ex) {
            getLogger().warning("Stev Halloween candy shop migration failed: " + ex.getClass().getSimpleName());
        }
    }

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
            getConfig().set(root + "cursed-talisman.bonus-health", 2.0D);
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
     * The current specification defines the talisman as one extra heart (+2 max health),
     * not the older +20 HP implementation. Apply this migration once to existing installs
     * without repeatedly overriding an administrator's later balance changes.
     */
    private void migrateCursedTalismanHealth() {
        if (getConfig().getBoolean("migrations.cursed-talisman-single-heart-v1", false)) return;
        getConfig().set("rewards.shop.cursed-talisman.bonus-health", 2.0D);
        getConfig().set("migrations.cursed-talisman-single-heart-v1", true);
        saveConfig();
        getLogger().info("Cursed talisman health migrated to +2 health points (+1 heart).");
    }

    /**
     * One-time migration for the newly added Crimson Warden shield. The original
     * gear migration is intentionally one-shot, so it cannot install items added
     * in later releases on servers that already recorded that older migration.
     * Preserve server-defined prices and curse requirements while refreshing the
     * item identity and presentation from the packaged defaults.
     */
    private void migrateCrimsonWardenShieldDefaults() {
        if (getConfig().getBoolean("migrations.crimson-warden-shield-v1", false)) return;

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged default config.yml is unavailable; Crimson Warden shield migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            ConfigurationSection source = defaults.getConfigurationSection(
                    "rewards.shop.crimson-warden-shield");
            if (source == null) {
                getLogger().warning("Default Crimson Warden shield shop entry is missing; migration was skipped.");
                return;
            }

            String targetPath = "rewards.shop.crimson-warden-shield";
            if (!getConfig().isConfigurationSection(targetPath)) {
                for (String key : source.getKeys(true)) {
                    if (!source.isConfigurationSection(key)) {
                        getConfig().set(targetPath + "." + key, source.get(key));
                    }
                }
            } else {
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

            getConfig().set("migrations.crimson-warden-shield-v1", true);
            saveConfig();
            getLogger().info("Crimson Warden shield shop entry migrated once; existing price and curse settings were preserved.");
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
     * Apply the intentional Halloween event overhaul to existing installations once.
     * It updates event timings/balance/cues and world decoration density, while keeping
     * the administrator's global event enable switch, arena coordinates, prices and
     * all unrelated gameplay settings intact.
     */
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


    /**
     * Event behavior migration. Older configurations may still limit waves to 48 mobs
     * per world and 4 invasion mobs per player. Upgrade only known historical defaults;
     * preserve any non-default administrator choices.
     */
    private void migrateEventSpawnRules() {
        if (getConfig().getBoolean("migrations.event-spawn-rules-v1", false)) return;
        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Packaged config.yml unavailable; event spawn-rule migration was skipped.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            String root = "random-events.";
            // Upgrade only the old built-in values from previous releases.
            if (getConfig().getInt(root + "max-event-mobs", 48) == 48) {
                getConfig().set(root + "max-event-mobs",
                        defaults.getInt(root + "max-event-mobs", 256));
            }
            if (getConfig().getInt(root + "invasion-mobs-per-surge", 4) == 4) {
                getConfig().set(root + "invasion-mobs-per-surge",
                        defaults.getInt(root + "invasion-mobs-per-surge", 2));
            }
            for (String key : List.of("spawn-radius-blocks", "event-mob-lifetime-seconds")) {
                String path = root + key;
                if (!getConfig().contains(path) && defaults.contains(path)) {
                    getConfig().set(path, defaults.get(path));
                }
            }
            for (String sectionPath : List.of(
                    "random-events.cursed-harvest",
                    "random-events.pumpkin-apocalypse",
                    "random-events.graveyard-rising")) {
                ConfigurationSection source = defaults.getConfigurationSection(sectionPath);
                if (source == null) continue;
                for (String key : source.getKeys(true)) {
                    if (source.isConfigurationSection(key)) continue;
                    String path = sectionPath + "." + key;
                    if (!getConfig().contains(path)) getConfig().set(path, source.get(key));
                }
            }
            getConfig().set("migrations.event-spawn-rules-v1", true);
            saveConfig();
            getLogger().info("Halloween event spawn rules migrated: all-player targeting, 25-block radius and bounded world scans; customized non-default values were preserved.");
        } catch (IOException ex) {
            getLogger().warning("Event spawn-rule migration failed: " + ex.getClass().getSimpleName());
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

    public HalloweenSeasonManager getSeasonManager() {
        return seasonManager;
    }

    public HalloweenGraveEncounterManager getGraveEncounterManager() {
        return graveEncounterManager;
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

    public HalloweenContentRegistry getContentRegistry() {
        return contentRegistry;
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

    public HalloweenStructureManager getStructureManager() {
        return structureManager;
    }

    public HalloweenRelicAbilityManager getRelicAbilityManager() {
        return relicAbilityManager;
    }

    public HalloweenHeadlessNickManager getHeadlessNickManager() {
        return headlessNickManager;
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

    /**
     * General gameplay eligibility. The lobby world is always excluded from automatic
     * Halloween gameplay; explicit manual structure commands have their own permission
     * path and intentionally do not call this method.
     */
    public boolean isEligibleGameplayWorld(World world) {
        if (world == null || world.getName().equalsIgnoreCase("Spawn")) return false;

        java.util.List<String> worlds = getConfig().getStringList("gameplay.worlds.names");
        if (worlds.isEmpty()) return true;

        boolean listed = worlds.stream().anyMatch(name -> name.equalsIgnoreCase(world.getName()));
        String mode = getConfig().getString("gameplay.worlds.mode", "BLACKLIST");
        if ("WHITELIST".equalsIgnoreCase(mode)) {
            return listed;
        }
        return !listed;
    }

    /**
     * One-time migration for the atmospheric world-generation release. The user's
     * explicit request is to make Halloween content appear throughout the playable map.
     * Wildcard generation is still fail-safe for the lobby: the world named Spawn is
     * always excluded, and automatic decoration only adds blocks in safe natural spots.
     */
    private void migrateAtmosphericWorldGenerationDefaults() {
        if (getConfig().getBoolean("migrations.atmospheric-world-generation-v1", false)) return;
        if (getConfig().getStringList("automatic-world-generation.allowed-worlds").isEmpty()) {
            getConfig().set("automatic-world-generation.allowed-worlds", List.of("*"));
        }
        getConfig().set("world-decorations.decorate-existing-chunks", true);
        getConfig().set("automatic-world-generation.structures-on-existing-chunks", true);
        getConfig().set("migrations.atmospheric-world-generation-v1", true);
        saveConfig();
        getLogger().info("Atmospheric Halloween generation migration complete; all non-Spawn worlds are eligible.");
    }

    /**
     * Automatic generation is limited to the configured allow-list. The wildcard
     * means all worlds except the protected Spawn world; an empty list remains fail-closed.
     */
    public boolean isAutomaticHalloweenWorld(World world) {
        if (world == null || world.getName().equalsIgnoreCase("Spawn")) return false;
        if (!getConfig().getBoolean("automatic-world-generation.enabled", true)) return false;
        java.util.List<String> allowed = getConfig().getStringList("automatic-world-generation.allowed-worlds");
        return allowed.stream().anyMatch(name -> name.equals("*") || name.equalsIgnoreCase(world.getName()));
    }

    public boolean isEligibleGameplayPlayer(Player player) {
        if (player == null || dataManager == null || dataManager.isHalloweenDisabled(player.getUniqueId())) return false;

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
