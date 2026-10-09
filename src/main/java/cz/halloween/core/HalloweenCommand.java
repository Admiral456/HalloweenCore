package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HalloweenCommand implements CommandExecutor, TabCompleter {
    private final HalloweenCore plugin;

    public HalloweenCommand(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = List.of(
                    "stats", "progress", "curse", "event", "challenge",
                    "rewards", "claim", "top", "reload", "debug", "setvillage", "setvampirearena", "buildvampirearena", "setsecret", "secrets", "boss", "bosseffects", "modelpreview", "give", "shader", "on", "off"
            );
            return subcommands.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("event") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "start", "stop", "soulstorm", "witching-hour",
                    "cursed-harvest", "blood-moon-invasion", "pumpkin-apocalypse", "graveyard-rising", "random").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event")
                && args[1].equalsIgnoreCase("start") && sender.hasPermission("halloweencore.admin")) {
            return List.of("random", "soulstorm", "witching-hour", "cursed-harvest", "blood-moon-invasion",
                    "pumpkin-apocalypse", "graveyard-rising").stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("shader") && sender.hasPermission("halloweencore.admin")) {
            return List.of("on", "off", "reload").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buildvampirearena") && sender.hasPermission("halloweencore.admin")) {
            return List.of("confirm").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setsecret") && sender.hasPermission("halloweencore.admin")) {
            var secrets = plugin.getConfig().getConfigurationSection("secret-discoveries.locations");
            if (secrets == null) return List.of();
            return secrets.getKeys(false).stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            var section = plugin.getConfig().getConfigurationSection("rewards.shop");
            if (section == null) return List.of();
            return section.getKeys(false).stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("modelpreview") && sender.hasPermission("halloweencore.admin")) {
            return List.of("idle", "walk", "attack", "fly", "false_sigil", "blood_pulse", "mirror_strike", "nightfall")
                    .stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bosseffects") && sender.hasPermission("halloweencore.admin")) {
            return List.of("1", "2", "3", "4").stream()
                    .filter(value -> value.startsWith(args[1]))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("event") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "start", "stop", "soulstorm", "witching-hour", "cursed-harvest",
                    "blood-moon-invasion", "pumpkin-apocalypse", "graveyard-rising", "random")
                    .stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("start")
                && sender.hasPermission("halloweencore.admin")) {
            return List.of("soulstorm", "witching-hour", "cursed-harvest", "blood-moon-invasion",
                    "pumpkin-apocalypse", "graveyard-rising", "random")
                    .stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("boss") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "start", "test", "stop").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) return stats(sender);
        if (args[0].equalsIgnoreCase("top")) return top(sender);
        if (args[0].equalsIgnoreCase("progress")) return progress(sender);
        if (args[0].equalsIgnoreCase("curse")) return curse(sender);
        if (args[0].equalsIgnoreCase("event")) return event(sender, args);
        if (args[0].equalsIgnoreCase("rewards")) return rewards(sender);
        if (args[0].equalsIgnoreCase("claim")) return claim(sender, args);
        if (args[0].equalsIgnoreCase("challenge")) return challenge(sender);
        if (args[0].equalsIgnoreCase("reload")) return reload(sender);
        if (args[0].equalsIgnoreCase("debug")) return debug(sender);
        if (args[0].equalsIgnoreCase("shader")) return shader(sender, args);
        if (args[0].equalsIgnoreCase("setvillage")) return setVillage(sender);
        if (args[0].equalsIgnoreCase("setvampirearena")) return setVampireArena(sender);
        if (args[0].equalsIgnoreCase("buildvampirearena")) return buildVampireArena(sender, args);
        if (args[0].equalsIgnoreCase("setsecret")) return setSecret(sender, args);
        if (args[0].equalsIgnoreCase("secrets")) return showSecrets(sender);
        if (args[0].equalsIgnoreCase("boss")) return boss(sender, args);
        if (args[0].equalsIgnoreCase("bosseffects")) return bossEffects(sender, args);
        if (args[0].equalsIgnoreCase("modelpreview")) return modelPreview(sender, args);
        if (args[0].equalsIgnoreCase("give")) return give(sender, args);
        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")) return toggle(sender, args[0]);

        sender.sendMessage(plugin.color("&6/halloween &7- tvoje Halloween statistiky"));
        sender.sendMessage(plugin.color("&6/halloween progress &7- společný progress serveru"));
        sender.sendMessage(plugin.color("&6/halloween curse &7- tvoje úroveň prokletí"));
        sender.sendMessage(plugin.color("&6/halloween event [start|stop] &7- stav a testovací spuštění eventu (admin)"));
        sender.sendMessage(plugin.color("&6/halloween rewards &7- limitované odměny 2026"));
        sender.sendMessage(plugin.color("&6/halloween challenge &7- dnešní Halloween lov"));
        sender.sendMessage(plugin.color("&6/halloween claim <id> &7- vyzvednutí odměny"));
        sender.sendMessage(plugin.color("&6/halloween top &7- leaderboard"));
        sender.sendMessage(plugin.color("&6/halloween reload &7- reload configu"));
        sender.sendMessage(plugin.color("&6/halloween debug &7- diagnostika integrací (admin)"));
        sender.sendMessage(plugin.color("&6/halloween shader <on|off|reload> &7- upraví shader a znovu sestaví ItemsAdder pack"));
        sender.sendMessage(plugin.color("&6/halloween setvillage &7- nastavit Haunted Village na pozici hráče (admin)"));
        sender.sendMessage(plugin.color("&6/halloween setsecret <id> &7- nastavit tajné místo (admin)"));
        sender.sendMessage(plugin.color("&6/halloween secrets &7- nápovědy a postup tajných objevů"));
        sender.sendMessage(plugin.color("&6/halloween setvampirearena &7- nastavit arénu Krále upírů na pozici hráče (admin)"));
        sender.sendMessage(plugin.color("&6/halloween buildvampirearena [confirm] &7- náhled a bezpečná stavba arény (admin)"));
        sender.sendMessage(plugin.color("&6/halloween boss <status|start|test|stop> &7- finále nebo bezpečný test bosse (admin)"));
        sender.sendMessage(plugin.color("&6/halloween bosseffects <1|2|3|4> &7- bezpečný vizuální náhled útoků bosse (admin)"));
        sender.sendMessage(plugin.color("&6/halloween modelpreview <animace> &7- bezpečný 10s náhled modelu a animace (admin)"));
        sender.sendMessage(plugin.color("&6/halloween give <hráč> <počet> &7- admin"));
        sender.sendMessage(plugin.color("&6/halloween on|off &7- zapnutí/vypnutí eventu"));
        return true;
    }

    private boolean stats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz může použít jen hráč."));
            return true;
        }
        if (!checkUse(player) || !checkEnabled(player)) return true;

        long amount = plugin.getService().getFragments(player.getUniqueId());
        long goal = plugin.getService().getGlobalGoal();
        long total = plugin.getService().getServerFragments();
        int curse = plugin.getService().getCurseLevel(player.getUniqueId());
        String curseName = plugin.getService().getCurseName(player.getUniqueId());
        int streak = plugin.getDataManager().getStreak(player.getUniqueId());

        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        sender.sendMessage(plugin.color("&6&lHALLOWEEN 2026"));
        sender.sendMessage(plugin.color("&7Tvoje fragmenty: &e" + amount));
        sender.sendMessage(plugin.color("&7Celoživotně získáno: &e" + plugin.getDataManager().getLifetimeFragments(player.getUniqueId())));
        sender.sendMessage(plugin.color("&7Prokletí: &e" + curse + " &8(" + curseName + "&8)"));
        sender.sendMessage(plugin.color("&7Denní streak: &e" + streak + "&7/7"));
        sender.sendMessage(plugin.color("&7Tvůj násobič fragmentů: &e" + String.format("%.2f", plugin.getService().getFragmentMultiplier(player.getUniqueId(), "mob-kill")) + "x"));
        String active = plugin.getEventManager() == null ? null : plugin.getEventManager().getActiveEventId();
        if (active != null) {
            String eventName = plugin.getConfig().getString("random-events.types." + active + ".name", active);
            sender.sendMessage(plugin.color("&7Aktivní událost: &6" + eventName + " &7(" + plugin.getEventManager().getRemainingSeconds() + " s)"));
        } else {
            sender.sendMessage(plugin.color("&7Další událost přibližně za: &e" + plugin.getEventManager().getNextEventSeconds() + " s"));
        }
        sender.sendMessage(plugin.color("&7Serverový progress: &e" + total + " &7/ &e" + goal));
        sender.sendMessage(plugin.color("&7Progress: &e" + String.format("%.1f", plugin.getService().getGlobalProgressPercent()) + "%"));
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        return true;
    }

    private boolean progress(CommandSender sender) {
        if (!checkUse(sender) || !checkEnabled(sender)) return true;

        long total = plugin.getService().getServerFragments();
        long goal = plugin.getService().getGlobalGoal();
        sender.sendMessage(plugin.color("&6&lHALLOWEEN PROGRESS"));
        sender.sendMessage(plugin.color("&7Aktuálně: &e" + total + " &7/ &e" + goal));
        sender.sendMessage(plugin.color("&7Progress: &e" + String.format("%.1f", plugin.getService().getGlobalProgressPercent()) + "%"));
        return true;
    }

    private boolean curse(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz může použít jen hráč."));
            return true;
        }
        if (!checkUse(player) || !checkEnabled(player)) return true;

        int level = plugin.getService().getCurseLevel(player.getUniqueId());
        String name = plugin.getService().getCurseName(player.getUniqueId());
        sender.sendMessage(plugin.color("&6&lTVÉ PROKLETÍ"));
        sender.sendMessage(plugin.color("&7Úroveň: &e" + level));
        sender.sendMessage(plugin.color("&7Titul: &e" + name));
        return true;
    }

    private boolean event(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!checkAdmin(sender)) return true;
            if (plugin.getEventManager() == null) {
                sender.sendMessage(plugin.color("&cSprávce eventů není dostupný."));
                return true;
            }
            String action = args[1].toLowerCase(java.util.Locale.ROOT);
            if (action.equals("start")) {
                String requested = args.length >= 3 ? args[2] : "random";
                if (!plugin.isEventEnabled()) {
                    sender.sendMessage(plugin.color("&cNejdřív zapni Halloween: /halloween on"));
                    return true;
                }
                if (!plugin.getEventManager().startEventNow(requested)) {
                    sender.sendMessage(plugin.color("&cEvent nelze spustit. Už jeden běží, nebo je ID neplatné. Použij /halloween event start random."));
                    return true;
                }
                sender.sendMessage(plugin.color("&aTestovací okamžité spuštění eventu: &e" + requested));
                return true;
            }
            if (action.equals("stop")) {
                if (!plugin.getEventManager().stopActiveEventNow()) {
                    sender.sendMessage(plugin.color("&7Právě není aktivní žádný event."));
                } else {
                    sender.sendMessage(plugin.color("&eAktivní Halloween event byl ukončen správcem."));
                }
                return true;
            }
            if (!action.equals("status")) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween event [status|start [random|soulstorm|witching-hour|cursed-harvest|blood-moon-invasion|pumpkin-apocalypse|graveyard-rising]|stop]"));
                return true;
            }
        } else if (!checkUse(sender)) {
            return true;
        }

        HalloweenEventManager manager = plugin.getEventManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cSprávce Halloween eventů není dostupný."));
            return true;
        }
        if (!plugin.isEventEnabled()) {
            sender.sendMessage(plugin.color("&cHalloween je vypnutý. Správce může použít /halloween on."));
            return true;
        }
        String active = manager.getActiveEventId();
        if (active == null) {
            long seconds = manager.getNextEventSeconds();
            sender.sendMessage(plugin.color("&6&lHALLOWEEN UDÁLOST"));
            sender.sendMessage(plugin.color("&7Právě neběží žádný event."));
            if (seconds < 0) {
                sender.sendMessage(plugin.color("&7Automatický plán je vypnutý; správce může použít &e/halloween event start random&7."));
            } else {
                sender.sendMessage(plugin.color("&7Další event přibližně za &e" + seconds + " s&7."));
            }
            if (sender.hasPermission("halloweencore.admin")) {
                sender.sendMessage(plugin.color("&7Pro test: &e/halloween event start random"));
            }
            return true;
        }
        String eventName = plugin.getConfig().getString("random-events.types." + active + ".name", active);
        sender.sendMessage(plugin.color("&6&lHALLOWEEN UDÁLOST"));
        sender.sendMessage(plugin.color("&7Typ: &e" + eventName + " &8(" + active + ")"));
        sender.sendMessage(plugin.color("&7Zbývá: &e" + manager.getRemainingSeconds() + " s"));
        return true;
    }

    private boolean rewards(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz může použít jen hráč."));
            return true;
        }
        if (!checkUse(player) || !checkEnabled(player)) return true;
        plugin.getRewardManager().openMenu(player);
        return true;
    }

    private boolean claim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz může použít jen hráč."));
            return true;
        }
        if (!checkUse(player) || !checkEnabled(player)) return true;
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween claim <id>"));
            return true;
        }
        plugin.getRewardManager().claim(player, args[1]);
        return true;
    }

    private boolean challenge(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz může použít jen hráč."));
            return true;
        }
        if (!checkUse(player) || !checkEnabled(player)) return true;
        plugin.getChallengeManager().show(player);
        return true;
    }

    private boolean top(CommandSender sender) {
        if (!checkUse(sender) || !checkEnabled(sender)) return true;

        sender.sendMessage(plugin.color("&6&lHALLOWEEN TOP"));
        int position = 1;
        for (Map.Entry<UUID, Long> entry : plugin.getDataManager().getAllLifetimeFragments().entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .toList()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String name = player.getName() == null ? entry.getKey().toString().substring(0, 8) : player.getName();
            sender.sendMessage(plugin.color("&e#" + position + " &f" + name + " &7- &6" + entry.getValue()));
            position++;
        }

        if (position == 1) sender.sendMessage(plugin.color("&7Leaderboard je zatím prázdný."));
        return true;
    }

    private boolean debug(CommandSender sender) {
        if (!checkAdmin(sender)) return true;

        boolean itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
        boolean mythicMobs = plugin.getServer().getPluginManager().getPlugin("MythicMobs") != null;
        boolean modelEngine = plugin.getServer().getPluginManager().getPlugin("ModelEngine") != null;
        boolean battlePass = plugin.getServer().getPluginManager().getPlugin("BattlePass") != null;
        boolean worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
        boolean papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        boolean itemsAdderVersion = itemsAdder && plugin.getItemManager().isSupportedVersion();
        boolean hunterMask = plugin.getItemManager().isCustomItemAvailable(
                plugin.getConfig().getString("rewards.shop.hunter-mask.itemsadder-id", ""));
        boolean cursedTalisman = plugin.getItemManager().isCustomItemAvailable(
                plugin.getConfig().getString("rewards.shop.cursed-talisman.itemsadder-id", ""));
        boolean halloweenToken = plugin.getItemManager().isCustomItemAvailable(
                plugin.getConfig().getString("rewards.shop.halloween-token.itemsadder-id", ""));
        boolean cursedCandy = plugin.getItemManager().isCustomItemAvailable(
                plugin.getConfig().getString("special-mobs.relic-item-id", ""));
        boolean hauntedMap = plugin.getItemManager().isCustomItemAvailable(
                plugin.getConfig().getString("special-mobs.map-item-id", ""));

        long total = plugin.getService().getServerFragments();
        long goal = plugin.getService().getGlobalGoal();
        double progress = plugin.getService().getGlobalProgressPercent();
        HalloweenBossManager.VampireSpec vampire = plugin.getBossManager().getVampireSpec();

        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        sender.sendMessage(plugin.color("&6&lHALLOWEEN DIAGNOSTIKA"));
        sender.sendMessage(plugin.color("&7Event: " + (plugin.isEventEnabled() ? "&aON" : "&cOFF")));
        sender.sendMessage(plugin.color("&7Random events: " + (plugin.getConfig().getBoolean("random-events.enabled", true) ? "&aON" : "&cOFF")));
        sender.sendMessage(plugin.color("&7Server progress: &e" + total + " &7/ &e" + goal + " &8(" + String.format("%.1f", progress) + "%)"));
        sender.sendMessage(plugin.color("&7Online hráči: &e" + Bukkit.getOnlinePlayers().size()));
        sender.sendMessage(plugin.color("&7ItemsAdder: " + status(itemsAdder) + " &8• verze: " + status(itemsAdderVersion)));
        String musicId = plugin.getConfig().getString("atmosphere.sound", "");
        sender.sendMessage(plugin.color("&7Halloween soundtrack: " + (!musicId.isBlank() && itemsAdder ? "&aNAKONFIGUROVÁN" : "&cNEDOSTUPNÝ")
                + (musicId.isBlank() ? "" : " &8• " + musicId)));
        sender.sendMessage(plugin.color("&7Vanilla hudba: &a31 hudebních událostí potlačeno v packu + watchdog MUSIC"));
        sender.sendMessage(plugin.color("&7Speciální mobové: " + (mythicMobs && modelEngine ? "&aMythicMobs + ModelEngine nalezeny" : "&evanilla fallback; pro vlastní 3D modely je potřeba MythicMobs + ModelEngine")));
        sender.sendMessage(plugin.color("&7Krvavý měsíc: &c" + plugin.getConfig().getDouble("random-events.blood-moon-damage-multiplier", 3.0D)
                + "× poškození od monster &8• vlna " + plugin.getConfig().getInt("random-events.invasion-mobs-per-surge", 4)));
        sender.sendMessage(plugin.color("&7  hunter_mask: " + status(hunterMask)));
        sender.sendMessage(plugin.color("&7  cursed_talisman: " + status(cursedTalisman)));
        sender.sendMessage(plugin.color("&7  halloween_token: " + status(halloweenToken)));
        sender.sendMessage(plugin.color("&7  cursed_candy: " + status(cursedCandy)));
        sender.sendMessage(plugin.color("&7  haunted_map: " + status(hauntedMap)));
        sender.sendMessage(plugin.color("&7MythicMobs: " + status(mythicMobs)));
        sender.sendMessage(plugin.color("&7ModelEngine: " + status(modelEngine)));
        sender.sendMessage(plugin.color("&7BattlePass: " + status(battlePass)));
        sender.sendMessage(plugin.color("&7WorldGuard: " + status(worldGuard)));
        sender.sendMessage(plugin.color("&7PlaceholderAPI: " + status(papi)));
        boolean villageConfigured = plugin.getConfig().getBoolean("haunted-village.enabled", false)
                && !plugin.getConfig().getString("haunted-village.world", "").isBlank();
        sender.sendMessage(plugin.color("&7Haunted Village: " + (villageConfigured ? "&aKONFIGUROVÁNA" : "&eČEKÁ NA SOUŘADNICE")));
        var configErrors = HalloweenConfigValidator.validate(plugin);
        sender.sendMessage(plugin.color("&7Config preflight: " + (configErrors.isEmpty() ? "&aOK" : "&c" + configErrors.size() + " chyba/chyb")));
        if (!configErrors.isEmpty()) {
            for (String error : configErrors) {
                sender.sendMessage(plugin.color("&c  • " + error));
            }
        }
        sender.sendMessage(plugin.color("&7Finále odemčeno: " + (plugin.getDataManager().isFinaleUnlocked() ? "&aANO" : "&cNE")));
        String arenaWorld = plugin.getConfig().getString("bosses.vampire.arena.world", "");
        boolean arenaConfigured = plugin.getConfig().getBoolean("bosses.vampire.arena.configured", false);
        boolean arenaWorldLoaded = !arenaWorld.isBlank() && Bukkit.getWorld(arenaWorld) != null;
        sender.sendMessage(plugin.color("&7Upíří boss: &e" + vampire.id()
                + " &7min. &e" + vampire.minHeightBlocks() + " &7h / &e" + vampire.minWidthWithWingsBlocks()
                + " &7w+křídla " + (plugin.getBossManager().isVampireSpecificationValid() ? "&aOK" : "&cNE")
                + " &8• připravenost: " + (plugin.getBossManager().isVampireReady() ? "&aANO" : "&eČEKÁ")));
        sender.sendMessage(plugin.color("&7Upíří aréna: " + (arenaConfigured ? "&aNASTAVENA" : "&eNENÍ")
                + (arenaConfigured ? " &8• svět: " + (arenaWorldLoaded ? "&aNAČTEN" : "&cNENAČTEN") : "")));
        var modelSection = plugin.getConfig().getConfigurationSection("bosses.vampire.model");
        boolean modelRequired = modelSection == null || modelSection.getBoolean("required", true);
        boolean modelReady = modelSection != null && modelSection.getBoolean("ready", false);
        String modelId = modelSection == null ? "" : modelSection.getString("id", "");
        sender.sendMessage(plugin.color("&7Model: " + (modelRequired ? (modelReady ? "&aPŘIPRAVEN" : "&eČEKÁ") : "&7VOLITELNÝ")
                + (modelId.isBlank() ? "" : " &8• " + modelId)));
        sender.sendMessage(plugin.color("&7Boss bar: " + (plugin.getBossManager().isVampireBossBarActive() ? "&aAKTIVNÍ" : "&eČEKÁ NA BOSSE")));
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        return true;
    }

    private String status(boolean present) {
        return present ? "&aNALEZEN" : "&cNENÍ";
    }

    private boolean setSecret(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz musíš použít přímo v místě tajného objevu."));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween setsecret <id>"));
            sender.sendMessage(plugin.color("&7ID: &eblood-altar, witch-den, forgotten-grave"));
            return true;
        }

        String id = args[1].toLowerCase(java.util.Locale.ROOT);
        String path = "secret-discoveries.locations." + id;
        if (!plugin.getConfig().isConfigurationSection(path)) {
            sender.sendMessage(plugin.color("&cTajemství s ID &e" + id + " &cv konfiguraci neexistuje."));
            sender.sendMessage(plugin.color("&7Přidej nejdřív definici do secret-discoveries.locations."));
            return true;
        }

        plugin.getConfig().set("secret-discoveries.enabled", true);
        plugin.getConfig().set(path + ".configured", true);
        plugin.getConfig().set(path + ".world", player.getWorld().getName());
        plugin.getConfig().set(path + ".x", player.getLocation().getX());
        plugin.getConfig().set(path + ".y", player.getLocation().getY());
        plugin.getConfig().set(path + ".z", player.getLocation().getZ());
        plugin.saveConfig();

        String name = plugin.getConfig().getString(path + ".name", id);
        player.sendMessage(plugin.color("&5&lTAJNÝ OBJEV &8» &aMísto &f" + name + " &abylo nastaveno."));
        player.sendMessage(plugin.color("&7Hráči uvidí pouze nápovědu, nikoliv souřadnice."));
        player.sendMessage(plugin.color("&7Po objevení získá každý hráč odměnu pouze jednou."));
        return true;
    }

    private boolean showSecrets(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cPřehled tajných objevů je určen hráčům ve hře."));
            return true;
        }
        if (!plugin.getConfig().getBoolean("secret-discoveries.enabled", true)) {
            sender.sendMessage(plugin.color("&7Tajné objevy jsou momentálně vypnuté."));
            return true;
        }
        if (plugin.getSecretDiscoveryManager() == null) {
            sender.sendMessage(plugin.color("&cSystém tajných objevů není dostupný."));
            return true;
        }
        plugin.getSecretDiscoveryManager().showSecrets(player);
        return true;
    }

    private boolean setVillage(CommandSender sender) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz musí použít hráč přímo v Haunted Village."));
            return true;
        }

        plugin.getConfig().set("haunted-village.enabled", true);
        plugin.getConfig().set("haunted-village.world", player.getWorld().getName());
        plugin.getConfig().set("haunted-village.x", player.getLocation().getX());
        plugin.getConfig().set("haunted-village.y", player.getLocation().getY());
        plugin.getConfig().set("haunted-village.z", player.getLocation().getZ());
        plugin.saveConfig();

        player.sendMessage(plugin.color("&6HALLOWEEN &8» &aHaunted Village nastavena."));
        player.sendMessage(plugin.color("&7Svět: &e" + player.getWorld().getName()));
        player.sendMessage(plugin.color("&7Pozice: &e" + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())));
        return true;
    }

    private boolean buildVampireArena(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz musíš použít přímo ve světě, ve kterém stavíš arénu."));
            return true;
        }
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("confirm"))) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween buildvampirearena [confirm]"));
            return true;
        }
        if (!plugin.getConfig().getBoolean("bosses.vampire.arena.configured", false)) {
            sender.sendMessage(plugin.color("&cNejdřív stoupni na střed podlahy a použij /halloween setvampirearena."));
            return true;
        }

        String worldName = plugin.getConfig().getString("bosses.vampire.arena.world", "");
        org.bukkit.World world = worldName == null || worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(plugin.color("&cSvět uložené arény není načtený."));
            return true;
        }
        if (!player.getWorld().getUID().equals(world.getUID())) {
            sender.sendMessage(plugin.color("&cPřejdi do světa uložené upíří arény a příkaz spusť z jejího okolí."));
            return true;
        }

        org.bukkit.Location center = new org.bukkit.Location(
                world,
                plugin.getConfig().getDouble("bosses.vampire.arena.x"),
                plugin.getConfig().getDouble("bosses.vampire.arena.y"),
                plugin.getConfig().getDouble("bosses.vampire.arena.z")
        );
        double allowedDistance = (VampireArenaBuilder.RADIUS + 8.0D) * (VampireArenaBuilder.RADIUS + 8.0D);
        if (player.getLocation().distanceSquared(center) > allowedDistance) {
            sender.sendMessage(plugin.color("&cJsi příliš daleko od středu 97blokové arény. Přijď blíž, aby byly načtené potřebné chunky."));
            return true;
        }

        VampireArenaBuilder builder = new VampireArenaBuilder();
        VampireArenaBuilder.Inspection inspection = builder.inspect(center);
        if (!inspection.clear()) {
            sender.sendMessage(plugin.color("&cArénu nelze bezpečně postavit: " + inspection.problem()));
            if (inspection.obstructions() > 0) {
                sender.sendMessage(plugin.color("&7Nalezené překážky v prostoru: &e" + inspection.obstructions()
                        + " &7; první: &e" + inspection.firstObstruction()));
            }
            sender.sendMessage(plugin.color("&7Odstraň stavby/stromy nad budoucí arénou, načti oblast a zkus to znovu."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.color("&4&lNÁHLED STAVBY ARÉNY"));
            sender.sendMessage(plugin.color("&7Svět: &e" + world.getName()));
            sender.sendMessage(plugin.color("&7Střed: &e" + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()));
            sender.sendMessage(plugin.color("&7Průměr arény: &e97 bloků &8• &78 věží, 4 monumentální brány, 8 vnitřních obelisků, obvodová zeď a runový kruh"));
            sender.sendMessage(plugin.color("&7Kontrola prošla: nad podlahou je 20 bloků volného prostoru. Vrchní vrstva terénu v kruhu bude nahrazena podlahou."));
            sender.sendMessage(plugin.color("&ePokud je místo správné, potvrď stavbu: &6/halloween buildvampirearena confirm"));
            sender.sendMessage(plugin.color("&cPotvrzení změní povrch v kruhu o poloměru 48 bloků a přestaví arénu až do 15 bloků výšky. Předem zazálohuj svět!"));
            return true;
        }

        int changed = builder.build(center);
        plugin.getConfig().set("bosses.vampire.arena.generated", true);
        plugin.saveConfig();
        sender.sendMessage(plugin.color("&aGotovo! Upíří aréna byla postavena."));
        sender.sendMessage(plugin.color("&7Změněné bloky: &e" + changed + " &8• &7Průměr arény: &e97 bloků"));
        sender.sendMessage(plugin.color("&7Finální boss zůstává vypnutý, dokud nebude model ověřen přes ModelEngine a resource pack."));
        return true;
    }

    private boolean setVampireArena(CommandSender sender) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz musí použít hráč přímo v aréně Krále upírů."));
            return true;
        }

        plugin.getConfig().set("bosses.vampire.arena.configured", true);
        plugin.getConfig().set("bosses.vampire.arena.generated", false);
        plugin.getConfig().set("bosses.vampire.arena.world", player.getWorld().getName());
        plugin.getConfig().set("bosses.vampire.arena.x", player.getLocation().getBlockX() + 0.5D);
        plugin.getConfig().set("bosses.vampire.arena.y", player.getLocation().getBlockY());
        plugin.getConfig().set("bosses.vampire.arena.z", player.getLocation().getBlockZ() + 0.5D);
        plugin.saveConfig();

        player.sendMessage(plugin.color("&4HALLOWEEN &8» &aStřed arény Krále upírů nastaven."));
        player.sendMessage(plugin.color("&7Svět: &e" + player.getWorld().getName()));
        player.sendMessage(plugin.color("&7Středový bod: &e" + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
                player.getLocation().getBlockX() + 0.5D, (double) player.getLocation().getBlockY(),
                player.getLocation().getBlockZ() + 0.5D)));
        player.sendMessage(plugin.color("&7Tento blok je přesný spawn point. Příkaz použij uprostřed arény na její podlaze."));
        return true;
    }


    private boolean modelPreview(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz musíš použít ve hře."));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween modelpreview <idle|walk|attack|fly|false_sigil|blood_pulse|mirror_strike|nightfall>"));
            return true;
        }
        HalloweenVampireEncounterManager encounter = plugin.getVampireEncounterManager();
        if (encounter == null || !encounter.previewModelAnimation(player, args[1])) {
            sender.sendMessage(plugin.color("&cNáhled nelze spustit. Ověř, že MythicMobs a ModelEngine běží, model je importovaný a finální encounter právě neběží."));
            return true;
        }
        sender.sendMessage(plugin.color("&aDočasný nezranitelný model byl vytvořen před tebou. Automaticky zmizí za 10 sekund."));
        return true;
    }

    private boolean bossEffects(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cVizuální náhled může spustit jen hráč přímo ve hře."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween bosseffects <1|2|3|4>"));
            return true;
        }

        int phase;
        try {
            phase = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.color("&cZadej fázi 1, 2, 3 nebo 4."));
            return true;
        }

        if (plugin.getVampireEncounterManager() == null
                || !plugin.getVampireEncounterManager().previewAbility(player, phase)) {
            sender.sendMessage(plugin.color("&cFáze musí být číslo od 1 do 4."));
            return true;
        }

        sender.sendMessage(plugin.color("&aSpuštěn vizuální náhled fáze " + phase
                + ". &7Jen částice a zvuky — žádný boss ani poškození hráčů."));
        return true;
    }

    private boolean boss(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;

        HalloweenVampireEncounterManager encounter = plugin.getVampireEncounterManager();
        if (encounter == null) {
            sender.sendMessage(plugin.color("&cVampire encounter manager není dostupný."));
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
        switch (action) {
            case "status" -> {
                var spec = plugin.getBossManager().getVampireSpec();
                sender.sendMessage(plugin.color("&4&lKRÁL UPÍRŮ &8» &7status"));
                sender.sendMessage(plugin.color("&7Konfigurace: " + (spec.enabled() ? "&aZAPNUTA" : "&eVYPNUTA")));
                sender.sendMessage(plugin.color("&7Specifikace: " + (plugin.getBossManager().isVampireSpecificationValid() ? "&aOK" : "&cNEPLATNÁ")));
                sender.sendMessage(plugin.color("&7Finále: " + (plugin.getDataManager().isFinaleUnlocked() ? "&aODEMČENO" : "&cNEODEMČENO")));
                sender.sendMessage(plugin.color("&7Král upírů poražen: " + (plugin.getDataManager().isVampireDefeated() ? "&aANO" : "&cNE")));
                sender.sendMessage(plugin.color("&7Aréna: " + (plugin.getBossManager().isVampireReady() ? "&aPŘIPRAVENA" : "&eČEKÁ")));
                sender.sendMessage(plugin.color("&7Encounter: " + (encounter.isActive() ? "&aAKTIVNÍ" : "&eNEBĚŽÍ")));
                if (encounter.isActive()) {
                    sender.sendMessage(plugin.color("&7Fáze: &e" + encounter.getPhase() + " &7• hráči: &e" + encounter.getParticipantCount()
                            + " &7• HP: &e" + String.format(java.util.Locale.ROOT, "%.1f",
                            plugin.getBossManager().getVampireBossHealthPercent() * 100.0D) + "%"));
                }
            }
            case "test" -> {
                if (encounter.isActive()) {
                    sender.sendMessage(plugin.color("&eEncounter už běží."));
                    return true;
                }
                if (encounter.startTestEncounter()) {
                    sender.sendMessage(plugin.color("&aTestovací boss byl vyvolán bez progressu a bez odměn."));
                } else {
                    sender.sendMessage(plugin.color("&cTestovací boss se nepodařilo vyvolat. Potřebuje nastavené místo arény a funkční MythicMobs definici vampire-king."));
                    sender.sendMessage(plugin.color("&7Zkontroluj /halloween debug, /mm mobs a konzoli."));
                }
            }
            case "start" -> {
                if (encounter.isActive()) {
                    sender.sendMessage(plugin.color("&eEncounter už běží."));
                    return true;
                }
                if (!plugin.getBossManager().isVampireReady()) {
                    sender.sendMessage(plugin.color("&cKrál upírů zatím není připraven. Zkontroluj finale/progress, arénu, model a enabled."));
                    return true;
                }
                if (encounter.startEncounter()) {
                    sender.sendMessage(plugin.color("&aEncounter Krále upírů byl spuštěn."));
                } else {
                    sender.sendMessage(plugin.color("&cEncounter se nepodařilo spustit. Zkontroluj konzoli a /halloween debug."));
                }
            }
            case "stop" -> {
                if (!encounter.isActive()) {
                    sender.sendMessage(plugin.color("&7Encounter neběží."));
                    return true;
                }
                encounter.stopEncounter();
                encounter.delayNaturalSummoningAfterStop();
                sender.sendMessage(plugin.color("&aEncounter Krále upírů byl zastaven. Automatické vyvolání je dočasně odloženo."));
            }
            default -> sender.sendMessage(plugin.color("&cPoužití: /halloween boss <status|start|test|stop>"));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!checkAdmin(sender)) return true;

        plugin.reloadEventConfig();
        sender.sendMessage(plugin.message("messages.reloaded"));
        sender.sendMessage(plugin.color(plugin.isEventEnabled()
                ? "&6HALLOWEEN &8» &aKonfigurace načtena. Hudba a dekorace obnoveny; první event je naplánovaný přibližně za "
                    + Math.max(5, plugin.getConfig().getInt("random-events.start-delay-seconds", 30)) + " sekund."
                : "&6HALLOWEEN &8» &eKonfigurace načtena, ale Halloween je vypnutý v config.yml."));

        var itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder != null && itemsAdder.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean started = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
                if (started) {
                    sender.sendMessage(plugin.color("&aItemsAdder: /iazip byl spuštěn pro sestavení aktuálního resource packu."));
                    sender.sendMessage(plugin.color("&7Doručení packu hráčům závisí na funkčním hostingu ItemsAdderu. Pro shader změny se znovu připoj do Minecraftu."));
                } else {
                    sender.sendMessage(plugin.color("&cItemsAdder pack se nepodařilo spustit. Spusť /iazip ručně v konzoli."));
                }
            });
        } else {
            sender.sendMessage(plugin.color("&eItemsAdder není zapnutý; plugin a event konfigurace se načetly, ale resource pack se nesestavil."));
        }
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (args.length < 3) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween give <hráč> <počet>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.color("&cHráč není online."));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.color("&cPočet musí být celé číslo."));
            return true;
        }

        if (amount <= 0L) {
            sender.sendMessage(plugin.color("&cPočet musí být větší než 0."));
            return true;
        }

        long before = plugin.getService().getFragments(target.getUniqueId());
        long newBalance = plugin.getService().addFragments(target.getUniqueId(), amount, "admin");
        long expected = before > Long.MAX_VALUE - amount ? Long.MAX_VALUE : before + amount;
        if (newBalance < expected) {
            sender.sendMessage(plugin.color("&cFragmenty se nepodařilo přidat."));
            return true;
        }
        sender.sendMessage(plugin.color("&aPřidáno &e" + amount + " &afragmentů hráči &f" + target.getName() + "&a."));
        target.sendMessage(plugin.color("&6Získal jsi &e" + amount + " &6Halloween fragmentů. Celkem: &e" + newBalance));
        return true;
    }

    private boolean shader(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (args.length != 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween shader <on|off|reload>"));
            return true;
        }

        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        Path shaderPath = plugin.getDataFolder().getParentFile().toPath()
                .resolve("ItemsAdder/contents/warriorland_halloween/resourcepack/assets/minecraft/shaders/core/sky.fsh");

        if (action.equals("reload")) {
            sender.sendMessage(plugin.color("&6&lHALLOWEEN &8» &7Spouštím nové sestavení resource packu pro shader..."));
            return rebuildItemsAdderPack(sender);
        }
        if (!action.equals("on") && !action.equals("off")) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween shader <on|off|reload>"));
            return true;
        }

        if (!Files.isRegularFile(shaderPath)) {
            sender.sendMessage(plugin.color("&cShader jsem nenašel na serveru: &7plugins/ItemsAdder/contents/warriorland_halloween/resourcepack/assets/minecraft/shaders/core/sky.fsh"));
            sender.sendMessage(plugin.color("&7Nahraj obsah ItemsAdderu a pak spusť /halloween shader reload."));
            return true;
        }

        try {
            String source = Files.readString(shaderPath, StandardCharsets.UTF_8);
            java.util.regex.Pattern tintPattern = java.util.regex.Pattern.compile(
                    "sky\\.rgb\\s*=\\s*mix\\(sky\\.rgb,\\s*sky\\.rgb\\s*\\*\\s*halloweenTint,\\s*[0-9.]+\\s*\\);");
            java.util.regex.Matcher matcher = tintPattern.matcher(source);
            if (!matcher.find()) {
                sender.sendMessage(plugin.color("&cV souboru sky.fsh jsem nenašel očekávaný Halloween tint."));
                sender.sendMessage(plugin.color("&7Shader nebyl změněn. Obnov zdrojový Halloween shader a opakuj příkaz."));
                return true;
            }

            String weight = action.equals("on") ? "0.55" : "0.0";
            String replacement = "sky.rgb = mix(sky.rgb, sky.rgb * halloweenTint, " + weight + ");";
            String updated = matcher.replaceFirst(java.util.regex.Matcher.quoteReplacement(replacement));
            Files.writeString(shaderPath, updated, StandardCharsets.UTF_8);
            sender.sendMessage(plugin.color(action.equals("on")
                    ? "&aHalloween shader je zapnutý."
                    : "&eHalloween barevný nádech shaderu je vypnutý."));
            return rebuildItemsAdderPack(sender);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not update Halloween sky shader: " + ex.getMessage());
            sender.sendMessage(plugin.color("&cShader se nepodařilo změnit: &7" + ex.getMessage()));
            return true;
        }
    }

    private boolean rebuildItemsAdderPack(CommandSender sender) {
        var itemsAdder = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            sender.sendMessage(plugin.color("&cItemsAdder není zapnutý. Změna shaderu byla uložena, ale pack teď nemohu sestavit."));
            sender.sendMessage(plugin.color("&7Po zapnutí ItemsAdderu spusť /iazip nebo /halloween shader reload."));
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean dispatched = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iazip");
            if (dispatched) {
                sender.sendMessage(plugin.color("&aPříkaz /iazip byl předán ItemsAdderu."));
                sender.sendMessage(plugin.color("&7Po dokončení sestavení odpoj a znovu připoj Minecraft, aby se aktualizovaný pack stáhl."));
                sender.sendMessage(plugin.color("&7To, zda se nový pack hráčům opravdu doručí, závisí také na hostingu resource packu v ItemsAdderu."));
            } else {
                sender.sendMessage(plugin.color("&cNepodařilo se zavolat /iazip. Spusť ho ručně v konzoli serveru."));
            }
        });
        return true;
    }

    private boolean toggle(CommandSender sender, String arg) {
        if (!checkAdmin(sender)) return true;
        boolean enabled = arg.equalsIgnoreCase("on");
        plugin.setEventEnabled(enabled);
        sender.sendMessage(plugin.message(enabled ? "messages.event-enabled" : "messages.event-disabled"));
        return true;
    }

    private boolean checkUse(CommandSender sender) {
        if (!sender.hasPermission("halloweencore.use")) {
            sender.sendMessage(plugin.message("messages.no-permission"));
            return false;
        }
        return true;
    }

    private boolean checkEnabled(CommandSender sender) {
        if (!plugin.isEventEnabled()) {
            sender.sendMessage(plugin.message("messages.disabled"));
            return false;
        }
        return true;
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(plugin.message("messages.no-permission"));
            return false;
        }
        return true;
    }
}
