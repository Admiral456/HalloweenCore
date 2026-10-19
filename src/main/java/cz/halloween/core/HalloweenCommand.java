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
            List<String> subcommands = new ArrayList<>(List.of(
                    "stats", "progress", "curse", "challenge", "rewards", "claim", "top", "event", "ambience", "secrets"
            ));
            if (sender.hasPermission("halloweencore.admin")) {
                subcommands.addAll(List.of("reload", "debug", "give", "on", "off", "npc", "cache", "setsecret"));
            }
            return subcommands.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setsecret")
                && sender.hasPermission("halloweencore.admin")) {
            var secrets = plugin.getConfig().getConfigurationSection("secret-discoveries.locations");
            if (secrets == null) return List.of();
            return secrets.getKeys(false).stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ambience")) {
            List<String> options = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("halloweencore.admin")) options.addAll(List.of("on", "off", "test"));
            return options.stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("event")) {
            List<String> options = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("halloweencore.admin")) {
                options.addAll(List.of("start", "stop"));
            }
            return options.stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("event")
                && args[1].equalsIgnoreCase("start")
                && sender.hasPermission("halloweencore.admin")
                && plugin.getVanillaEventManager() != null) {
            return plugin.getVanillaEventManager().getKnownEventIds().stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")
                && sender.hasPermission("halloweencore.admin")) {
            return List.of("spawn", "remove").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
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
        if (args.length == 2 && args[0].equalsIgnoreCase("cache")
                && sender.hasPermission("halloweencore.admin")) {
            return List.of("spawn", "key").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("cache")
                && args[1].equalsIgnoreCase("key")
                && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT)
                            .startsWith(args[2].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("cache")
                && args[1].equalsIgnoreCase("key")
                && sender.hasPermission("halloweencore.admin")) {
            return List.of("1", "8", "16", "32", "64").stream()
                    .filter(value -> value.startsWith(args[3]))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")
                && sender.hasPermission("halloweencore.admin")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(value -> value.toLowerCase(java.util.Locale.ROOT)
                            .startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) return stats(sender);

        String requested = args[0].toLowerCase(java.util.Locale.ROOT);
        if (List.of("quests", "quest", "shader", "setvillage", "setvampirearena",
                "buildvampirearena", "boss", "bosseffects", "modelpreview")
                .contains(requested)) {
            sender.sendMessage(plugin.color("&eTato stará Halloween funkce byla odstavena a bude nahrazena novým vanilla systémem."));
            return true;
        }

        if (args[0].equalsIgnoreCase("setsecret")) return setSecret(sender, args);
        if (args[0].equalsIgnoreCase("secrets")) return showSecrets(sender);
        if (args[0].equalsIgnoreCase("npc")) return npc(sender, args);
        if (args[0].equalsIgnoreCase("event")) return vanillaEvent(sender, args);
        if (args[0].equalsIgnoreCase("ambience")) return ambience(sender, args);
        if (args[0].equalsIgnoreCase("cache")) return cache(sender, args);
        if (args[0].equalsIgnoreCase("top")) return top(sender);
        if (args[0].equalsIgnoreCase("progress")) return progress(sender);
        if (args[0].equalsIgnoreCase("curse")) return curse(sender);
        if (args[0].equalsIgnoreCase("rewards")) return rewards(sender);
        if (args[0].equalsIgnoreCase("claim")) return claim(sender, args);
        if (args[0].equalsIgnoreCase("challenge")) return challenge(sender);
        if (args[0].equalsIgnoreCase("reload")) return reload(sender);
        if (args[0].equalsIgnoreCase("debug")) return debug(sender);
        if (args[0].equalsIgnoreCase("give")) return give(sender, args);
        if (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off")) return toggle(sender, args[0]);

        sender.sendMessage(plugin.color("&6/halloween &7- tvoje Halloween statistiky"));
        sender.sendMessage(plugin.color("&6/halloween progress &7- společný progress serveru"));
        sender.sendMessage(plugin.color("&6/halloween curse &7- tvoje úroveň prokletí"));
        sender.sendMessage(plugin.color("&6/halloween challenge &7- dnešní výzva"));
        sender.sendMessage(plugin.color("&6/halloween rewards &7- odměny za fragmenty"));
        sender.sendMessage(plugin.color("&6/halloween claim <id> &7- vyzvednutí odměny"));
        sender.sendMessage(plugin.color("&6/halloween top &7- leaderboard"));
        sender.sendMessage(plugin.color("&6/halloween reload &7- načtení configu"));
        sender.sendMessage(plugin.color("&6/halloween debug &7- diagnostika (admin)"));
        sender.sendMessage(plugin.color("&6/halloween give <hráč> <počet> &7- admin"));
        sender.sendMessage(plugin.color("&6/halloween on|off &7- zapnutí/vypnutí progresu"));
        sender.sendMessage(plugin.color("&6/halloween event [status|start <id>|stop] &7- vanilla Halloween události"));
        sender.sendMessage(plugin.color("&6/halloween ambience [status|on|off|test] &7- ambientní Halloween efekty"));
        sender.sendMessage(plugin.color("&6/halloween secrets &7- nápovědy k tajným objevům (easter eggům)"));
        sender.sendMessage(plugin.color("&6/halloween setsecret <id> &7- nastavit polohu skrytého místa (admin)"));
        sender.sendMessage(plugin.color("&6/halloween npc spawn <jméno>|remove &7- správa dýňového vesničana (admin)"));
        sender.sendMessage(plugin.color("&6/halloween cache spawn|key <hráč> [počet] &7- podzemní schránky (admin)"));
        sender.sendMessage(plugin.color("&7Staré eventy, vlastní moby, shader, hudba, dekorace, vesnice a příběhové úkoly jsou odstavené; nové události používají vanilla systém."));
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
        HalloweenVanillaEventManager vanillaEvents = plugin.getVanillaEventManager();
        String active = vanillaEvents == null ? null : vanillaEvents.getActiveEventId();
        if (active != null) {
            sender.sendMessage(plugin.color("&7Aktivní událost: &6" + vanillaEvents.getDisplayName(active)
                    + " &7(" + vanillaEvents.getRemainingSeconds() + " s)"));
        } else {
            long nextEventSeconds = vanillaEvents == null ? -1L : vanillaEvents.getNextEventSeconds();
            if (vanillaEvents == null || !vanillaEvents.isEnabled()
                    || !plugin.isEventEnabled() || nextEventSeconds < 0L) {
                sender.sendMessage(plugin.color("&7Vanilla události: &eVYPNUTÉ"));
            } else {
                sender.sendMessage(plugin.color("&7Další událost přibližně za: &e" + nextEventSeconds + " s"));
            }
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
        boolean itemsAdderVersion = itemsAdder && plugin.getItemManager().isSupportedVersion();
        boolean cursedCandy = itemsAdder
                && plugin.getItemManager().isCustomItemAvailable("warriorland_halloween:cursed_candy");
        boolean papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;

        long total = plugin.getService().getServerFragments();
        long goal = plugin.getService().getGlobalGoal();
        double progress = plugin.getService().getGlobalProgressPercent();
        var configErrors = HalloweenConfigValidator.validate(plugin);

        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        sender.sendMessage(plugin.color("&6&lHALLOWEENCORE — DIAGNOSTIKA"));
        sender.sendMessage(plugin.color("&7Progres: " + (plugin.isEventEnabled() ? "&aZAPNUTÝ" : "&cVYPNUTÝ")));
        sender.sendMessage(plugin.color("&7Serverové fragmenty: &e" + total + " &7/ &e" + goal
                + " &8(" + String.format(java.util.Locale.ROOT, "%.1f", progress) + "%)"));
        sender.sendMessage(plugin.color("&7Online hráči: &e" + Bukkit.getOnlinePlayers().size()));
        sender.sendMessage(plugin.color("&7ItemsAdder: " + status(itemsAdder)
                + " &8• podporovaná verze: " + status(itemsAdderVersion)));
        sender.sendMessage(plugin.color("&7Vlastní předmět cursed_candy: " + status(cursedCandy)));
        sender.sendMessage(plugin.color("&7PlaceholderAPI: " + status(papi)));
        HalloweenVanillaEventManager vanillaEvents = plugin.getVanillaEventManager();
        String activeVanillaEvent = vanillaEvents == null ? null : vanillaEvents.getActiveEventId();
        sender.sendMessage(plugin.color("&7Vanilla události: "
                + status(vanillaEvents != null && vanillaEvents.isEnabled() && plugin.isEventEnabled())
                + (activeVanillaEvent == null ? "" : " &8• běží: &e" + vanillaEvents.getDisplayName(activeVanillaEvent))));
        HalloweenVanillaAtmosphereManager vanillaAmbience = plugin.getVanillaAtmosphereManager();
        String ambienceStatus = vanillaAmbience == null ? "&cNENÍ"
                : !vanillaAmbience.isEnabled() ? "&cVYPNUTÁ"
                : vanillaAmbience.isRunning() ? "&aAKTIVNÍ"
                : "&ePOZASTAVENÁ";
        sender.sendMessage(plugin.color("&7Vanilla atmosféra: " + ambienceStatus
                + " &8• pulz: "
                + (vanillaAmbience == null ? "—" : vanillaAmbience.getMinimumIntervalSeconds()
                + "–" + vanillaAmbience.getMaximumIntervalSeconds() + " s")));
        sender.sendMessage(plugin.color("&7Dýňový vesničan: "
                + status(plugin.getPumpkinVillagerManager() != null && plugin.getPumpkinVillagerManager().isEnabled())
                + " &8• podzemní schránky: "
                + status(plugin.getCacheManager() != null && plugin.getCacheManager().isEnabled())
                + " &8• světové prvky: "
                + status(plugin.getWorldFeatureManager() != null && plugin.getWorldFeatureManager().isEnabled())));
        var secretLocations = plugin.getConfig().getConfigurationSection("secret-discoveries.locations");
        long configuredSecretCount = 0L;
        long totalSecretCount = 0L;
        if (secretLocations != null) {
            for (String secretId : secretLocations.getKeys(false)) {
                var secret = secretLocations.getConfigurationSection(secretId);
                if (secret == null) continue;
                totalSecretCount++;
                if (secret.getBoolean("configured", false)) configuredSecretCount++;
            }
        }
        boolean secretsEnabled = plugin.getSecretDiscoveryManager() != null
                && plugin.getConfig().getBoolean("secret-discoveries.enabled", false);
        sender.sendMessage(plugin.color("&7Skryté Easter eggy: "
                + (secretsEnabled ? "&aZAPNUTÉ" : "&cVYPNUTÉ")
                + " &8• nastavená místa: &e" + configuredSecretCount + "&8/&e" + totalSecretCount));
        sender.sendMessage(plugin.color("&7Staré eventy, questy, vlastní mobové, soundtrack a dekorace: &eODSTAVENY"));
        sender.sendMessage(plugin.color("&7Bonus talismanu: &e+"
                + plugin.getConfig().getDouble("rewards.shop.cursed-talisman.bonus-health", 2.0D)
                + " zdraví &8(1 srdce = 2 body)"));
        sender.sendMessage(plugin.color("&7Kontrola konfigurace: "
                + (configErrors.isEmpty() ? "&aOK" : "&c" + configErrors.size() + " problémů")));
        for (String error : configErrors) {
            sender.sendMessage(plugin.color("&c  • " + error));
        }
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        return true;
    }

    private String status(boolean present) {
        return present ? "&aNALEZEN" : "&cNENÍ";
    }

    private boolean reload(CommandSender sender) {
        if (!checkAdmin(sender)) return true;

        plugin.reloadEventConfig();
        sender.sendMessage(plugin.message("messages.reloaded"));
        sender.sendMessage(plugin.color("&7Staré eventy, vlastní moby, shader, soundtrack, dekorace, vesnice a původní příběhové questy zůstávají vypnuté."));
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

    private boolean npc(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cSpráva dýňového vesničana je dostupná jen hráči."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween npc spawn <jméno> nebo /halloween npc remove"));
            return true;
        }

        HalloweenPumpkinVillagerManager manager = plugin.getPumpkinVillagerManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cSprávce dýňového vesničana není dostupný."));
            return true;
        }

        if (args[1].equalsIgnoreCase("spawn")) {
            if (!manager.isEnabled()) {
                sender.sendMessage(plugin.color("&cDýňový vesničan je vypnutý v konfiguraci (pumpkin-villager.enabled)."));
                return true;
            }
            String name = args.length > 2
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                    : "Dýňový poutník";
            if (manager.spawn(player, name) == null) {
                sender.sendMessage(plugin.color("&cDýňového vesničana se nepodařilo vytvořit."));
                return true;
            }
            sender.sendMessage(plugin.color("&aDýňový vesničan byl vytvořen před tebou. NPC je vanilla villager s dýní na hlavě."));
            return true;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            if (manager.removeNearest(player, 8.0D)) {
                sender.sendMessage(plugin.color("&aNejbližší dýňový vesničan do 8 bloků byl odstraněn."));
            } else {
                sender.sendMessage(plugin.color("&cV okruhu 8 bloků není žádný spravovaný dýňový vesničan."));
            }
            return true;
        }

        sender.sendMessage(plugin.color("&cPoužití: /halloween npc spawn <jméno> nebo /halloween npc remove"));
        return true;
    }

    private boolean cache(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cSpráva schránek je dostupná jen hráči."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween cache spawn nebo /halloween cache key <hráč> [počet]"));
            return true;
        }

        HalloweenCacheManager manager = plugin.getCacheManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cSprávce podzemních schránek není dostupný."));
            return true;
        }

        if (args[1].equalsIgnoreCase("spawn")) {
            if (!manager.isEnabled()) {
                sender.sendMessage(plugin.color("&cPodzemní schránky jsou vypnuté v konfiguraci."));
                return true;
            }
            if (manager.spawnTestCache(player)) {
                sender.sendMessage(plugin.color("&aTestovací uzamčená schránka byla vytvořena před tebou."));
            } else {
                sender.sendMessage(plugin.color("&cSchránku se nepodařilo bezpečně umístit. Zkus rovnější místo s volným prostorem."));
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("key")) {
            if (args.length < 3) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween cache key <hráč> [počet]"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.color("&cCílový hráč není online."));
                return true;
            }
            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(plugin.color("&cPočet klíčů musí být celé číslo 1–64."));
                    return true;
                }
            }
            if (!manager.giveKey(target, amount)) {
                sender.sendMessage(plugin.color("&cPočet klíčů musí být v rozsahu 1–64."));
                return true;
            }
            sender.sendMessage(plugin.color("&aHráč &f" + target.getName() + " &adostal &e" + amount + " &dklíčů ke starým schránkám."));
            target.sendMessage(plugin.color("&dZískal jsi &e" + amount + " &dklíčů ke starým schránkám."));
            return true;
        }

        sender.sendMessage(plugin.color("&cPoužití: /halloween cache spawn nebo /halloween cache key <hráč> [počet]"));
        return true;
    }

    private boolean setSecret(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return true;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz použij ve hře přímo na místě tajného objevu."));
            return true;
        }
        if (!plugin.isEligibleGameplayWorld(player.getWorld())) {
            sender.sendMessage(plugin.color("&cTajný objev musí být umístěn v herním světě povoleném v gameplay.worlds."));
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
            sender.sendMessage(plugin.color("&cTajný objev s ID &e" + id + " &cneexistuje v konfiguraci."));
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
        player.sendMessage(plugin.color("&7Každý hráč může za objev získat odměnu jen jednou."));
        return true;
    }

    private boolean showSecrets(CommandSender sender) {
        if (!checkUse(sender)) return true;
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

    private boolean ambience(CommandSender sender, String[] args) {
        if (!checkUse(sender)) return true;
        HalloweenVanillaAtmosphereManager manager = plugin.getVanillaAtmosphereManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cSprávce ambientních efektů není dostupný."));
            return true;
        }

        String action = args.length < 2 ? "status" : args[1].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status")) {
            boolean configured = manager.isEnabled();
            boolean running = manager.isRunning();
            String status = !configured ? "&cVYPNUTO"
                    : running ? "&aAKTIVNÍ"
                    : "&ePOZASTAVENO (Halloween je vypnutý)";
            sender.sendMessage(plugin.color("&6&lHALLOWEEN ATMOSFÉRA"));
            sender.sendMessage(plugin.color("&7Vanilla ambience: " + status));
            sender.sendMessage(plugin.color("&7Zvuky: "
                    + (plugin.getConfig().getBoolean("vanilla-atmosphere.sounds-enabled", true) ? "&aZAPNUTÉ" : "&cVYPNUTÉ")
                    + " &8• &7částice: "
                    + (plugin.getConfig().getBoolean("vanilla-atmosphere.particles-enabled", true) ? "&aZAPNUTÉ" : "&cVYPNUTÉ")));
            sender.sendMessage(plugin.color("&7Další pulz: náhodně každých "
                    + manager.getMinimumIntervalSeconds() + "–" + manager.getMaximumIntervalSeconds() + " sekund."));
            return true;
        }

        if (action.equals("test")) {
            if (!checkAdmin(sender)) return true;
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.color("&cTest ambientních efektů musí spustit hráč přímo ve hře."));
                return true;
            }
            if (manager.testPulse(player)) {
                sender.sendMessage(plugin.color("&aTestovací ambientní pulz byl přehrán. Zkontroluj částice a zvuk poblíž."));
            } else {
                sender.sendMessage(plugin.color("&cPulz nelze přehrát. Zkontroluj, že Halloween i vanilla atmosféra jsou zapnuté a že jsi v povoleném světě/herním režimu."));
            }
            return true;
        }

        if (!action.equals("on") && !action.equals("off")) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween ambience [status|on|off|test]"));
            return true;
        }
        if (!checkAdmin(sender)) return true;

        boolean enabled = action.equals("on");
        plugin.setVanillaAtmosphereEnabled(enabled);
        sender.sendMessage(plugin.color(enabled
                ? "&aVanilla Halloween atmosféra zapnuta."
                : "&eVanilla Halloween atmosféra vypnuta."));
        if (enabled && !plugin.isEventEnabled()) {
            sender.sendMessage(plugin.color("&7Efekty se spustí po opětovném zapnutí Halloween systému."));
        }
        return true;
    }

    private boolean vanillaEvent(CommandSender sender, String[] args) {
        if (!checkUse(sender)) return true;
        HalloweenVanillaEventManager manager = plugin.getVanillaEventManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cSprávce vanilla událostí není dostupný."));
            return true;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            String active = manager.getActiveEventId();
            if (active != null) {
                sender.sendMessage(plugin.color("&6&lHALLOWEEN UDÁLOST"));
                sender.sendMessage(plugin.color("&7Aktivní: &e" + manager.getDisplayName(active)));
                sender.sendMessage(plugin.color("&7Zbývá: &e" + manager.getRemainingSeconds() + " &7s"));
            } else {
                long next = manager.getNextEventSeconds();
                boolean running = manager.isEnabled() && plugin.isEventEnabled() && next >= 0L;
                sender.sendMessage(plugin.color("&6&lHALLOWEEN UDÁLOST"));
                sender.sendMessage(plugin.color("&7Automatické události: " + (running ? "&aZAPNUTÉ" : "&cVYPNUTÉ")));
                if (running) sender.sendMessage(plugin.color("&7Další událost přibližně za: &e" + next + " &7s"));
                sender.sendMessage(plugin.color("&7Dostupné typy: &epumpkin-harvest, witching-hour, blood-moon"));
            }
            return true;
        }

        if (!checkAdmin(sender)) return true;
        if (args[1].equalsIgnoreCase("start")) {
            if (args.length < 3) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween event start <pumpkin-harvest|witching-hour|blood-moon>"));
                return true;
            }
            if (manager.startEventNow(args[2])) {
                sender.sendMessage(plugin.color("&aSpustil jsi událost &e" + manager.getDisplayName(args[2]) + "&a."));
            } else if (!manager.isEnabled() || !plugin.isEventEnabled()) {
                sender.sendMessage(plugin.color("&cUdálosti nebo Halloween progres jsou vypnuté v konfiguraci."));
            } else if (manager.getActiveEventId() != null) {
                sender.sendMessage(plugin.color("&cJiná událost už běží. Nejdřív použij /halloween event stop."));
            } else {
                sender.sendMessage(plugin.color("&cNeznámý typ události. Použij pumpkin-harvest, witching-hour nebo blood-moon."));
            }
            return true;
        }

        if (args[1].equalsIgnoreCase("stop")) {
            if (manager.stopActiveEventNow()) {
                sender.sendMessage(plugin.color("&aAktivní vanilla událost byla ukončena."));
            } else {
                sender.sendMessage(plugin.color("&7Momentálně neběží žádná událost."));
            }
            return true;
        }

        sender.sendMessage(plugin.color("&cPoužití: /halloween event [status|start <id>|stop]"));
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
