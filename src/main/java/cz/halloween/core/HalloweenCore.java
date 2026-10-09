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

public final class HalloweenCore extends JavaPlugin implements Listener {
    private HalloweenDataManager dataManager;
    private HalloweenServiceImpl service;
    private boolean eventEnabled;
    private HalloweenEventManager eventManager;
    private HalloweenRewardManager rewardManager;
    private HalloweenAtmosphere atmosphere;
    private HalloweenMobManager mobManager;
    private HalloweenChallengeManager challengeManager;
    private HalloweenBossManager bossManager;
    private HalloweenItemManager itemManager;
    private HalloweenPassiveEffectManager passiveEffectManager;
    private HalloweenVillageDiscoveryManager villageDiscoveryManager;
    private HalloweenSecretDiscoveryManager secretDiscoveryManager;
    private HalloweenVampireEncounterManager vampireEncounterManager;
    private HalloweenWorldDecorator worldDecorator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateAtmosphereVolume();

        dataManager = new HalloweenDataManager(this);
        dataManager.load();

        eventEnabled = getConfig().getBoolean("enabled", true);
        service = new HalloweenServiceImpl(this);
        eventManager = new HalloweenEventManager(this);
        rewardManager = new HalloweenRewardManager(this);
        atmosphere = new HalloweenAtmosphere(this);
        mobManager = new HalloweenMobManager(this);
        challengeManager = new HalloweenChallengeManager(this);
        bossManager = new HalloweenBossManager(this);
        itemManager = new HalloweenItemManager(this);
        passiveEffectManager = new HalloweenPassiveEffectManager(this);
        villageDiscoveryManager = new HalloweenVillageDiscoveryManager(this);
        secretDiscoveryManager = new HalloweenSecretDiscoveryManager(this);
        vampireEncounterManager = new HalloweenVampireEncounterManager(this);
        worldDecorator = new HalloweenWorldDecorator(this);

        for (String error : HalloweenConfigValidator.validate(this)) {
            getLogger().severe("[CONFIG] " + error);
        }
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
        getServer().getPluginManager().registerEvents(worldDecorator, this);

        if (getCommand("halloween") != null) {
            HalloweenCommand halloweenCommand = new HalloweenCommand(this);
            getCommand("halloween").setExecutor(halloweenCommand);
            getCommand("halloween").setTabCompleter(halloweenCommand);
        }

        long saveInterval = 20L * 60L * 5L;
        getServer().getScheduler().runTaskTimer(this, dataManager::save, saveInterval, saveInterval);
        eventManager.start();
        vampireEncounterManager.startNaturalSummoningMonitor();
        atmosphere.start();
        worldDecorator.start();
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
            worldDecorator.stop();
            if (eventEnabled && getConfig().getBoolean("world-decorations.enabled", true)) {
                worldDecorator.scanLoadedChunks();
            }
        }

        for (String error : HalloweenConfigValidator.validate(this)) {
            getLogger().severe("[CONFIG] " + error);
        }
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
        if (worldDecorator != null && enabled) worldDecorator.scanLoadedChunks();

        if (changed) {
            Bukkit.broadcastMessage(color(enabled
                    ? "&6&lHALLOWEEN &8» &eHalloween je zapnutý. První událost dorazí za chvíli!"
                    : "&8[HALLOWEEN] &7Halloween event byl vypnut."));
        } else if (enabled) {
            getLogger().info("Halloween event is already enabled; next event scheduled shortly.");
        }
    }

    private void migrateAtmosphereVolume() {
        // Upgrade existing server configs once: the prior default 0.35 was too quiet.
        if (getConfig().getBoolean("atmosphere.volume-triple-migrated", false)) return;
        double existing = getConfig().getDouble("atmosphere.volume", 0.35D);
        if (existing <= 0.36D) getConfig().set("atmosphere.volume", 1.0D);
        getConfig().set("atmosphere.volume-triple-migrated", true);
        saveConfig();
        getLogger().info("Halloween soundtrack volume upgraded to " +
                getConfig().getDouble("atmosphere.volume", 1.0D) + ".");
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
