package cz.halloween.core;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HalloweenCore extends JavaPlugin implements CommandExecutor, TabCompleter {
    private File playerDataFile;
    private YamlConfiguration playerData;
    private FragmentManager fragmentManager;
    private DailyQuestManager dailyQuestManager;
    private HalloweenShopManager shopManager;
    private HorrorNightManager horrorNightManager;
    private HalloweenPlaceholders placeholders;
    private org.bukkit.event.Listener jobsFragmentBridge;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Nelze vytvořit datovou složku HalloweenCore.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        migrateLegacyConfig();
        reloadConfig();
        installDefaults();

        playerDataFile = new File(getDataFolder(), "players.yml");
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        fragmentManager = new FragmentManager(this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholders = new HalloweenPlaceholders(this);
            if (placeholders.register()) {
                getLogger().info("PlaceholderAPI expansion registered: %halloween_*%");
            } else {
                placeholders = null;
                getLogger().warning("PlaceholderAPI expansion could not be registered.");
            }
        }
        dailyQuestManager = new DailyQuestManager(this);
        getServer().getPluginManager().registerEvents(dailyQuestManager, this);
        shopManager = new HalloweenShopManager(this);

        PluginCommand dailyQuest = getCommand("quest");
        if (dailyQuest != null) {
            dailyQuest.setExecutor(dailyQuestManager);
            dailyQuest.setTabCompleter(dailyQuestManager);
        }
        PluginCommand halloween = getCommand("halloween");
        if (halloween != null) {
            halloween.setExecutor(this);
            halloween.setTabCompleter(this);
        }

        org.example.SpookySeason.enable(this);
        horrorNightManager = new HorrorNightManager(this);
        horrorNightManager.start();
        registerJobsFragmentBridge();

        PluginCommand horrorNights = getCommand("horrornights");
        if (horrorNights != null) {
            horrorNights.setExecutor(this);
            horrorNights.setTabCompleter(this);
        }

        getLogger().info("HalloweenCore načten: SpookySeason, HorrorNights, denní questy, fragmenty a obchod.");
    }

    @Override
    public void onDisable() {
        if (placeholders != null) {
            placeholders.unregister();
            placeholders = null;
        }
        if (horrorNightManager != null) horrorNightManager.shutdown();
        if (org.example.SpookySeason.get() != null) org.example.SpookySeason.get().disable();
        flushJobsFragmentBridge();
        savePlayerData();
    }

    private void installDefaults() {
        getConfig().addDefault("season.enabled", true);
        getConfig().addDefault("activeWindow.startMonth", 10);
        getConfig().addDefault("activeWindow.startDay", 23);
        getConfig().addDefault("activeWindow.endMonth", 11);
        getConfig().addDefault("activeWindow.endDay", 2);

        getConfig().addDefault("quest-rewards.default-difficulty", "light");
        getConfig().addDefault("quest-rewards.daily-completion-bonus", 15);
        getConfig().addDefault("quest-rewards.weekly-completion-bonus", 50);
        getConfig().addDefault("quest-rewards.weekly-multiplier", 3.0);
        getConfig().addDefault("quest-rewards.difficulties.light.min-fragments", 1);
        getConfig().addDefault("quest-rewards.difficulties.light.max-fragments", 2);
        getConfig().addDefault("quest-rewards.difficulties.medium.min-fragments", 2);
        getConfig().addDefault("quest-rewards.difficulties.medium.max-fragments", 4);
        getConfig().addDefault("quest-rewards.difficulties.hard.min-fragments", 5);
        getConfig().addDefault("quest-rewards.difficulties.hard.max-fragments", 8);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.min-fragments", 8);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.max-fragments", 12);
        getConfig().addDefault("quest-rewards.difficulties.extreme.min-fragments", 10);
        getConfig().addDefault("quest-rewards.difficulties.extreme.max-fragments", 16);

        getConfig().addDefault("jobs-fragments.enabled", true);
        getConfig().addDefault("jobs-fragments.experience-per-fragment", 25.0);
        getConfig().addDefault("jobs-fragments.max-per-day", 8);

        getConfig().addDefault("horrorNights.effect-interval-ticks", 200);

        getConfig().addDefault("daily-quest.enabled", true);
        getConfig().addDefault("daily-quest.reward-fragments", 1);
        getConfig().addDefault("shop.enabled", true);

        String[][] prices = {
            {"candy","3"},{"coal","10"},{"copper","12"},{"raw-iron","15"},{"iron","18"},
            {"raw-gold","14"},{"gold","20"},{"redstone","15"},{"lapis","15"},{"quartz","18"},
            {"emerald","30"},{"diamond","45"},{"netherite-scrap","125"},{"netherite-ingot","350"},
            {"cobblestone","10"},{"stone","12"},{"deepslate","16"},{"oak-logs","15"},{"dark-oak-logs","20"},
            {"glass","15"},{"obsidian","35"},{"quartz-block","30"},{"glowstone","25"},{"slime-block","40"},
            {"tnt","45"},{"bread","10"},{"cooked-beef","18"},{"baked-potato","12"},{"golden-carrot","25"},
            {"pumpkin-pie","15"},{"honey-bottle","18"},{"golden-apple","100"},{"enchanted-golden-apple","1000"},
            {"ender-pearl","25"},{"blaze-rod","35"},{"arrow","12"},{"firework-rocket","25"},
            {"experience-bottle","40"},{"ender-chest","60"},{"shulker-box","200"},{"anvil","45"},
            {"name-tag","90"},{"saddle","65"},{"water-bucket","20"},{"lead","15"},{"compass","45"},
            {"clock","45"},{"recovery-compass","180"},{"spyglass","60"},{"fishing-rod","50"},{"shears","35"},
            {"flint-and-steel","40"},{"dragon-head","450"},{"end-crystal","225"},{"conduit","650"},
            {"totem","750"},{"beacon","1600"},{"nether-star","900"},{"iron-pickaxe","35"},
            {"diamond-sword","120"},{"diamond-pickaxe","150"},{"diamond-axe","120"},{"diamond-shovel","85"},
            {"diamond-hoe","95"},{"netherite-sword","600"},{"netherite-axe","550"},{"netherite-pickaxe","700"},
            {"netherite-shovel","350"},{"netherite-hoe","400"},{"bow","80"},{"crossbow","100"},{"shield","70"},
            {"trident","850"},{"mace","1400"},{"iron-helmet","35"},
            {"iron-chestplate","60"},{"iron-leggings","50"},{"iron-boots","30"},{"diamond-helmet","150"},
            {"diamond-chestplate","250"},{"diamond-leggings","200"},{"diamond-boots","120"},
            {"golden-horse-armor","100"},{"diamond-horse-armor","180"},{"netherite-helmet","450"},
            {"netherite-chestplate","850"},{"netherite-leggings","700"},{"netherite-boots","450"},{"elytra","2000"},
            {"amethyst-shard","30"},{"prismarine-shard","25"},{"prismarine-crystals","40"},{"slime-ball","30"},
            {"magma-cream","50"},{"phantom-membrane","110"},{"ghast-tear","130"},{"shulker-shell","125"},
            {"turtle-scute","150"},{"nautilus-shell","90"},{"heart-of-the-sea","250"},{"crying-obsidian","45"},
            {"blaze-powder","45"},{"ender-eye","120"},{"dragon-breath","100"},{"netherite-template","1100"},
            {"prismarine-bricks","45"},{"sea-lantern","60"},{"terracotta","25"},{"orange-concrete","30"},
            {"lantern","25"},{"campfire","20"},{"chain","35"},
            {"dirt","8"},
            {"sand","10"},
            {"gravel","10"},
            {"netherrack","12"},
            {"soul-sand","20"},
            {"soul-soil","20"},
            {"basalt","18"},
            {"blackstone","18"},
            {"end-stone","35"},
            {"purpur-block","45"},
            {"bricks","20"},
            {"bookshelf","45"},
            {"enchanting-table","100"},
            {"brewing-stand","50"},
            {"hopper","55"},
            {"dispenser","40"},
            {"dropper","35"},
            {"piston","45"},
            {"sticky-piston","75"},
            {"observer","55"},
            {"comparator","35"},
            {"repeater","25"},
            {"chest","15"},
            {"barrel","18"},
            {"minecart","65"},
            {"rail","20"},
            {"powered-rail","55"},
            {"armor-stand","25"},
            {"leather","12"},
            {"leather-chestplate","35"},
            {"turtle-helmet","160"},
            {"golden-helmet","55"},
            {"golden-sword","25"},
            {"iron-sword","30"},
            {"iron-axe","35"},
            {"bone-block","35"},
            {"moss-block","30"},
            {"sponge","100"},
            {"sea-pickle","30"},
            {"flower-pot","20"},
            {"pumpkin","12"},
            {"carved-pumpkin","20"},
            {"jack-o-lantern","30"},
            {"honey-block","45"},
            {"target","35"},
            {"redstone-lamp","30"},
            {"redstone-torch","20"}
        };
        for (String[] price : prices) {
            getConfig().addDefault("shop.prices." + price[0], Integer.parseInt(price[1]));
        }
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void migrateLegacyConfig() {
        File file = new File(getDataFolder(), "config.yml");
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int startMonth = cfg.getInt("activeWindow.startMonth", 10);
        int startDay = cfg.getInt("activeWindow.startDay", 27);
        int endMonth = cfg.getInt("activeWindow.endMonth", 10);
        int endDay = cfg.getInt("activeWindow.endDay", 31);
        if (startMonth == 10 && startDay == 27 && endMonth == 10 && endDay == 31) {
            cfg.set("activeWindow.startMonth", 10);
            cfg.set("activeWindow.startDay", 23);
            cfg.set("activeWindow.endMonth", 11);
            cfg.set("activeWindow.endDay", 2);
        }
        if (!cfg.contains("activeWindow.startMonth")) cfg.set("activeWindow.startMonth", 10);
        if (!cfg.contains("activeWindow.startDay")) cfg.set("activeWindow.startDay", 23);
        if (!cfg.contains("activeWindow.endMonth")) cfg.set("activeWindow.endMonth", 11);
        if (!cfg.contains("activeWindow.endDay")) cfg.set("activeWindow.endDay", 2);

        // Upgrade only untouched shipped defaults; customized server prices are preserved.
        migrateDefaultPrice(cfg, "shop.prices.enchanted-golden-apple", 1000, 750, 80, 250);
        migrateDefaultPrice(cfg, "shop.prices.golden-apple", 100, 65, 18, 45);
        migrateDefaultPrice(cfg, "shop.prices.iron", 18, 8);
        migrateDefaultPrice(cfg, "shop.prices.gold", 20, 15);
        migrateDefaultPrice(cfg, "shop.prices.emerald", 30, 25);
        migrateDefaultPrice(cfg, "shop.prices.diamond", 45, 35);
        migrateDefaultPrice(cfg, "shop.prices.netherite-ingot", 350, 150, 250);
        migrateDefaultPrice(cfg, "shop.prices.netherite-scrap", 125, 70, 100);
        migrateDefaultPrice(cfg, "shop.prices.totem", 750, 350, 380);
        migrateDefaultPrice(cfg, "shop.prices.beacon", 1600, 1000);
        migrateDefaultPrice(cfg, "shop.prices.nether-star", 900, 750);
        migrateDefaultPrice(cfg, "shop.prices.trident", 850, 550, 650);
        migrateDefaultPrice(cfg, "shop.prices.mace", 1400, 800, 1000);
        migrateDefaultPrice(cfg, "shop.prices.elytra", 2000, 1200);
        migrateDefaultPrice(cfg, "shop.prices.netherite-sword", 600, 400);
        migrateDefaultPrice(cfg, "shop.prices.netherite-axe", 550, 350, 360);
        migrateDefaultPrice(cfg, "shop.prices.netherite-helmet", 450, 250, 300);
        migrateDefaultPrice(cfg, "shop.prices.netherite-chestplate", 850, 500, 600);
        migrateDefaultPrice(cfg, "shop.prices.netherite-leggings", 700, 400, 480);
        migrateDefaultPrice(cfg, "shop.prices.netherite-boots", 450, 200, 300);
        migrateDefaultInteger(cfg, "quest-rewards.daily-completion-bonus", 15, 8);
        migrateDefaultInteger(cfg, "quest-rewards.weekly-completion-bonus", 50, 8);
        migrateDefaultDouble(cfg, "quest-rewards.weekly-multiplier", 3.0, 1.0, 2.0);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.light.min-fragments", 1, 3);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.light.max-fragments", 2, 5);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.medium.min-fragments", 2, 8, 6);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.medium.max-fragments", 4, 12, 9);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.hard.min-fragments", 5, 18, 10);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.hard.max-fragments", 8, 28, 15);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.very-hard.min-fragments", 8, 32, 16);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.very-hard.max-fragments", 12, 48, 25);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.extreme.min-fragments", 10, 55);
        migrateDefaultInteger(cfg, "quest-rewards.difficulties.extreme.max-fragments", 16, 80);
        migrateDefaultInteger(cfg, "jobs-fragments.max-per-day", 8);
        migrateDefaultDouble(cfg, "jobs-fragments.experience-per-fragment", 25.0);

        // Preserve the complete SpookySeason/HorrorNights configuration.
        // Only retired features are removed: secret rooms/keys and failed generators.
        for (String key : List.of("secret-rooms", "random-events", "grave-encounters",
                "automatic-world-generation")) {
            cfg.set(key, null);
        }
        for (String tier : List.of("light", "medium", "hard", "very-hard", "extreme")) {
            cfg.set("quest-rewards.difficulties." + tier + ".key-chances", null);
        }
        ConfigurationSection overrides = cfg.getConfigurationSection("quest-rewards.quests");
        if (overrides != null) {
            for (String id : overrides.getKeys(false)) {
                cfg.set("quest-rewards.quests." + id + ".key-chances", null);
            }
        }
        try {
            cfg.save(file);
        } catch (IOException exception) {
            getLogger().warning("Nepodařilo se vyčistit starou konfiguraci: " + exception.getMessage());
        }
    }

    private void registerJobsFragmentBridge() {
        if (Bukkit.getPluginManager().getPlugin("Jobs") == null) {
            getLogger().info("Jobs Reborn není nainstalován; fragmenty z jobů zůstávají neaktivní.");
            return;
        }
        try {
            Class<?> bridgeType = Class.forName("cz.halloween.core.JobsFragmentBridge");
            Object instance = bridgeType.getConstructor(HalloweenCore.class).newInstance(this);
            if (!(instance instanceof org.bukkit.event.Listener listener)) {
                throw new IllegalStateException("Jobs bridge does not implement Listener");
            }
            getServer().getPluginManager().registerEvents(listener, this);
            jobsFragmentBridge = listener;
            getLogger().info("Jobs Reborn propojen: fragmenty za XP (limit dle config.yml).");
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException exception) {
            getLogger().warning("Jobs Reborn fragment bridge se nepodařilo zapnout: " + exception.getMessage());
        }
    }

    private void flushJobsFragmentBridge() {
        if (jobsFragmentBridge == null) return;
        try {
            jobsFragmentBridge.getClass().getMethod("flush").invoke(jobsFragmentBridge);
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().warning("Nepodařilo se uložit postup Jobs fragmentů: " + exception.getMessage());
        } finally {
            jobsFragmentBridge = null;
        }
    }

    private void migrateDefaultPrice(YamlConfiguration cfg, String key, int target, int... legacyValues) {
        migrateDefaultInteger(cfg, key, target, legacyValues);
    }

    private void migrateDefaultInteger(YamlConfiguration cfg, String key, int target, int... legacyValues) {
        if (!cfg.contains(key)) return;
        int current = cfg.getInt(key);
        for (int legacy : legacyValues) {
            if (current == legacy) {
                cfg.set(key, target);
                return;
            }
        }
    }

    private void migrateDefaultDouble(YamlConfiguration cfg, String key, double target, double... legacyValues) {
        if (!cfg.contains(key)) return;
        double current = cfg.getDouble(key);
        for (double legacy : legacyValues) {
            if (Double.compare(current, legacy) == 0) {
                cfg.set(key, target);
                return;
            }
        }
    }

    public boolean isSeasonActive() {
        return getConfig().getBoolean("season.enabled", true) && isInSeasonWindow();
    }

    public boolean isInSeasonWindow() {
        int startDay = getConfig().getInt("activeWindow.startDay", 0);
        int endDay = getConfig().getInt("activeWindow.endDay", 0);
        if (startDay == 0 || endDay == 0) return true;

        int startMonth = Math.max(1, Math.min(12, getConfig().getInt("activeWindow.startMonth", 10)));
        int endMonth = Math.max(1, Math.min(12, getConfig().getInt("activeWindow.endMonth", startMonth)));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Prague"));
        int current = now.getMonthValue() * 100 + now.getDayOfMonth();
        int start = startMonth * 100 + startDay;
        int end = endMonth * 100 + endDay;
        return start <= end ? current >= start && current <= end : current >= start || current <= end;
    }

    public YamlConfiguration getPlayerData() {
        return playerData;
    }

    public FragmentManager getFragmentManager() {
        return fragmentManager;
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
        if (command.getName().equalsIgnoreCase("horrornights")) {
            return handleHorrorNightCommand(sender, args, 0);
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "fragments" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Tento příkaz může použít jen hráč.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "Máš " + ChatColor.YELLOW
                            + fragmentManager.getFragments(player.getUniqueId())
                            + ChatColor.GOLD + " fragmentů.");
                }
            }
            case "shop" -> shopManager.open(sender);
            case "horror" -> handleHorrorNightCommand(sender, args, 1);
            case "reload" -> {
                if (!sender.hasPermission("halloweencore.admin")) {
                    sender.sendMessage(ChatColor.RED + "Na tento příkaz nemáš oprávnění.");
                    return true;
                }
                migrateLegacyConfig();
                reloadConfig();
                installDefaults();
                shopManager.refresh();
                if (org.example.SpookySeason.get() != null) org.example.SpookySeason.get().reload();
                if (horrorNightManager != null) horrorNightManager.reload();
                sender.sendMessage(ChatColor.GREEN + "Konfigurace načtena.");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private boolean handleHorrorNightCommand(CommandSender sender, String[] args, int offset) {
        if (args.length <= offset || args[offset].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.GOLD + "HorrorNights — příkazy");
            sender.sendMessage(ChatColor.YELLOW + "/horrornights <start|stop|status|reload> [svět]");
            sender.sendMessage(ChatColor.YELLOW + "/halloween horror <start|stop|status|reload> [svět]");
            return true;
        }
        String action = args[offset].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("horrornights.admin") && !sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(ChatColor.RED + "Na tento příkaz nemáš oprávnění.");
            return true;
        }
        if (horrorNightManager == null) {
            sender.sendMessage(ChatColor.RED + "HorrorNights není inicializovaný.");
            return true;
        }
        if (action.equals("status")) {
            List<String> active = horrorNightManager.getActiveWorldNames();
            sender.sendMessage(active.isEmpty()
                    ? ChatColor.GRAY + "Právě neprobíhá žádná hororová noc."
                    : ChatColor.GOLD + "Aktivní hororové noci: " + ChatColor.YELLOW + String.join(", ", active));
            return true;
        }
        if (action.equals("reload")) {
            reloadConfig();
            installDefaults();
            horrorNightManager.reload();
            sender.sendMessage(ChatColor.GREEN + "Nastavení HorrorNights načteno.");
            return true;
        }
        String worldName = args.length > offset + 1 ? args[offset + 1] : null;
        World world = worldName == null
                ? (sender instanceof Player player ? player.getWorld() : null)
                : Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Zadej platný svět; konzole musí uvést jeho název.");
            return true;
        }
        if (action.equals("start")) {
            if (horrorNightManager.startManually(world)) {
                sender.sendMessage(ChatColor.GREEN + "Hororová noc spuštěna ve světě " + world.getName() + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Noc nelze spustit. Ověř noční čas, povolené světy a horrorNights.enabled.");
            }
        } else if (action.equals("stop")) {
            sender.sendMessage(horrorNightManager.stopWorld(world)
                    ? ChatColor.GREEN + "Hororová noc zastavena ve světě " + world.getName() + "."
                    : ChatColor.RED + "V tomto světě žádná hororová noc neběží.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Použití: /horrornights <start|stop|status|reload> [svět]");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "HalloweenCore — příkazy");
        sender.sendMessage(ChatColor.YELLOW + "/quest " + ChatColor.GRAY + "— denní a týdenní kontrakty");
        sender.sendMessage(ChatColor.YELLOW + "/halloween shop " + ChatColor.GRAY + "— halloweenský obchod");
        sender.sendMessage(ChatColor.YELLOW + "/halloween fragments " + ChatColor.GRAY + "— stav fragmentů");
        sender.sendMessage(ChatColor.YELLOW + "/halloween reload " + ChatColor.GRAY + "— načtení konfigurace "
                + "(admin)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("horrornights")) {
            if (args.length == 1) return partial(args[0], List.of("start", "stop", "status", "reload", "help"));
            if (args.length == 2) return partial(args[1], Bukkit.getWorlds().stream().map(World::getName).toList());
            return List.of();
        }
        if (args.length == 1) {
            return partial(args[0], List.of("help", "fragments", "shop", "horror", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("horror")) {
            return partial(args[1], List.of("start", "stop", "status", "reload"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("horror")) {
            return partial(args[2], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        return List.of();
    }

    private List<String> partial(String input, List<String> values) {
        String needle = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }
}
