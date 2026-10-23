/*
 * Integrated from SpookySeason 1.7.0 by BoondockSulfur under the MIT License.
 * Copyright (c) 2026 BoondockSulfur. See THIRD_PARTY_LICENSES/SpookySeason-MIT.txt.
 */
package org.example;

import java.io.File;
import java.time.ZonedDateTime;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.cmd.BloodRainCommand;
import org.example.cmd.PumpkinRainCommand;
import org.example.cmd.SpookyBossCommand;
import org.example.cmd.SpookyRaidCommand;
import org.example.cmd.SpookyToggleCommand;
import org.example.cmd.SpookyVolumeCommand;
import org.example.feature.BatSwarmManager;
import org.example.feature.BloodRainManager;
import org.example.feature.EntityCleanupListener;
import org.example.feature.GhostListener;
import org.example.feature.HalloweenBossManager;
import org.example.feature.HalloweenMobListener;
import org.example.feature.HauntedNightManager;
import org.example.feature.JumpScareListener;
import org.example.feature.PumpkinRainManager;
import org.example.feature.SeasonRewardManager;
import org.example.feature.SpookyMenuGUI;
import org.example.feature.TrickOrTreatListener;
import org.example.feature.raid.RaidManager;
import org.example.integration.CustomJukeboxHook;
import org.example.integration.RegionIntegration;
import org.example.integration.SpookyPlaceholders;
import org.example.lang.Lang;
import org.example.prefs.PlayerPrefs;
import org.example.prefs.PlayerStats;
import org.example.update.UpdateChecker;
import org.example.util.Attributes;
import org.example.util.ConfigMerger;
import org.example.util.Scheduler;

public final class SpookySeason {
    private static SpookySeason instance;
    private final JavaPlugin host;

    private SpookySeason(JavaPlugin host) { this.host = host; }

    public static SpookySeason enable(JavaPlugin host) {
        instance = new SpookySeason(host);
        instance.onEnable();
        return instance;
    }

    /** Real Bukkit plugin instance used by the integrated module. */
    public JavaPlugin plugin() { return host; }
    public File getDataFolder() { return host.getDataFolder(); }
    public FileConfiguration getConfig() { return host.getConfig(); }
    public java.util.logging.Logger getLogger() { return host.getLogger(); }
    public org.bukkit.Server getServer() { return host.getServer(); }
    public org.bukkit.plugin.PluginDescriptionFile getDescription() { return host.getDescription(); }
    public PluginCommand getCommand(String name) { return host.getCommand(name); }
    public void saveDefaultConfig() { host.saveDefaultConfig(); }
    public void saveConfig() { host.saveConfig(); }
    public void reloadConfig() { host.reloadConfig(); }
    /** Configuration paths of removed features; cleaned up on every start. */
    private static final List<String> OBSOLETE_CONFIG_PATHS = List.of("seasonEndRewards.commands");
    /** Sections whose entries the admin may delete; the merge must not bring them back. */
    private static final List<String> FREE_FORM_CONFIG_PATHS = List.of(
            "seasonEndRewards.ranks", "raid.waves.names", "raid.target.zones");
    private NamespacedKey entityMarker;
    private NamespacedKey ghostMarker;
    private NamespacedKey raidMarker;
    private HauntedNightManager hauntedNightManager;
    private PumpkinRainManager pumpkinRainManager;
    private BloodRainManager bloodRainManager;
    private HalloweenBossManager bossManager;
    private BatSwarmManager batSwarmManager;
    private SeasonRewardManager rewardManager;
    private SpookyMenuGUI menuGUI;
    private PlayerPrefs prefs;
    private PlayerStats stats;
    private RegionIntegration regionIntegration;
    private CustomJukeboxHook customJukeboxHook;
    private RaidManager raidManager;

    public static SpookySeason get() {
        return instance;
    }

    public NamespacedKey entityMarker() {
        return this.entityMarker;
    }

    /** Marks the vex ghosts of a blood moon night — in addition to the general entityMarker. */
    public NamespacedKey ghostMarker() {
        return this.ghostMarker;
    }

    /** Marks a raid's attackers and target — in addition to the entityMarker. */
    public NamespacedKey raidMarker() {
        return this.raidMarker;
    }

    public PlayerPrefs prefs() {
        return this.prefs;
    }

    public PlayerStats stats() {
        return this.stats;
    }

    public RegionIntegration regions() {
        return this.regionIntegration;
    }

    public CustomJukeboxHook jukebox() {
        return this.customJukeboxHook;
    }

    public HauntedNightManager haunted() {
        return this.hauntedNightManager;
    }

    public PumpkinRainManager pumpkins() {
        return this.pumpkinRainManager;
    }

    public BloodRainManager bloodRain() {
        return this.bloodRainManager;
    }

    public HalloweenBossManager boss() {
        return this.bossManager;
    }

    public SeasonRewardManager rewards() {
        return this.rewardManager;
    }

    public SpookyMenuGUI menu() {
        return this.menuGUI;
    }

    public RaidManager raid() {
        return this.raidManager;
    }

    private void onEnable() {
        instance = this;
        // Resolves the attributes through the registry (1.21.x and 26.x keys). A failure should
        // surface at enable time, not later during a spawn.
        Attributes.init();
        this.entityMarker = new NamespacedKey(this.host, "spooky_entity");
        this.ghostMarker = new NamespacedKey(this.host, "spooky_ghost");
        this.raidMarker = new NamespacedKey(this.host, "spooky_raid");
        this.saveDefaultConfig();
        this.mergeConfig();
        Lang.load(this.host);
        this.regionIntegration = new RegionIntegration(this.host);
        this.customJukeboxHook = new CustomJukeboxHook(this.host);
        this.prefs = new PlayerPrefs(this.host);
        this.stats = new PlayerStats(this.host);
        this.pumpkinRainManager = new PumpkinRainManager(this.host);
        this.bloodRainManager = new BloodRainManager(this.host);
        this.hauntedNightManager = new HauntedNightManager(this.host);
        this.bossManager = new HalloweenBossManager(this.host);
        this.batSwarmManager = new BatSwarmManager(this.host);
        this.rewardManager = new SeasonRewardManager(this.host);
        this.menuGUI = new SpookyMenuGUI();
        this.raidManager = new RaidManager(this.host);
        this.registerCommand("pumpkinrain", new PumpkinRainCommand(this.pumpkinRainManager));
        this.registerCommand("bloodrain", new BloodRainCommand(this.bloodRainManager));
        this.registerCommand("spooky", new SpookyToggleCommand());
        this.registerCommand("spookyvolume", new SpookyVolumeCommand());
        this.registerCommand("spookyboss", new SpookyBossCommand());
        this.registerCommand("spookyraid", new SpookyRaidCommand());
        Bukkit.getPluginManager().registerEvents((Listener)new TrickOrTreatListener(this), this.host);
        Bukkit.getPluginManager().registerEvents((Listener)new GhostListener(), this.host);
        Bukkit.getPluginManager().registerEvents((Listener)new JumpScareListener(this), this.host);
        Bukkit.getPluginManager().registerEvents((Listener)new HalloweenMobListener(this), this.host);
        Bukkit.getPluginManager().registerEvents((Listener)this.bossManager, this.host);
        Bukkit.getPluginManager().registerEvents((Listener)this.rewardManager, this.host);
        Bukkit.getPluginManager().registerEvents((Listener)this.menuGUI, this.host);
        Bukkit.getPluginManager().registerEvents((Listener)this.raidManager, this.host);
        Bukkit.getPluginManager().registerEvents((Listener)new EntityCleanupListener(this), this.host);
        this.hauntedNightManager.start();
        this.bossManager.start();
        this.batSwarmManager.start();
        this.rewardManager.start();
        this.raidManager.start();
        this.bloodRainManager.startTicker();
        // Upstream bStats telemetry is intentionally not registered for the combined HalloweenCore plugin.
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.hookPlaceholderAPI();
        }
        if (this.getConfig().getBoolean("updateChecker", true)) {
            UpdateChecker updateChecker = new UpdateChecker(this.host);
            Bukkit.getPluginManager().registerEvents((Listener)updateChecker, this.host);
            updateChecker.checkAsync();
        }
        this.getLogger().info(Lang.get("plugin.enabled", new String[0]));
    }

    public void disable() {
        // Every stop() is guarded separately; one failure (for example rejected scheduling on
        // Folia) must not abort the rest of the cleanup.
        this.safeStop(this.hauntedNightManager == null ? null : this.hauntedNightManager::stop, "hauntedNight");
        this.safeStop(this.pumpkinRainManager == null ? null : this.pumpkinRainManager::stop, "pumpkinRain");
        this.safeStop(this.bloodRainManager == null ? null : this.bloodRainManager::shutdown, "bloodRain");
        this.safeStop(this.bossManager == null ? null : this.bossManager::stop, "boss");
        this.safeStop(this.batSwarmManager == null ? null : this.batSwarmManager::stop, "batSwarm");
        this.safeStop(this.rewardManager == null ? null : this.rewardManager::stop, "rewards");
        this.safeStop(this.raidManager == null ? null : this.raidManager::stop, "raid");
        if (this.prefs != null) {
            this.prefs.flush();
        }
        if (this.stats != null) {
            this.stats.flush();
        }
    }

    private void safeStop(Runnable stop, String name) {
        if (stop == null) {
            return;
        }
        try {
            stop.run();
        }
        catch (Exception e) {
            this.getLogger().warning("Cleanup of " + name + " failed during disable: " + e.getMessage());
        }
    }

    public void stopAll() {
        if (this.hauntedNightManager != null) {
            this.hauntedNightManager.stop();
        }
        if (this.pumpkinRainManager != null) {
            this.pumpkinRainManager.stop();
        }
        if (this.bloodRainManager != null) {
            this.bloodRainManager.shutdown();
        }
        if (this.bossManager != null) {
            this.bossManager.stop();
        }
        if (this.batSwarmManager != null) {
            this.batSwarmManager.stop();
        }
        if (this.raidManager != null) {
            // Announced: players in the middle of a raid would otherwise just see it vanish.
            this.raidManager.stop(true);
        }
        this.removeAllPluginEntities();
    }

    public void removeAllPluginEntities() {
        if (Scheduler.isFolia()) {
            // On Folia a world's entity list belongs to no single thread and cannot be walked
            // safely across regions. The managers clear their own active entities in stopAll();
            // orphans left over from a crash or old chunks are removed by EntityCleanupListener
            // on the next chunk load.
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!e.getPersistentDataContainer().has(this.entityMarker, PersistentDataType.BYTE)) continue;
                if (!e.isValid() || e.isDead()) continue;
                e.remove();
            }
        }
    }

    public void startAll() {
        if (this.hauntedNightManager != null) {
            this.hauntedNightManager.start();
        }
        if (this.bossManager != null) {
            this.bossManager.start();
        }
        if (this.batSwarmManager != null) {
            this.batSwarmManager.start();
        }
        if (this.rewardManager != null) {
            this.rewardManager.start();
        }
        if (this.raidManager != null) {
            this.raidManager.start();
        }
        if (this.bloodRainManager != null) {
            this.bloodRainManager.startTicker();
        }
    }

    public void reload() {
        this.mergeConfig();
        Lang.load(this.host);
        this.hauntedNightManager.start();
        this.bossManager.start();
        this.batSwarmManager.start();
        this.rewardManager.start();
        // Deliberately only the ticker: a running raid carries on with its frozen configuration
        // snapshot and is not torn down by a reload.
        this.raidManager.start();
        this.bloodRainManager.startTicker();
    }

    private void mergeConfig() {
        File file = new File(this.getDataFolder(), "config.yml");
        ConfigMerger.merge(this.host, "config.yml", file, FREE_FORM_CONFIG_PATHS);
        ConfigMerger.removeObsolete(this.host, file, OBSOLETE_CONFIG_PATHS);
        this.reloadConfig();
    }

    public boolean isSeasonActive() {
        return this.getConfig().getBoolean("active", true) && this.isInSeasonWindow();
    }

    /**
     * The calendar window only, without the manual {@code active} switch.
     *
     * <p>The season-end rewards deliberately hang off this: otherwise a {@code /spooky off} in the
     * middle of the season would count as the season ending, handing out rewards and resetting the
     * treat statistics.
     */
    public boolean isInSeasonWindow() {
        FileConfiguration cfg = this.getConfig();
        int startDay = cfg.getInt("activeWindow.startDay", 0);
        int endDay = cfg.getInt("activeWindow.endDay", 0);
        if (startDay == 0 || endDay == 0) {
            return true;
        }
        int startMonth = Math.max(1, Math.min(12, cfg.getInt("activeWindow.startMonth", 10)));
        int endMonth = Math.max(1, Math.min(12, cfg.getInt("activeWindow.endMonth", startMonth)));
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneId.of("Europe/Prague"));
        int current = now.getMonthValue() * 100 + now.getDayOfMonth();
        int start = startMonth * 100 + startDay;
        int end = endMonth * 100 + endDay;
        return start <= end ? current >= start && current <= end : current >= start || current <= end;
    }

    public boolean isWorldEnabled(World world) {
        List<String> enabled = this.getConfig().getStringList("enabledWorlds");
        return enabled.isEmpty() || enabled.contains(world.getName());
    }

    private void hookPlaceholderAPI() {
        new SpookyPlaceholders(this).register();
        this.getLogger().info("PlaceholderAPI integration enabled.");
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = this.getCommand(name);
        if (cmd == null) {
            this.getLogger().severe("Command '" + name + "' not found in plugin.yml!");
            return;
        }
        cmd.setExecutor((CommandExecutor)executor);
        if (executor instanceof TabCompleter) {
            TabCompleter tc = (TabCompleter)executor;
            cmd.setTabCompleter(tc);
        }
    }
}

