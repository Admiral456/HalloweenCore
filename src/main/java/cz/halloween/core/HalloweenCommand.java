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
                    "stats", "progress", "curse", "challenge", "rewards", "claim",
                    "top", "reload", "debug", "give", "on", "off"
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
        if (List.of("event", "quests", "quest", "shader", "setvillage", "setvampirearena",
                "buildvampirearena", "setsecret", "secrets", "boss", "bosseffects", "modelpreview")
                .contains(requested)) {
            sender.sendMessage(plugin.color("&eTato stará Halloween funkce byla odstavena a bude nahrazena novým vanilla systémem."));
            return true;
        }

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
        sender.sendMessage(plugin.color("&7Staré eventy, vlastní moby, shader, hudba, dekorace, vesnice a příběhové úkoly jsou odstavené."));
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
            long nextEventSeconds = plugin.getEventManager().getNextEventSeconds();
            if (!plugin.getConfig().getBoolean("random-events.enabled", false) || nextEventSeconds < 0L) {
                sender.sendMessage(plugin.color("&7Automatické eventy: &eVYPNUTÉ"));
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
