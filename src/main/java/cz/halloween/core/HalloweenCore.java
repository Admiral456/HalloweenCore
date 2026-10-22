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
    private DialogQuestManager questManager;
    private DailyQuestManager dailyQuestManager;
    private HalloweenShopManager shopManager;
    private HorrorNightManager horrorNightManager;
    private HalloweenPlaceholders placeholders;

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
        questManager = new DialogQuestManager(this);
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
        savePlayerData();
    }

    private void installDefaults() {
        getConfig().addDefault("season.enabled", true);
        getConfig().addDefault("activeWindow.startMonth", 10);
        getConfig().addDefault("activeWindow.startDay", 23);
        getConfig().addDefault("activeWindow.endMonth", 11);
        getConfig().addDefault("activeWindow.endDay", 2);

        getConfig().addDefault("quest-rewards.default-difficulty", "light");
        getConfig().addDefault("quest-rewards.difficulties.light.min-fragments", 3);
        getConfig().addDefault("quest-rewards.difficulties.light.max-fragments", 5);
        getConfig().addDefault("quest-rewards.difficulties.medium.min-fragments", 6);
        getConfig().addDefault("quest-rewards.difficulties.medium.max-fragments", 9);
        getConfig().addDefault("quest-rewards.difficulties.hard.min-fragments", 10);
        getConfig().addDefault("quest-rewards.difficulties.hard.max-fragments", 15);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.min-fragments", 16);
        getConfig().addDefault("quest-rewards.difficulties.very-hard.max-fragments", 25);

        getConfig().addDefault("daily-quest.enabled", true);
        getConfig().addDefault("daily-quest.reward-fragments", 1);
        getConfig().addDefault("shop.enabled", true);
        getConfig().addDefault("shop.prices.candy", 3);
        getConfig().addDefault("shop.prices.iron", 8);
        getConfig().addDefault("shop.prices.gold", 15);
        getConfig().addDefault("shop.prices.diamond", 35);
        getConfig().addDefault("shop.prices.emerald", 25);
        getConfig().addDefault("shop.prices.netherite-ingot", 150);
        getConfig().addDefault("shop.prices.netherite-scrap", 70);
        getConfig().addDefault("shop.prices.golden-apple", 18);
        getConfig().addDefault("shop.prices.enchanted-golden-apple", 80);
        getConfig().addDefault("shop.prices.totem", 350);
        getConfig().addDefault("shop.prices.trident", 550);
        getConfig().addDefault("shop.prices.mace", 800);
        getConfig().addDefault("shop.prices.netherite-sword", 400);
        getConfig().addDefault("shop.prices.netherite-axe", 350);
        getConfig().addDefault("shop.prices.netherite-helmet", 250);
        getConfig().addDefault("shop.prices.netherite-chestplate", 500);
        getConfig().addDefault("shop.prices.netherite-leggings", 400);
        getConfig().addDefault("shop.prices.netherite-boots", 200);
        getConfig().addDefault("shop.prices.elytra", 1200);
        getConfig().addDefault("shop.prices.netherite-spear", 900);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    /**
     * Remove settings for the old mob/event bundle from an existing server config,
     * while preserving quest progress settings and shop prices.
     */
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

        // Preserve the complete SpookySeason/HorrorNights configuration.
        // Only retired features are removed: secret rooms/keys and failed generators.
        for (String key : List.of("secret-rooms", "random-events", "grave-encounters",
                "automatic-world-generation")) {
            cfg.set(key, null);
        }
        for (String tier : List.of("light", "medium", "hard", "very-hard")) {
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

    public DialogQuestManager getQuestManager() {
        return questManager;
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
            case "quest" -> handleQuestCommand(sender, args);
            case "fragments" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Tento příkaz může použít jen hráč.");
                } else {
                    sender.sendMessage(ChatColor.GOLD + "Máš " + ChatColor.YELLOW
                            + questManager.getFragments(player.getUniqueId())
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "HalloweenCore — příkazy");
        sender.sendMessage(ChatColor.YELLOW + "/quest " + ChatColor.GRAY + "— denní úkol za fragment");
        sender.sendMessage(ChatColor.YELLOW + "/halloween shop " + ChatColor.GRAY + "— halloweenský obchod");
        sender.sendMessage(ChatColor.YELLOW + "/halloween quest start|progress|complete|status <id> "
                + ChatColor.GRAY + "— dialogové questy");
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
            return partial(args[0], List.of("help", "quest", "fragments", "shop", "horror", "reload"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("horror")) {
            return partial(args[1], List.of("start", "stop", "status", "reload"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("horror")) {
            return partial(args[2], Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("quest")) {
            return partial(args[1], List.of("start", "progress", "complete", "status", "list"));
        }
        return List.of();
    }

    private List<String> partial(String input, List<String> values) {
        String needle = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).toList();
    }
}
