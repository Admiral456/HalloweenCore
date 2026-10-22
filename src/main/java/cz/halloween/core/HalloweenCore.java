package cz.halloween.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

public final class HalloweenCore extends JavaPlugin implements CommandExecutor, TabCompleter {
    private File playerDataFile;
    private YamlConfiguration playerData;
    private DialogQuestManager questManager;
    private KeyItemFactory keyItemFactory;
    private SecretRoomManager secretRoomManager;
    private HorrorNightManager horrorNightManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        org.example.SpookySeason.enable(this);
        horrorNightManager = new HorrorNightManager(this);
        horrorNightManager.start();
        getLogger().info("HalloweenCore spuštěn: questy, fragmenty, tajné místnosti, Trick-or-Treat, sezónní eventy, nájezdy a hororové noci.");
    }

    private void installDefaults() {
        getConfig().addDefault("horrorNights.enabled", true);
        getConfig().addDefault("horrorNights.horror-night-chance", 10.0);
        getConfig().addDefault("horrorNights.check-interval-ticks", 6000);
        getConfig().addDefault("horrorNights.spawn-interval-ticks", 60);
        getConfig().addDefault("horrorNights.enabled-worlds", List.of());
        getConfig().addDefault("horrorNights.excluded-worlds", List.of("Spawn"));
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

        // Configurable example used by the bundled BetonQuest dialogue package.
        // The sample gives five fragments once and intentionally does not grant a key.
        getConfig().addDefault("quest-rewards.quests.farmer_wheat.difficulty", "light");
        getConfig().addDefault("quest-rewards.quests.farmer_wheat.fragments", 5);
        getConfig().addDefault("quest-rewards.quests.farmer_wheat.key-chances.common", 0.0);
        getConfig().addDefault("quest-rewards.quests.farmer_wheat.key-chances.rare", 0.0);
        getConfig().addDefault("quest-rewards.quests.farmer_wheat.key-chances.legendary", 0.0);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @Override
    public void onDisable() {
        if (horrorNightManager != null) horrorNightManager.shutdown();
        if (org.example.SpookySeason.get() != null) org.example.SpookySeason.get().disable();
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
            case "horror" -> handleHorrorCommand(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("halloweencore.admin")) {
                    sender.sendMessage(ChatColor.RED + "Na tento příkaz nemáš oprávnění.");
                    return true;
                }
                reloadConfig();
                installDefaults();
                if (org.example.SpookySeason.get() != null) org.example.SpookySeason.get().reload();
                if (horrorNightManager != null) horrorNightManager.reload();
                sender.sendMessage(ChatColor.GREEN + "Konfigurace načtena. Již vytvořené místnosti se nemění.");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleHorrorCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(ChatColor.RED + "Na správu hororových nocí nemáš oprávnění.");
            return;
        }
        if (horrorNightManager == null) {
            sender.sendMessage(ChatColor.RED + "Správce hororových nocí není inicializován.");
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        if (action.equals("status")) {
            List<String> active = horrorNightManager.getActiveWorldNames();
            sender.sendMessage(ChatColor.GOLD + "Hororové noci: "
                    + (active.isEmpty() ? ChatColor.GRAY + "žádný aktivní svět" : ChatColor.RED + String.join(", ", active)));
            return;
        }
        if (!action.equals("start") && !action.equals("stop")) {
            sender.sendMessage(ChatColor.YELLOW + "Použití: /halloween horror <start|stop|status> [svět]");
            return;
        }
        org.bukkit.World world = args.length >= 3 ? Bukkit.getWorld(args[2])
                : (sender instanceof Player player ? player.getWorld() : null);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Zadej existující svět, například /halloween horror start world.");
            return;
        }
        if (action.equals("start")) {
            if (horrorNightManager.startManually(world)) sender.sendMessage(ChatColor.GREEN + "Hororová noc spuštěna ve světě " + world.getName() + ".");
            else sender.sendMessage(ChatColor.YELLOW + "Hororová noc už běží, svět je vyloučený nebo právě není noc.");
        } else {
            if (horrorNightManager.stopWorld(world)) sender.sendMessage(ChatColor.GREEN + "Hororová noc zastavena ve světě " + world.getName() + ".");
            else sender.sendMessage(ChatColor.YELLOW + "V tomto světě žádná hororová noc neběží.");
        }
    }

    private void handleQuestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED
                    + "Questové příkazy spusť jako hráč, se kterým vede dialog UplineDialogues.");
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
                    + "Tuto akci smí spustit jen nakonfigurovaný dialog. V UplineDialogues použij běh příkazu jako OP.");
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
        sender.sendMessage(ChatColor.YELLOW + "/halloween horror <start|stop|status> [svět] " + ChatColor.GRAY + "— správa hororových nocí");
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
        if (args.length == 3 && args[0].equalsIgnoreCase("horror")) return partial(args[2], Bukkit.getWorlds().stream().map(org.bukkit.World::getName).toList());
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
}
