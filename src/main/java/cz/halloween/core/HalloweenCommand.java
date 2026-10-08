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
                    "rewards", "claim", "top", "reload", "debug", "setvillage", "setvampirearena", "boss", "give", "on", "off"
            );
            return subcommands.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
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
        if (args.length == 2 && args[0].equalsIgnoreCase("boss") && sender.hasPermission("halloweencore.admin")) {
            return List.of("status", "start", "stop").stream()
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
        if (args[0].equalsIgnoreCase("event")) return event(sender);
        if (args[0].equalsIgnoreCase("rewards")) return rewards(sender);
        if (args[0].equalsIgnoreCase("claim")) return claim(sender, args);
        if (args[0].equalsIgnoreCase("challenge")) return challenge(sender);
        if (args[0].equalsIgnoreCase("reload")) return reload(sender);
        if (args[0].equalsIgnoreCase("debug")) return debug(sender);
        if (args[0].equalsIgnoreCase("setvillage")) return setVillage(sender);
        if (args[0].equalsIgnoreCase("setvampirearena")) return setVampireArena(sender);
        if (args[0].equalsIgnoreCase("boss")) return boss(sender, args);
        if (args[0].equalsIgnoreCase("give")) return give(sender, args);
        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")) return toggle(sender, args[0]);

        sender.sendMessage(plugin.color("&6/halloween &7- tvoje Halloween statistiky"));
        sender.sendMessage(plugin.color("&6/halloween progress &7- společný progress serveru"));
        sender.sendMessage(plugin.color("&6/halloween curse &7- tvoje úroveň prokletí"));
        sender.sendMessage(plugin.color("&6/halloween event &7- aktuální Halloween událost"));
        sender.sendMessage(plugin.color("&6/halloween rewards &7- limitované odměny 2026"));
        sender.sendMessage(plugin.color("&6/halloween challenge &7- dnešní Halloween lov"));
        sender.sendMessage(plugin.color("&6/halloween claim <id> &7- vyzvednutí odměny"));
        sender.sendMessage(plugin.color("&6/halloween top &7- leaderboard"));
        sender.sendMessage(plugin.color("&6/halloween reload &7- reload configu"));
        sender.sendMessage(plugin.color("&6/halloween debug &7- diagnostika integrací (admin)"));
        sender.sendMessage(plugin.color("&6/halloween setvillage &7- nastavit Haunted Village na pozici hráče (admin)"));
        sender.sendMessage(plugin.color("&6/halloween setvampirearena &7- nastavit arénu Krále upírů na pozici hráče (admin)"));
        sender.sendMessage(plugin.color("&6/halloween boss <status|start|stop> &7- finální encounter (admin)"));
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

    private boolean event(CommandSender sender) {
        if (!checkUse(sender) || !checkEnabled(sender)) return true;
        if (plugin.getEventManager() == null || plugin.getEventManager().getActiveEventId() == null) {
            sender.sendMessage(plugin.color("&7Právě neprobíhá žádná náhodná událost."));
            long seconds = plugin.getEventManager() == null ? 0L : plugin.getEventManager().getNextEventSeconds();
            sender.sendMessage(plugin.color("&7Další událost přibližně za &e" + seconds + " s&7."));
            return true;
        }
        sender.sendMessage(plugin.color("&6&lHALLOWEEN UDÁLOST"));
        String active = plugin.getEventManager().getActiveEventId();
        String eventName = plugin.getConfig().getString("random-events.types." + active + ".name", active);
        sender.sendMessage(plugin.color("&7Typ: &e" + eventName));
        sender.sendMessage(plugin.color("&7Zbývá: &e" + plugin.getEventManager().getRemainingSeconds() + " s"));
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
        sender.sendMessage(plugin.color("&7  hunter_mask: " + status(hunterMask)));
        sender.sendMessage(plugin.color("&7  cursed_talisman: " + status(cursedTalisman)));
        sender.sendMessage(plugin.color("&7  halloween_token: " + status(halloweenToken)));
        sender.sendMessage(plugin.color("&7  cursed_candy: " + status(cursedCandy)));
        sender.sendMessage(plugin.color("&7  haunted_map: " + status(hauntedMap)));
        sender.sendMessage(plugin.color("&7MythicMobs: " + status(mythicMobs)));
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

    private boolean setVampireArena(CommandSender sender) {
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
                sender.sendMessage(plugin.color("&7Aréna: " + (plugin.getBossManager().isVampireReady() ? "&aPŘIPRAVENA" : "&eČEKÁ")));
                sender.sendMessage(plugin.color("&7Encounter: " + (encounter.isActive() ? "&aAKTIVNÍ" : "&eNEBĚŽÍ")));
                if (encounter.isActive()) {
                    sender.sendMessage(plugin.color("&7Fáze: &e" + encounter.getPhase() + " &7• hráči: &e" + encounter.getParticipantCount()
                            + " &7• HP: &e" + String.format(java.util.Locale.ROOT, "%.1f", plugin.getBossManager().getVampireBossHealthPercent() * 100.0D) + "%"));
                }
            }
            case "start" -> {
                if (encounter.isActive()) {
                    sender.sendMessage(plugin.color("&eEncounter už běží."));
                    return true;
                }
                if (!plugin.getBossManager().isVampireReady()) {
                    sender.sendMessage(plugin.color("&cKrál upírů zatím není připraven. Zkontroluj finale/progress, arénu a enabled."));
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
                sender.sendMessage(plugin.color("&aEncounter Krále upírů byl zastaven."));
            }
            default -> sender.sendMessage(plugin.color("&cPoužití: /halloween boss <status|start|stop>"));
        }
        return true;
    }

        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz musí použít hráč přímo v aréně Krále upírů."));
            return true;
        }

        plugin.getConfig().set("bosses.vampire.arena.configured", true);
        plugin.getConfig().set("bosses.vampire.arena.world", player.getWorld().getName());
        plugin.getConfig().set("bosses.vampire.arena.x", player.getLocation().getX());
        plugin.getConfig().set("bosses.vampire.arena.y", player.getLocation().getY());
        plugin.getConfig().set("bosses.vampire.arena.z", player.getLocation().getZ());
        plugin.saveConfig();

        player.sendMessage(plugin.color("&4HALLOWEEN &8» &aAréna Krále upírů nastavena."));
        player.sendMessage(plugin.color("&7Svět: &e" + player.getWorld().getName()));
        player.sendMessage(plugin.color("&7Pozice: &e" + String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ())));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!checkAdmin(sender)) return true;
        plugin.reloadEventConfig();
        sender.sendMessage(plugin.message("messages.reloaded"));
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
