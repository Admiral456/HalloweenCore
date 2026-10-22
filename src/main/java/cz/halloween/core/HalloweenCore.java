package cz.halloween.core;

import cz.halloween.core.horrornights.HorrorNightsManager;
import cz.halloween.core.spooky.cmd.BloodRainCommand;
import cz.halloween.core.spooky.cmd.PumpkinRainCommand;
import cz.halloween.core.spooky.cmd.SpookyBossCommand;
import cz.halloween.core.spooky.cmd.SpookyRaidCommand;
import cz.halloween.core.spooky.cmd.SpookyToggleCommand;
import cz.halloween.core.spooky.cmd.SpookyVolumeCommand;
import cz.halloween.core.spooky.feature.BatSwarmManager;
import cz.halloween.core.spooky.feature.BloodRainManager;
import cz.halloween.core.spooky.feature.EntityCleanupListener;
import cz.halloween.core.spooky.feature.GhostListener;
import cz.halloween.core.spooky.feature.HalloweenBossManager;
import cz.halloween.core.spooky.feature.HalloweenMobListener;
import cz.halloween.core.spooky.feature.HauntedNightManager;
import cz.halloween.core.spooky.feature.JumpScareListener;
import cz.halloween.core.spooky.feature.PumpkinRainManager;
import cz.halloween.core.spooky.feature.SeasonRewardManager;
import cz.halloween.core.spooky.feature.SpookyMenuGUI;
import cz.halloween.core.spooky.feature.TrickOrTreatListener;
import cz.halloween.core.spooky.feature.raid.RaidManager;
import cz.halloween.core.spooky.integration.CustomJukeboxHook;
import cz.halloween.core.spooky.integration.RegionIntegration;
import cz.halloween.core.spooky.integration.SpookyPlaceholders;
import cz.halloween.core.spooky.lang.Lang;
import cz.halloween.core.spooky.prefs.PlayerPrefs;
import cz.halloween.core.spooky.prefs.PlayerStats;
import cz.halloween.core.spooky.util.Attributes;
import cz.halloween.core.spooky.util.ConfigMerger;
import cz.halloween.core.spooky.util.Scheduler;
import java.io.File;
import java.io.IOException;
import java.time.Month;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;


/**
 * Includes adapted lifecycle and integration methods from SpookySeason 1.7.0 by BoondockSulfur.
 * Those portions are redistributed under MIT; see THIRD_PARTY_NOTICES.md and
 * META-INF/licenses/spookyseason-MIT.txt.
 */
public final class HalloweenCore extends JavaPlugin implements CommandExecutor, TabCompleter {
    private File playerDataFile;
    private YamlConfiguration playerData;
    private DialogQuestManager questManager;
    private KeyItemFactory keyItemFactory;
    private SecretRoomManager secretRoomManager;
    private static HalloweenCore instance;
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


    private HorrorNightsManager horrorNightsManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        mergeConfig();
        installDefaults();
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Nelze vytvořit datovou složku HalloweenCore.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        playerDataFile = new File(getDataFolder(), "players.yml");
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        keyItemFactory = new KeyItemFactory(this);
        questManager = new DialogQuestManager(this);
        secretRoomManager = new SecretRoomManager(this, keyItemFactory);
        getServer().getPluginManager().registerEvents(secretRoomManager, this);
        if (getConfig().getStringList("secret-rooms.worlds").isEmpty()) {
            getLogger().warning("Generování tajných místností je bezpečně vypnuté: nastav secret-rooms.worlds na přesný název herního světa. Svět Spawn je vždy vyloučen.");
        }
        if (getCommand("halloween") != null) {
            getCommand("halloween").setExecutor(this);
            getCommand("halloween").setTabCompleter(this);
        }
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new HalloweenPlaceholders(this).register();
        }
        enableSpookySeason();
        horrorNightsManager = new HorrorNightsManager(this);
        horrorNightsManager.start();
        getLogger().info("HalloweenCore načten: questy, tajné místnosti, sezónní funkce a strašidelné noci.");
    }


    private void enableSpookySeason() {
        Attributes.init();
        entityMarker = new NamespacedKey(this, "spooky_entity");
        ghostMarker = new NamespacedKey(this, "spooky_ghost");
        raidMarker = new NamespacedKey(this, "spooky_raid");
        Lang.load(this);
        regionIntegration = new RegionIntegration(this);
        customJukeboxHook = new CustomJukeboxHook(this);
        prefs = new PlayerPrefs(this);
        stats = new PlayerStats(this);
        pumpkinRainManager = new PumpkinRainManager(this);
        bloodRainManager = new BloodRainManager(this);
        hauntedNightManager = new HauntedNightManager(this);
        bossManager = new HalloweenBossManager(this);
        batSwarmManager = new BatSwarmManager(this);
        rewardManager = new SeasonRewardManager(this);
        menuGUI = new SpookyMenuGUI();
        raidManager = new RaidManager(this);
        registerCommand("pumpkinrain", new PumpkinRainCommand(pumpkinRainManager));
        registerCommand("bloodrain", new BloodRainCommand(bloodRainManager));
        registerCommand("spooky", new SpookyToggleCommand());
        registerCommand("spookyvolume", new SpookyVolumeCommand());
        registerCommand("spookyboss", new SpookyBossCommand());
        registerCommand("spookyraid", new SpookyRaidCommand());
        Bukkit.getPluginManager().registerEvents(new TrickOrTreatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GhostListener(), this);
        Bukkit.getPluginManager().registerEvents(new JumpScareListener(this), this);
        Bukkit.getPluginManager().registerEvents(new HalloweenMobListener(this), this);
        Bukkit.getPluginManager().registerEvents(bossManager, this);
        Bukkit.getPluginManager().registerEvents(rewardManager, this);
        Bukkit.getPluginManager().registerEvents(menuGUI, this);
        Bukkit.getPluginManager().registerEvents(raidManager, this);
        Bukkit.getPluginManager().registerEvents(new EntityCleanupListener(this), this);
        startAll();
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) hookPlaceholderAPI();
    }

    private void disableSpookySeason() {
        safeStop(hauntedNightManager == null ? null : hauntedNightManager::stop, "hauntedNight");
        safeStop(pumpkinRainManager == null ? null : pumpkinRainManager::stop, "pumpkinRain");
        safeStop(bloodRainManager == null ? null : bloodRainManager::shutdown, "bloodRain");
        safeStop(bossManager == null ? null : bossManager::stop, "boss");
        safeStop(batSwarmManager == null ? null : batSwarmManager::stop, "batSwarm");
        safeStop(rewardManager == null ? null : rewardManager::stop, "rewards");
        safeStop(raidManager == null ? null : raidManager::stop, "raid");
        if (prefs != null) prefs.flush();
        if (stats != null) stats.flush();
    }

    public static HalloweenCore get() { return instance; }
    public NamespacedKey entityMarker() { return entityMarker; }
    public NamespacedKey ghostMarker() { return ghostMarker; }
    public NamespacedKey raidMarker() { return raidMarker; }
    public PlayerPrefs prefs() { return prefs; }
    public PlayerStats stats() { return stats; }
    public RegionIntegration regions() { return regionIntegration; }
    public CustomJukeboxHook jukebox() { return customJukeboxHook; }
    public HauntedNightManager haunted() { return hauntedNightManager; }
    public PumpkinRainManager pumpkins() { return pumpkinRainManager; }
    public BloodRainManager bloodRain() { return bloodRainManager; }
    public HalloweenBossManager boss() { return bossManager; }
    public SeasonRewardManager rewards() { return rewardManager; }
    public SpookyMenuGUI menu() { return menuGUI; }
    public RaidManager raid() { return raidManager; }

    private void handleHorrorCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(ChatColor.RED + "Na tento příkaz nemáš oprávnění.");
            return;
        }
        if (horrorNightsManager == null) {
            sender.sendMessage(ChatColor.RED + "Správce strašidelných nocí není načten.");
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "Strašidelné noci: " +
                    (horrorNightsManager.isActiveAnywhere() ? ChatColor.GREEN + "aktivní" : ChatColor.GRAY + "neaktivní"));
            sender.sendMessage(ChatColor.YELLOW + "Použití: /halloween horror <start|stop|status> [svět]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        World world = args.length >= 3 ? Bukkit.getWorld(args[2])
                : (sender instanceof Player player ? player.getWorld() : null);
        if ((action.equals("start") || action.equals("stop")) && world == null) {
            sender.sendMessage(ChatColor.RED + "Z konzole zadej název světa.");
            return;
        }
        if ("start".equals(action)) {
            if (horrorNightsManager.startManually(world)) {
                sender.sendMessage(ChatColor.GREEN + "Strašidelná noc spuštěna ve světě " + world.getName() + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Strašidelné noci jsou vypnuté nebo tento svět není v horrorNights.worlds povolený.");
            }
        } else if ("stop".equals(action)) {
            horrorNightsManager.stopWorld(world, true);
            sender.sendMessage(ChatColor.GREEN + "Strašidelná noc ukončena ve světě " + world.getName() + ".");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Použití: /halloween horror <start|stop|status> [svět]");
        }
    }

    private void installDefaults() {
        getConfig().addDefault("secret-rooms.enabled", true);
        getConfig().addDefault("secret-rooms.worlds", List.of());
        getConfig().addDefault("secret-rooms.excluded-worlds", List.of("Spawn"));
        getConfig().addDefault("secret-rooms.chance-per-new-chunk", 0.01);
        getConfig().addDefault("secret-rooms.min-depth-below-surface", 16);
        getConfig().addDefault("secret-rooms.max-depth-below-surface", 28);
        getConfig().addDefault("secret-rooms.rarity-weights.common", 70);
        getConfig().addDefault("secret-rooms.rarity-weights.rare", 25);
        getConfig().addDefault("secret-rooms.rarity-weights.legendary", 5);

        getConfig().addDefault("secret-rooms.loot.common.min-fragments", 8);
        getConfig().addDefault("secret-rooms.loot.common.max-fragments", 16);
        getConfig().addDefault("secret-rooms.loot.rare.min-fragments", 20);
        getConfig().addDefault("secret-rooms.loot.rare.max-fragments", 35);
        getConfig().addDefault("secret-rooms.loot.legendary.min-fragments", 40);
        getConfig().addDefault("secret-rooms.loot.legendary.max-fragments", 70);

        getConfig().addDefault("quest-rewards.default-difficulty", "light");
        getConfig().addDefault("quest-rewards.difficulties.light.min-fragments", 3);
        getConfig().addDefault("quest-rewards.difficulties.light.max-fragments", 5);
        getConfig().addDefault("quest-rewards.difficulties.medium.min-fragments", 6);
        getConfig().addDefault("quest-rewards.difficulties.medium.max-fragments", 9);
        getConfig().addDefault("quest-rewards.difficulties.hard.min-fragments", 10);
        getConfig().addDefault("quest-rewards.difficulties.hard.max-fragments", 15);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.min-fragments", 16);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.max-fragments", 25);

        getConfig().addDefault("quest-rewards.difficulties.light.key-chances.common", 0.10);
        getConfig().addDefault("quest-rewards.difficulties.light.key-chances.rare", 0.01);
        getConfig().addDefault("quest-rewards.difficulties.light.key-chances.legendary", 0.001);
        getConfig().addDefault("quest-rewards.difficulties.medium.key-chances.common", 0.15);
        getConfig().addDefault("quest-rewards.difficulties.medium.key-chances.rare", 0.035);
        getConfig().addDefault("quest-rewards.difficulties.medium.key-chances.legendary", 0.005);
        getConfig().addDefault("quest-rewards.difficulties.hard.key-chances.common", 0.20);
        getConfig().addDefault("quest-rewards.difficulties.hard.key-chances.rare", 0.06);
        getConfig().addDefault("quest-rewards.difficulties.hard.key-chances.legendary", 0.02);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.key-chances.common", 0.25);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.key-chances.rare", 0.10);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.key-chances.legendary", 0.08);

        // The sample gives five fragments once and intentionally does not grant a key.
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @Override
    public void onDisable() {
        if (horrorNightsManager != null) horrorNightsManager.stop();
        disableSpookySeason();
        removeAllPluginEntities();
        savePlayerData();
        if (secretRoomManager != null) {
            secretRoomManager.saveRooms();
        }
    }

    public YamlConfiguration getPlayerData() {
        return playerData;
    }

    public DialogQuestManager getQuestManager() {
        return questManager;
    }

    public KeyItemFactory getKeyItemFactory() {
        return keyItemFactory;
    }

    public void savePlayerData() {
        if (playerData == null || playerDataFile == null) return;
        try {
            playerData.save(playerDataFile);
        } catch (IOException exception) {
            getLogger().severe("Nepodařilo se uložit players.yml: " + exception.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "quest" -> handleQuestCommand(sender, args);
            case "fragments" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Tento příkaz může použít jen hráč.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "Máš " + ChatColor.YELLOW
                            + questManager.getFragments(player.getUniqueId()) + ChatColor.GOLD + " fragmentů.");
                }
            }
            case "key" -> handleKeyCommand(sender, args);
            case "horror" -> handleHorrorCommand(sender, args);
            case "rooms" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("status")) {
                    sender.sendMessage(ChatColor.GOLD + "Tajné místnosti: "
                            + (getConfig().getBoolean("secret-rooms.enabled", true)
                            ? ChatColor.GREEN + "zapnuté" : ChatColor.RED + "vypnuté")
                            + ChatColor.GOLD + "; zaregistrované truhly: "
                            + ChatColor.YELLOW + secretRoomManager.getRoomCount());
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "Použití: /halloween rooms status");
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("halloweencore.admin")) {
                    sender.sendMessage(ChatColor.RED + "Na tento příkaz nemáš oprávnění.");
                    return true;
                }
                reload();
                if (horrorNightsManager != null) horrorNightsManager.start();
                sender.sendMessage(ChatColor.GREEN + "Konfigurace načtena. Již vytvořené místnosti se nemění.");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleQuestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED
                    + "Questové příkazy spusť jako hráč, kterému chceš změnit stav úkolu.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW
                    + "Použití: /halloween quest <start|progress|complete|status> <id> [množství]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (List.of("start", "progress", "step", "complete", "finish").contains(action)
                && !player.hasPermission("halloweencore.quest.dialogue")) {
            player.sendMessage(ChatColor.RED
                    + "Tuto akci smí spustit pouze oprávněná questová nebo dialogová integrace.");
            return;
        }
        if (action.equals("list") || (action.equals("status") && args.length < 3)) {
            questManager.sendQuestList(player);
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Chybí ID úkolu.");
            return;
        }
        String id = args[2];
        if (!DialogQuestManager.isValidQuestId(id)) {
            sender.sendMessage(ChatColor.RED + "ID smí obsahovat malá písmena, čísla, pomlčku a podtržítko.");
            return;
        }
        switch (action) {
            case "start" -> questManager.startQuest(player, id);
            case "progress", "step" -> {
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException exception) {
                        sender.sendMessage(ChatColor.RED + "Množství musí být celé číslo.");
                        return;
                    }
                }
                questManager.progressQuest(player, id, amount);
            }
            case "complete", "finish" -> questManager.completeQuest(player, id);
            case "status" -> questManager.sendQuestStatus(player, id);
            default -> sender.sendMessage(ChatColor.YELLOW
                    + "Použití: /halloween quest <start|progress|complete|status> <id> [množství]");
        }
    }

    private void handleKeyCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW
                    + "Použití: /halloween key give [common|rare|legendary] [hráč] [počet]");
            return;
        }
        if (!sender.hasPermission("halloweencore.key.give")) {
            sender.sendMessage(ChatColor.RED + "Na vydávání klíčů nemáš oprávnění.");
            return;
        }

        String rarity = "common";
        int cursor = 2;
        if (cursor < args.length && KeyItemFactory.isValidRarity(args[cursor])) {
            rarity = KeyItemFactory.normalizeRarity(args[cursor]);
            cursor++;
        }

        Player target;
        int amount = 1;
        if (cursor >= args.length) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.YELLOW
                        + "Použití z konzole: /halloween key give [common|rare|legendary] <hráč> [počet]");
                return;
            }
            target = player;
        } else if (sender instanceof Player player && isPositiveInteger(args[cursor])) {
            // Convenient self-grant forms: /halloween key give 3 and /halloween key give rare 3.
            target = player;
            try {
                amount = Integer.parseInt(args[cursor]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(ChatColor.RED + "Počet musí být celé číslo.");
                return;
            }
            cursor++;
        } else {
            target = Bukkit.getPlayerExact(args[cursor]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Hráč musí být online.");
                return;
            }
            cursor++;
            if (cursor < args.length) {
                try {
                    amount = Integer.parseInt(args[cursor]);
                } catch (NumberFormatException exception) {
                    sender.sendMessage(ChatColor.RED + "Počet musí být celé číslo.");
                    return;
                }
                cursor++;
            }
        }

        if (cursor < args.length) {
            sender.sendMessage(ChatColor.YELLOW
                    + "Použití: /halloween key give [common|rare|legendary] [hráč] [počet]");
            return;
        }
        if (amount < 1 || amount > 64) {
            sender.sendMessage(ChatColor.RED + "Počet klíčů musí být v rozsahu 1 až 64.");
            return;
        }

        ItemStack key = keyItemFactory.createKey(rarity, amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(key);
        for (ItemStack leftover : leftovers.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        String rarityLabel = switch (rarity) {
            case "rare" -> "vzácný";
            case "legendary" -> "legendární";
            default -> "obyčejný";
        };
        target.sendMessage(ChatColor.GOLD + "Získal jsi " + amount + "× " + rarityLabel
                + " klíč tajné místnosti.");
        if (sender != target) sender.sendMessage(ChatColor.GREEN + "Klíče předány hráči " + target.getName() + ".");
    }

    private boolean isPositiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "HalloweenCore — nová verze");
        sender.sendMessage(ChatColor.YELLOW + "/halloween quest start <id> " + ChatColor.GRAY + "— přijmout úkol");
        sender.sendMessage(ChatColor.YELLOW + "/halloween quest progress <id> [množství] " + ChatColor.GRAY + "— postup");
        sender.sendMessage(ChatColor.YELLOW + "/halloween quest complete <id> " + ChatColor.GRAY + "— dokončit jednou");
        sender.sendMessage(ChatColor.YELLOW + "/halloween quest status [id] " + ChatColor.GRAY + "— stav úkolů");
        sender.sendMessage(ChatColor.YELLOW + "/halloween fragments " + ChatColor.GRAY + "— počet fragmentů");
        sender.sendMessage(ChatColor.YELLOW + "/halloween rooms status " + ChatColor.GRAY + "— stav místností");
        sender.sendMessage(ChatColor.YELLOW + "/halloween horror <start|stop|status> [svět] " + ChatColor.GRAY + "— strašidelné noci (admin)");
        sender.sendMessage(ChatColor.YELLOW + "/halloween key give [common|rare|legendary] [hráč] [počet] " + ChatColor.GRAY + "— administrátorské vydání klíče");
        if (sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(ChatColor.YELLOW + "/halloween reload " + ChatColor.GRAY + "— načíst konfiguraci");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], List.of("help", "quest", "fragments", "key", "rooms", "horror", "reload"));
        if (args.length == 2 && args[0].equalsIgnoreCase("quest"))
            return partial(args[1], List.of("start", "progress", "complete", "status", "list"));
        if (args.length == 2 && args[0].equalsIgnoreCase("key")) return partial(args[1], List.of("give"));
        if (args.length == 2 && args[0].equalsIgnoreCase("rooms")) return partial(args[1], List.of("status"));
        if (args.length == 2 && args[0].equalsIgnoreCase("horror")) return partial(args[1], List.of("start", "stop", "status"));
        if (args.length == 3 && args[0].equalsIgnoreCase("horror")) {
            List<String> names = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) names.add(world.getName());
            return partial(args[2], names);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return partial(args[2], names);
        }
        return List.of();
    }

    private List<String> partial(String input, List<String> values) {
        String needle = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
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
        Lang.load((Plugin)this);
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
        ConfigMerger.merge((Plugin)this, "config.yml", file, FREE_FORM_CONFIG_PATHS);
        ConfigMerger.removeObsolete((Plugin)this, file, OBSOLETE_CONFIG_PATHS);
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
        int start = cfg.getInt("activeWindow.startDay", 0);
        int end = cfg.getInt("activeWindow.endDay", 0);
        if (start == 0 || end == 0) {
            return true;
        }
        ZonedDateTime now = ZonedDateTime.now();
        if (now.getMonth() != Month.OCTOBER) {
            return false;
        }
        int day = now.getDayOfMonth();
        return day >= start && day <= end;
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
