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
                    "stats", "progress", "curse", "event", "challenge", "quests", "structures", "mob", "goblin", "nick",
                    "rewards", "claim", "top", "reload", "debug", "setvillage", "setvampirearena", "buildvampirearena", "setsecret", "secrets", "boss", "bosseffects", "modelpreview", "give", "giveitem", "shader", "on", "off"
            );
            return subcommands.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("quests") || args[0].equalsIgnoreCase("quest"))
                && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "debug", "reset").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("quests") || args[0].equalsIgnoreCase("quest"))
                && (args[1].equalsIgnoreCase("reset") || args[1].equalsIgnoreCase("status")
                || args[1].equalsIgnoreCase("debug")) && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("event") && sender.hasPermission("halloweencore.admin")) {
            return List.of("list", "status", "start", "stop", "soulstorm", "witching-hour",
                    "cursed-harvest", "blood-moon-invasion", "pumpkin-apocalypse", "graveyard-rising", "random").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event")
                && (args[1].equalsIgnoreCase("start") || args[1].equalsIgnoreCase("status"))
                && sender.hasPermission("halloweencore.admin")) {
            return List.of("random", "soulstorm", "witching-hour", "cursed-harvest", "blood-moon-invasion",
                    "pumpkin-apocalypse", "graveyard-rising").stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("structures") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "scan").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
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
        if (args.length == 2 && args[0].equalsIgnoreCase("goblin") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "arena", "dry-run", "confirm", "cancel", "start", "spawn", "test", "stop").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("goblin")
                && (args[1].equalsIgnoreCase("arena") || args[1].equalsIgnoreCase("dry-run"))
                && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getWorlds().stream().map(org.bukkit.World::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .sorted().toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("nick") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "state", "spawn", "test", "target", "attack", "despawn", "cleanup").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("nick")
                && (args[1].equalsIgnoreCase("spawn") || args[1].equalsIgnoreCase("test") || args[1].equalsIgnoreCase("target"))
                && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .sorted().toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("structures") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "scan", "spawn").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("structures")
                && args[1].equalsIgnoreCase("spawn") && sender.hasPermission("halloweencore.admin")) {
            return List.of("haunted-village", "cursed-graveyard").stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(java.util.Locale.ROOT))).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("structures")
                && args[1].equalsIgnoreCase("spawn") && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT)
                            .startsWith(args[3].toLowerCase(java.util.Locale.ROOT))).sorted().toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mob") && sender.hasPermission("halloweencore.admin")) {
            return List.of("spawn", "cleanup", "status").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mob") && args[1].equalsIgnoreCase("spawn")
                && sender.hasPermission("halloweencore.admin")) {
            return List.of("cursed-zombie", "void-reaper", "gravekeeper", "frost-stalker",
                            "blood-spider", "pumpkin-wraith", "hex-witch", "nightmare").stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("mob") && args[1].equalsIgnoreCase("spawn")
                && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT)
                            .startsWith(args[3].toLowerCase(java.util.Locale.ROOT)))
                    .sorted().toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("giveitem") && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .sorted().toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("giveitem") && sender.hasPermission("halloweencore.admin")) {
            return plugin.getRewardManager().getConfiguredRewardIds().stream()
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toList(), list -> {
                                list.add("all");
                                return list;
                            }))
                    .stream().filter(value -> value.toLowerCase(java.util.Locale.ROOT)
                            .startsWith(args[2].toLowerCase(java.util.Locale.ROOT))).toList();
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
        if (args[0].equalsIgnoreCase("quests") || args[0].equalsIgnoreCase("quest")) return quests(sender, args);
        if (args[0].equalsIgnoreCase("reload")) return reload(sender);
        if (args[0].equalsIgnoreCase("debug")) return debug(sender);
        if (args[0].equalsIgnoreCase("shader")) return shader(sender, args);
        if (args[0].equalsIgnoreCase("structures")) return structures(sender, args);
        if (args[0].equalsIgnoreCase("mob")) return mob(sender, args);
        if (args[0].equalsIgnoreCase("goblin")) return goblin(sender, args);
        if (args[0].equalsIgnoreCase("nick")) return nick(sender, args);
        if (args[0].equalsIgnoreCase("setvillage")) return setVillage(sender);
        if (args[0].equalsIgnoreCase("setvampirearena")) return setVampireArena(sender);
        if (args[0].equalsIgnoreCase("buildvampirearena")) return buildVampireArena(sender, args);
        if (args[0].equalsIgnoreCase("setsecret")) return setSecret(sender, args);
        if (args[0].equalsIgnoreCase("secrets")) return showSecrets(sender);
        if (args[0].equalsIgnoreCase("boss")) return boss(sender, args);
        if (args[0].equalsIgnoreCase("bosseffects")) return bossEffects(sender, args);
        if (args[0].equalsIgnoreCase("modelpreview")) return modelPreview(sender, args);
        if (args[0].equalsIgnoreCase("give")) return give(sender, args);
        if (args[0].equalsIgnoreCase("giveitem")) return giveItem(sender, args);
        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")) return toggle(sender, args[0]);

        sender.sendMessage(plugin.color("&6/halloween &7- tvoje Halloween statistiky"));
        sender.sendMessage(plugin.color("&6/halloween progress &7- společný progress serveru"));
        sender.sendMessage(plugin.color("&6/halloween curse &7- tvoje úroveň prokletí"));
        sender.sendMessage(plugin.color("&6/halloween event [list|status [id]|start [id]|stop] &7- event diagnostika (admin)"));
        sender.sendMessage(plugin.color("&6/halloween rewards &7- limitované odměny 2026"));
        sender.sendMessage(plugin.color("&6/halloween challenge &7- dnešní Halloween lov"));
        sender.sendMessage(plugin.color("&6/halloween quests &7- příběhová řada Halloween questů"));
        sender.sendMessage(plugin.color("&6/halloween quests status|debug <hráč> &7- diagnostika příběhu (admin)"));
        sender.sendMessage(plugin.color("&6/halloween quests reset <hráč> confirm &7- potvrzený reset příběhu (admin)"));
        sender.sendMessage(plugin.color("&6/halloween claim <id> &7- vyzvednutí odměny"));
        sender.sendMessage(plugin.color("&6/halloween top &7- leaderboard"));
        sender.sendMessage(plugin.color("&6/halloween reload &7- reload configu"));
        sender.sendMessage(plugin.color("&6/halloween debug &7- diagnostika integrací (admin)"));
        sender.sendMessage(plugin.color("&6/halloween shader <on|off|reload> &7- nápověda ke klientskému Iris shaderu"));
        sender.sendMessage(plugin.color("&6/halloween structures <status|scan|spawn> &7- diagnostika a bezpečný ruční spawn struktur (admin)"));
        sender.sendMessage(plugin.color("&7Ruční spawn: &e/halloween structures spawn <haunted-village|cursed-graveyard> [hráč]"));
        sender.sendMessage(plugin.color("&6/halloween goblin <status|arena|dry-run|confirm|cancel|start|spawn|stop> &7- samostatný Král skřítků (admin)"));
        sender.sendMessage(plugin.color("&6/halloween nick <status|spawn|test|target|attack|despawn> &7- Bezhlavý Nick (admin)"));
        sender.sendMessage(plugin.color("&6/halloween setvillage &7- nastavit Haunted Village na pozici hráče (admin)"));
        sender.sendMessage(plugin.color("&6/halloween setsecret <id> &7- nastavit tajné místo (admin)"));
        sender.sendMessage(plugin.color("&6/halloween secrets &7- nápovědy a postup tajných objevů"));
        sender.sendMessage(plugin.color("&6/halloween setvampirearena &7- nastavit arénu Krále upírů na pozici hráče (admin)"));
        sender.sendMessage(plugin.color("&6/halloween buildvampirearena [confirm] &7- náhled a bezpečná stavba arény (admin)"));
        sender.sendMessage(plugin.color("&6/halloween boss <status|start|test|stop> &7- finále nebo bezpečný test bosse (admin)"));
        sender.sendMessage(plugin.color("&6/halloween bosseffects <1|2|3|4> &7- bezpečný vizuální náhled útoků bosse (admin)"));
        sender.sendMessage(plugin.color("&6/halloween modelpreview <animace> &7- bezpečný 10s náhled modelu a animace (admin)"));
        sender.sendMessage(plugin.color("&6/halloween give <hráč> <počet> &7- přidat fragmenty (admin)"));
        sender.sendMessage(plugin.color("&6/halloween giveitem <hráč> <id|all> &7- dát testovací položku bez ceny (admin)"));
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
            if (action.equals("list")) {
                sender.sendMessage(plugin.color("&6&lHALLOWEEN EVENTY &8» &7Registrované události:"));
                for (String id : plugin.getEventManager().getKnownEventIds()) {
                    String name = plugin.getConfig().getString("random-events.types." + id + ".name", id);
                    String state = !plugin.getEventManager().isEventIndividuallyEnabled(id) ? "&cDISABLED"
                            : plugin.getEventManager().isActive(id) ? "&aACTIVE" : "&7WAITING";
                    sender.sendMessage(plugin.color(" &8• &f" + id + " &8— &e" + name + " &8[ " + state + " &8]"));
                }
                sender.sendMessage(plugin.color("&7Poslední skutečný výsledek: &f" + plugin.getEventManager().getLastEffectSummary()));
                return true;
            }
            if (action.equals("start")) {
                String requested = args.length >= 3 ? args[2] : "random";
                if (!plugin.isEventEnabled()) {
                    sender.sendMessage(plugin.color("&cNejdřív zapni Halloween: /halloween on"));
                    return true;
                }
                if (!plugin.getEventManager().startEventNow(requested)) {
                    sender.sendMessage(plugin.color("&cEvent nelze spustit: &e" + plugin.getEventManager().getLastStartFailure()));
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
                sender.sendMessage(plugin.color("&cPoužití: /halloween event [list|status [event-id]|start [random|event-id]|stop]"));
                return true;
            }
            if (args.length >= 3) {
                String requested = args[2].toLowerCase(java.util.Locale.ROOT);
                if (!plugin.getEventManager().getKnownEventIds().contains(requested)) {
                    sender.sendMessage(plugin.color("&cNeznámé ID eventu: &e" + requested + "&c. Použij /halloween event list."));
                    return true;
                }
                String name = plugin.getConfig().getString("random-events.types." + requested + ".name", requested);
                sender.sendMessage(plugin.color("&6&lEVENT STATUS &8» &e" + name + " &8(" + requested + ")"));
                String state = !plugin.getEventManager().isEventIndividuallyEnabled(requested) ? "&cDISABLED"
                        : plugin.getEventManager().isActive(requested) ? "&aACTIVE" : "&7WAITING";
                sender.sendMessage(plugin.color("&7Stav: " + state));
                if (requested.equals(plugin.getEventManager().getLastEffectEventId())) {
                    sender.sendMessage(plugin.color("&7Poslední vlna: &f" + plugin.getEventManager().getLastEffectSummary()));
                } else {
                    sender.sendMessage(plugin.color("&7Pro tento event zatím není zaznamenána poslední vlna."));
                }
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
            sender.sendMessage(plugin.color("&7Poslední výsledek vlny: &f" + manager.getLastEffectSummary()));
            if (sender.hasPermission("halloweencore.admin")) {
                sender.sendMessage(plugin.color("&7Pro test: &e/halloween event list &8| &e/halloween event start blood-moon-invasion"));
            }
            return true;
        }
        String eventName = plugin.getConfig().getString("random-events.types." + active + ".name", active);
        sender.sendMessage(plugin.color("&6&lHALLOWEEN UDÁLOST"));
        sender.sendMessage(plugin.color("&7Typ: &e" + eventName + " &8(" + active + ")"));
        sender.sendMessage(plugin.color("&7Zbývá: &e" + manager.getRemainingSeconds() + " s"));
        sender.sendMessage(plugin.color("&7Poslední skutečný výsledek: &f" + manager.getLastEffectSummary()));
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

    private boolean quests(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            String action = args[1].toLowerCase(java.util.Locale.ROOT);
            if (action.equals("reset")) {
                if (!checkAdmin(sender)) return true;
                if (args.length != 4 || !args[3].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(plugin.color("&ePozor: reset smaže aktivní i dokončené kapitoly tohoto hráče."));
                    sender.sendMessage(plugin.color("&7Fragmenty a ostatní statistiky zůstanou zachované."));
                    sender.sendMessage(plugin.color("&cPotvrď příkazem: &f/halloween quests reset <online hráč> confirm"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(plugin.color("&cHráč není online. Vyber online hráče."));
                    return true;
                }
                plugin.getDataManager().resetStoryQuests(target.getUniqueId());
                plugin.getQuestManager().show(target);
                plugin.getDataManager().save();
                sender.sendMessage(plugin.color("&aPříběhové questy hráče &f" + target.getName()
                        + " &abyly resetovány na první kapitolu."));
                if (sender != target) {
                    target.sendMessage(plugin.color("&6HALLOWEEN &8» &eTvůj příběhový postup byl restartován správcem. Začínáš znovu od první kapitoly."));
                }
                return true;
            }
            if (action.equals("status") || action.equals("debug")) {
                if (!checkAdmin(sender)) return true;
                if (args.length != 3) {
                    sender.sendMessage(plugin.color("&cPoužití: /halloween quests " + action + " <online hráč>"));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(plugin.color("&cHráč není online. Vyber online hráče."));
                    return true;
                }
                sender.sendMessage(plugin.color("&6&lHALLOWEEN QUEST DEBUG"));
                sender.sendMessage(plugin.color("&7" + plugin.getQuestManager().getDebugSummary(target)));
                return true;
            }
            sender.sendMessage(plugin.color("&cPoužití: /halloween quests [status|debug <online hráč>|reset <online hráč> confirm]"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz může použít jen hráč."));
            return true;
        }
        if (!checkUse(player) || !checkEnabled(player)) return true;
        plugin.getQuestManager().show(player);
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
        boolean crimsonShield = plugin.getItemManager().isCustomItemAvailable(
                "warriorland_halloween:crimson_warden_shield");

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
        sender.sendMessage(plugin.color("&7Vanilla hudba: &a31 override definic + watchdog MUSIC &8• klientský pack musí být přijat"));
        sender.sendMessage(plugin.color("&7Speciální mobové: " + (mythicMobs && modelEngine ? "&aMythicMobs + ModelEngine nalezeny" : "&evanilla fallback; pro vlastní 3D modely je potřeba MythicMobs + ModelEngine")));
        sender.sendMessage(plugin.color("&7Krvavý měsíc: &c" + plugin.getConfig().getDouble("random-events.blood-moon-damage-multiplier", 3.0D)
                + "× poškození od monster &8• vlna " + plugin.getConfig().getInt("random-events.invasion-mobs-per-surge", 4)));
        sender.sendMessage(plugin.color("&7  hunter_mask: " + status(hunterMask)));
        sender.sendMessage(plugin.color("&7  cursed_talisman: " + status(cursedTalisman)));
        sender.sendMessage(plugin.color("&7  halloween_token: " + status(halloweenToken)));
        sender.sendMessage(plugin.color("&7  cursed_candy: " + status(cursedCandy)));
        sender.sendMessage(plugin.color("&7  haunted_map: " + status(hauntedMap)));
        sender.sendMessage(plugin.color("&7  crimson_warden_shield: " + status(crimsonShield)));
        sender.sendMessage(plugin.color("&7MythicMobs: " + status(mythicMobs)));
        sender.sendMessage(plugin.color("&7ModelEngine: " + status(modelEngine)));
        sender.sendMessage(plugin.color("&7BattlePass: " + status(battlePass)));
        sender.sendMessage(plugin.color("&7WorldGuard: " + status(worldGuard)));
        sender.sendMessage(plugin.color("&7PlaceholderAPI: " + status(papi)));
        boolean villageConfigured = plugin.getConfig().getBoolean("haunted-village.enabled", false)
                && !plugin.getConfig().getString("haunted-village.world", "").isBlank();
        sender.sendMessage(plugin.color("&7Haunted Village marker: " + (villageConfigured ? "&aKONFIGUROVÁNA" : "&eČEKÁ NA SOUŘADNICE")));
        var structures = plugin.getWorldStructureGenerator();
        sender.sendMessage(plugin.color("&7Náhodné struktury: " + (structures != null && structures.isEnabled() ? "&aAKTIVNÍ" : "&cVYPNUTÉ")
                + " &8• fronta " + (structures == null ? 0 : structures.getPendingCount())
                + " • vesnice " + (structures == null ? 0 : structures.getVillagesBuilt())
                + " • hřbitovy " + (structures == null ? 0 : structures.getGraveyardsBuilt())));
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

    private boolean goblin(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        HalloweenGoblinKingManager manager = plugin.getGoblinKingManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cSprávce Krále skřítků není dostupný."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&6/halloween goblin <status|arena|dry-run|confirm|cancel|start|spawn|stop>"));
            return true;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status")) {
            sender.sendMessage(plugin.color("&6&lKRÁL SKŘÍTKŮ"));
            sender.sendMessage(plugin.color("&7" + manager.statusText()));
            sender.sendMessage(plugin.color("&7Server progress: &e" + String.format("%.1f", plugin.getService().getGlobalProgressPercent())
                    + "% &8/ odemčení: &e" + plugin.getConfig().getDouble("bosses.goblin.unlock-global-progress-percent", 50.0D) + "%"));
            return true;
        }
        if (action.equals("confirm")) {
            String result = manager.confirmArena(sender);
            sender.sendMessage(plugin.color("&6[Goblin Arena] &7" + result));
            return true;
        }
        if (action.equals("cancel")) {
            manager.cancelArenaPlan();
            sender.sendMessage(plugin.color("&ePlán arény byl zrušen; nebyly provedeny žádné změny."));
            return true;
        }
        if (action.equals("arena") || action.equals("dry-run")) {
            boolean dryRun = action.equals("dry-run");
            if (args.length != 6) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween goblin " + action + " <world> <x> <y> <z>"));
                sender.sendMessage(plugin.color("&7Souřadnice musí být explicitní; aktuální pozice se nepoužívá automaticky."));
                return true;
            }
            org.bukkit.World world = Bukkit.getWorld(args[2]);
            if (world == null) {
                sender.sendMessage(plugin.color("&cNeznámý svět: &e" + args[2]));
                return true;
            }
            int x;
            int y;
            int z;
            try {
                x = Integer.parseInt(args[3]);
                y = Integer.parseInt(args[4]);
                z = Integer.parseInt(args[5]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.color("&cSouřadnice x/y/z musí být celá čísla."));
                return true;
            }
            org.bukkit.Location center = new org.bukkit.Location(world, x + 0.5D, y, z + 0.5D);
            HalloweenGoblinKingManager.ArenaInspection inspection = dryRun
                    ? manager.inspectArena(center, sender) : manager.previewArena(center, sender);
            sender.sendMessage(plugin.color("&6&lPEVNOST KRÁLE SKŘÍTKŮ — " + (dryRun ? "DRY-RUN" : "NÁHLED")));
            sender.sendMessage(plugin.color("&7Hranice: &e" + inspection.boundsText()));
            sender.sendMessage(plugin.color("&7Výška: &e" + inspection.minY() + " až " + inspection.maxY()));
            if (!inspection.clear()) {
                sender.sendMessage(plugin.color("&cStavba není bezpečná: " + inspection.issue()));
                if (inspection.firstObstruction() != null) {
                    sender.sendMessage(plugin.color("&7První překážka: &e" + inspection.firstObstruction()));
                }
                sender.sendMessage(plugin.color("&7Existující bloky se nepřepisují. Zvol prázdné místo."));
            } else if (dryRun) {
                sender.sendMessage(plugin.color("&aDRY-RUN OK: oblast je prázdná, chunky načtené a hranice zkontrolované. Nic nebylo postaveno."));
            } else {
                sender.sendMessage(plugin.color("&aPředkontrola prošla. Výstavba zatím neproběhla."));
                sender.sendMessage(plugin.color("&eZkontroluj hranice a zadej /halloween goblin confirm."));
                sender.sendMessage(plugin.color("&7Doporučený předběžný backup světa: " + world.getName()));
            }
            return true;
        }
        if (action.equals("spawn") || action.equals("test")) {
            if (!manager.spawnEncounter(true)) {
                sender.sendMessage(plugin.color("&cTest spawnu selhal. Ověř načtení ModelEngine + MythicMobs, model halloween_goblin_king, povolení bosse a dokončenou arénu."));
            } else {
                sender.sendMessage(plugin.color("&aTestovací Král skřítků je aktivní; testovací souboj nedává boss odměny."));
            }
            return true;
        }
        if (action.equals("start")) {
            double required = plugin.getConfig().getDouble("bosses.goblin.unlock-global-progress-percent", 50.0D);
            double progress = plugin.getService().getGlobalProgressPercent();
            if (progress < required) {
                sender.sendMessage(plugin.color("&cKrál skřítků se ještě neprobudí. Server progress: &e"
                        + String.format("%.1f", progress) + "% &7/ potřebuje &e" + required + "%."));
                return true;
            }
            if (!manager.spawnEncounter(false)) {
                sender.sendMessage(plugin.color("&cBoss nešel spustit. Zkontroluj /halloween goblin status, nastavení a dokončenou arénu."));
            } else {
                sender.sendMessage(plugin.color("&aKrál skřítků začal normální souboj. Odměny budou připsány aktivním účastníkům."));
            }
            return true;
        }
        if (action.equals("stop")) {
            manager.stopEncounter();
            sender.sendMessage(plugin.color("&eSouboj Krále skřítků byl bezpečně zastaven."));
            return true;
        }
        sender.sendMessage(plugin.color("&cPoužití: /halloween goblin <status|arena|dry-run|confirm|cancel|start|spawn|stop>"));
        return true;
    }

    private boolean nick(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        HalloweenHeadlessNickManager manager = plugin.getHeadlessNickManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cSprávce Bezhlavého Nicka není dostupný."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&6/halloween nick <status|spawn|test|target|attack|despawn>"));
            return true;
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status") || action.equals("state")) {
            sender.sendMessage(plugin.color("&6&lBEZHLAVÝ NICK"));
            sender.sendMessage(plugin.color("&7" + manager.statusText()));
            return true;
        }
        if (action.equals("despawn") || action.equals("cleanup")) {
            manager.stopEncounter();
            sender.sendMessage(plugin.color("&eNick i jeho kůň byli uklizeni."));
            return true;
        }
        if (action.equals("attack")) {
            if (!manager.forceAttack()) {
                sender.sendMessage(plugin.color("&cÚtok se nepodařilo spustit. Nick potřebuje aktivní cíl, přímou viditelnost a platné entity."));
            } else {
                sender.sendMessage(plugin.color("&aTestovací Spectral Charge byl spuštěn."));
            }
            return true;
        }
        if (action.equals("spawn") || action.equals("test")) {
            Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                    : (sender instanceof Player player ? player : null);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween nick " + action + " <online-hráč>"));
                return true;
            }
            boolean testOnly = action.equals("test");
            if (!manager.spawnFor(target, testOnly)) {
                sender.sendMessage(plugin.color("&cNick se nepodařilo spawnout. Zkontroluj čas noci, bezpečnou zónu, cílový svět a volné místo."));
            } else {
                sender.sendMessage(plugin.color("&aBezhlavý Nick začal stalking u hráče &e" + target.getName() + "&a."));
                sender.sendMessage(plugin.color("&7Soukromé zprávy dostává pouze vybraný cíl."));
            }
            return true;
        }
        if (action.equals("target")) {
            if (args.length != 3) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween nick target <online-hráč>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null || !manager.assignTarget(target)) {
                sender.sendMessage(plugin.color("&cNový cíl není platný, je v bezpečné zóně, nebo Nick není aktivní."));
            } else {
                sender.sendMessage(plugin.color("&aNick sleduje nově přiřazený cíl. Soukromou zprávu dostal pouze tento hráč."));
            }
            return true;
        }
        sender.sendMessage(plugin.color("&cPoužití: /halloween nick <status|spawn|test|target|attack|despawn>"));
        return true;
    }

    private boolean mob(CommandSender sender, String[] args) {
        if (!sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(plugin.color("&cTento příkaz je pouze pro administrátory."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&6/halloween mob <spawn|cleanup|status> &7— testování speciálních mobů"));
            sender.sendMessage(plugin.color("&7Spawn: &e/halloween mob spawn <id> [hráč]"));
            return true;
        }

        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status")) {
            if (args.length != 2) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween mob status"));
                return true;
            }
            int count = plugin.getMobManager().countManagedEventMobs();
            sender.sendMessage(plugin.color("&6Halloween mobové"));
            sender.sendMessage(plugin.color("&7Event: " + (plugin.isEventEnabled() ? "&aaktivní" : "&cvypnutý")
                    + " &8• označení event/test mobové v načtených chunkech: &e" + count));
            sender.sendMessage(plugin.color("&7Custom model: "
                    + (plugin.getConfig().getBoolean("special-mobs.custom-models.enabled", true) ? "&apovoleny" : "&cvypnuty")
                    + " &8• bez MythicMobs/ModelEngine se použije vanilla fallback."));
            return true;
        }
        if (action.equals("cleanup")) {
            if (args.length != 2) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween mob cleanup"));
                return true;
            }
            int removed = plugin.getMobManager().cleanupEventMobs();
            sender.sendMessage(plugin.color("&aOdstraněno označených event/test mobů: &e" + removed));
            return true;
        }
        if (action.equals("spawn")) {
            if (args.length < 3 || args.length > 4) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween mob spawn <cursed-zombie|void-reaper|gravekeeper|frost-stalker|blood-spider|pumpkin-wraith|hex-witch|nightmare> [hráč]"));
                return true;
            }
            String mobId = args[2].toLowerCase(java.util.Locale.ROOT);
            List<String> ids = List.of("cursed-zombie", "void-reaper", "gravekeeper", "frost-stalker",
                    "blood-spider", "pumpkin-wraith", "hex-witch", "nightmare");
            if (!ids.contains(mobId)) {
                sender.sendMessage(plugin.color("&cNeznámý mob. Platné ID: &7" + String.join(", ", ids)));
                return true;
            }

            Player target;
            if (args.length == 4) {
                target = Bukkit.getPlayerExact(args[3]);
                if (target == null) {
                    sender.sendMessage(plugin.color("&cHráč není online: &e" + args[3]));
                    return true;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(plugin.color("&cZ konzole zadej i cílového hráče: /halloween mob spawn <id> <hráč>"));
                return true;
            }

            if (!plugin.getMobManager().spawnTestMob(target, mobId)) {
                sender.sendMessage(plugin.color("&cSpawn selhal. Ověř, že je HalloweenCore zapnutý, svět povolený a poblíž hráče je bezpečné místo."));
                return true;
            }
            sender.sendMessage(plugin.color("&aTestovací mob &e" + mobId + " &abyl vyvolán poblíž hráče &e" + target.getName() + "&a."));
            sender.sendMessage(plugin.color("&7Testovací mob nedává eventové odměny. Úklid: &e/halloween mob cleanup"));
            return true;
        }

        sender.sendMessage(plugin.color("&cPoužití: /halloween mob <spawn|cleanup|status>"));
        return true;
    }

    private boolean structures(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        HalloweenWorldStructureGenerator generator = plugin.getWorldStructureGenerator();
        if (generator == null) {
            sender.sendMessage(plugin.color("&cGenerátor Halloween struktur není dostupný."));
            return true;
        }

        String action = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "status";
        if (action.equals("spawn")) {
            if (args.length < 3 || args.length > 4) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween structures spawn <haunted-village|cursed-graveyard> [hráč]"));
                return true;
            }
            Player target;
            if (args.length == 4) {
                target = Bukkit.getPlayerExact(args[3]);
                if (target == null) {
                    sender.sendMessage(plugin.color("&cHráč není online: &e" + args[3]));
                    return true;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(plugin.color("&cZ konzole musíš zadat i hráče: /halloween structures spawn <typ> <hráč>"));
                return true;
            }
            String result = generator.queueManualStructure(target.getLocation(), args[2]);
            sender.sendMessage(plugin.color("&6[Halloween Structures] &7" + result));
            return true;
        }
        if (args.length > 2 || (!action.equals("status") && !action.equals("scan"))) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween structures [status|scan]"));
            return true;
        }
        if (action.equals("scan")) {
            generator.scanLoadedChunks();
            sender.sendMessage(plugin.color("&6HALLOWEEN &8» &aKontrola načtených chunků zařazena."));
            sender.sendMessage(plugin.color("&7Generátor nenačítá vzdálené chunky; ověřuje jen již načtené části mapy."));
        }

        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        sender.sendMessage(plugin.color("&6&lHALLOWEEN – STRUKTURY SVĚTA"));
        sender.sendMessage(plugin.color("&7Stav: " + (generator.isEnabled() ? "&aAKTIVNÍ" : "&cVYPNUTÝ")));
        sender.sendMessage(plugin.color("&7Čekající kandidáti: &e" + generator.getPendingCount()));
        sender.sendMessage(plugin.color("&7Postavené vesnice v této relaci: &e" + generator.getVillagesBuilt()));
        sender.sendMessage(plugin.color("&7Postavené hřbitovy v této relaci: &e" + generator.getGraveyardsBuilt()));
        sender.sendMessage(plugin.color("&7Přeskočené kandidáty: &e" + generator.getCandidatesSkipped()));
        sender.sendMessage(plugin.color("&7Poslední důvod přeskočení: &e"
                + (generator.getLastSkipReason().isBlank() ? "žádný" : generator.getLastSkipReason())));
        sender.sendMessage(plugin.color("&7Hustota: vesnice &e1/"
                + Math.max(4, plugin.getConfig().getInt("world-structures.village-region-chunks", 10))
                + "² &7chunků, hřbitovy &e1/"
                + Math.max(3, plugin.getConfig().getInt("world-structures.graveyard-region-chunks", 6))
                + "² &7chunků (před kontrolou terénu)."));
        sender.sendMessage(plugin.color("&7Ochrana spawnu: &e"
                + Math.max(0, Math.min(1024, plugin.getConfig().getInt("world-structures.spawn-exclusion-radius", 64)))
                + " bloků od světového spawnu."));
        sender.sendMessage(plugin.color("&7Příkaz scan znovu zkontroluje pouze právě načtené chunky."));
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
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
        VampireArenaBuilder.Inspection inspection = builder.inspect(center, player);
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
            sender.sendMessage(plugin.color("&7Předkontrola prošla: přírodní terén lze bezpečně seříznout do 8 bloků a zasypat do 12 bloků."));
            sender.sendMessage(plugin.color("&7Stavby, bloky s inventářem a oblasti zamítnuté WorldGuardem se nepřepisují."));
            sender.sendMessage(plugin.color("&ePokud je místo správné, potvrď stavbu: &6/halloween buildvampirearena confirm"));
            sender.sendMessage(plugin.color("&cPotvrzení změní povrch v kruhu o poloměru 48 bloků a přestaví arénu až do 15 bloků výšky. Předem zazálohuj svět!"));
            return true;
        }

        int changed;
        try {
            changed = builder.build(center, player);
        } catch (IllegalStateException exception) {
            sender.sendMessage(plugin.color("&cStavba byla bezpečně zastavena: &e" + exception.getMessage()));
            return true;
        }
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


    private boolean giveItem(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (args.length != 3) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween giveitem <hráč> <id|all>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.color("&cHráč není online."));
            return true;
        }
        String id = args[2].toLowerCase(java.util.Locale.ROOT);
        if (id.equals("all")) {
            int granted = 0;
            List<String> failed = new ArrayList<>();
            for (String rewardId : plugin.getRewardManager().getConfiguredRewardIds()) {
                if (plugin.getRewardManager().giveAdminItem(target, rewardId)) granted++;
                else failed.add(rewardId);
            }
            sender.sendMessage(plugin.color("&aTestovací předměty: předáno &e" + granted
                    + "&a položek hráči &f" + target.getName() + "&a."));
            if (!failed.isEmpty()) sender.sendMessage(plugin.color("&cNepodařilo se dát: &7" + String.join(", ", failed)));
            target.sendMessage(plugin.color("&6HALLOWEEN &8» &eAdministrátor ti dal " + granted + " testovacích předmětů."));
            return true;
        }
        if (!plugin.getRewardManager().giveAdminItem(target, id)) {
            sender.sendMessage(plugin.color("&cNeznámý reward ID nebo neplatná definice: &e" + id
                    + "&c. Zkontroluj /halloween rewards."));
            return true;
        }
        sender.sendMessage(plugin.color("&aTestovací předmět &e" + id + " &apředán hráči &f" + target.getName() + "&a."));
        target.sendMessage(plugin.color("&6HALLOWEEN &8» &eAdministrátor ti dal testovací předmět: &f" + id + "&e."));
        return true;
    }

    private boolean shader(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (args.length != 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween shader <on|off|reload>"));
            return true;
        }

        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("reload")) {
            sender.sendMessage(plugin.color("&6&lHALLOWEEN &8» &7Iris shader běží pouze na klientovi; server ho nemůže vzdáleně přenačíst."));
            sender.sendMessage(plugin.color("&7Pokud se změnily textury nebo modely ItemsAdderu, sestav jejich pack samostatně příkazem &f/iazip&7."));
            sender.sendMessage(plugin.color("&7Na klientovi nahraď ZIP ve složce .minecraft/shaderpacks a shader znovu vyber v nastavení Iris."));
            return true;
        }
        if (action.equals("on") || action.equals("off")) {
            sender.sendMessage(plugin.color("&6&lHALLOWEEN &8» &7Server nemůže hráči zapnout ani vypnout Iris shader."));
            sender.sendMessage(plugin.color("&7Akce &f" + action + "&7 platí jen pro nastavení na tvém klientovi: Video Settings &8» &fShader Packs&7."));
            sender.sendMessage(plugin.color("&7Event Krvavého měsíce může shader synchronizovat pouze s nainstalovaným klientským modem HalloweenCore."));
            return true;
        }
        sender.sendMessage(plugin.color("&cNeznámá akce. Použij on, off nebo reload."));
        return true;
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
