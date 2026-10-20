package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class HalloweenCommand implements CommandExecutor, TabCompleter {
    private final HalloweenCore plugin;

    public HalloweenCommand(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "quest", "quests" -> handleQuest(sender, args);
            case "fragments" -> showFragments(sender);
            case "key" -> handleKey(sender, args);
            case "rooms" -> handleRooms(sender, args);
            case "locate" -> handleLocate(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean handleQuest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                plugin.getQuestManager().showStatus(player, null);
            } else {
                sender.sendMessage(plugin.color("&cPoužití: /halloween quest <start|turnin|progress|complete|claim|status> ..."));
            }
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("status")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.color("&cStav úkolů může zobrazit pouze hráč."));
                return true;
            }
            plugin.getQuestManager().showStatus(player, args.length >= 3 ? args[2] : null);
            return true;
        }


        if (action.equals("turnin")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.color("&cOdevzdání úkolu může provést pouze hráč ve hře."));
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween quest turnin <id>"));
                return true;
            }
            var manager = plugin.getQuestManager();
            String id = manager.normalizeId(args[2]);
            HalloweenQuestManager.TurnInResult result = manager.turnIn(player, id);
            if (result.success()) {
                player.sendMessage(plugin.color("&5&lÚKOL DOKONČEN &8» &d" + manager.getTitle(id)));
                if (manager.isRewardPaid(player.getUniqueId(), id)) {
                    long fragments = manager.getConfiguredFragmentReward(id);
                    player.sendMessage(plugin.color(fragments > 0L
                            ? "&7Nastavená jednorázová odměna: &d+" + fragments + " fragmentů."
                            : "&7Jednorázová odměna podle konfigurace byla vydána."));
                } else if (manager.hasConfiguredReward(id)) {
                    player.sendMessage(plugin.color("&eÚkol je dokončen, ale nastavenou odměnu se nepodařilo vydat. Správce má zkontrolovat ID odměny; pak ji můžeš vyzvednout příkazem /halloween quest claim " + id + "."));
                } else {
                    player.sendMessage(plugin.color("&7Úkol je uložen. Odměna zatím není nastavená; nic navíc se nevydalo."));
                }
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                return true;
            }

            String reason = result.reason();
            if (reason.equals("unknown")) {
                player.sendMessage(plugin.color("&cTento quest není definovaný v quests.definitions."));
            } else if (reason.equals("completed")) {
                player.sendMessage(plugin.color("&eTento úkol už máš dokončený."));
            } else if (reason.equals("not-active")) {
                player.sendMessage(plugin.color("&eNejdřív přijmi úkol v dialogu s NPC."));
            } else if (reason.equals("objective-missing")) {
                player.sendMessage(plugin.color("&cNejdřív splň průzkumný cíl a vrať se k zadavateli."));
            } else if (reason.equals("invalid-config")) {
                player.sendMessage(plugin.color("&cPožadavky tohoto úkolu jsou chybně nastavené. Informuj správce."));
            } else if (reason.startsWith("invalid-item:")) {
                player.sendMessage(plugin.color("&cPožadovaný předmět &f" + reason.substring("invalid-item:".length())
                        + " &cnení dostupný. Správce musí opravit konfiguraci."));
            } else if (reason.startsWith("missing:")) {
                int separator = reason.lastIndexOf(':');
                String itemId = separator > 8 ? reason.substring(8, separator) : "předmět";
                String missing = separator > 8 ? reason.substring(separator + 1) : "?";
                player.sendMessage(plugin.color("&cChybí ti &f" + missing + "x " + itemId
                        + "&c. Nic nebylo odebráno."));
            } else if (reason.equals("inventory-changed")) {
                player.sendMessage(plugin.color("&cInventář se změnil během odevzdávání. Zkontroluj předměty a zkus to znovu."));
            } else {
                player.sendMessage(plugin.color("&cÚkol se nepodařilo uložit. Zkontroluj stav u /halloween quest status."));
            }
            return true;
        }

        if (action.equals("claim")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.color("&cOdměnu může vyzvednout pouze hráč."));
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween quest claim <id>"));
                return true;
            }
            var manager = plugin.getQuestManager();
            String id = manager.normalizeId(args[2]);
            if (!manager.isValidId(id)) {
                sender.sendMessage(plugin.color("&cNeplatné ID úkolu."));
            } else if (!manager.isCompleted(player.getUniqueId(), id)) {
                sender.sendMessage(plugin.color("&cTento úkol ještě není dokončený."));
            } else if (manager.isRewardPaid(player.getUniqueId(), id)) {
                sender.sendMessage(plugin.color("&eJednorázová odměna už byla vyplacena."));
            } else if (!manager.hasConfiguredReward(id)) {
                sender.sendMessage(plugin.color("&7Odměna tohoto úkolu zatím nebyla domluvena ani nastavena."));
            } else if (manager.claimConfiguredReward(player, id)) {
                long fragments = manager.getConfiguredFragmentReward(id);
                if (fragments > 0L) {
                    player.sendMessage(plugin.color("&5&lODMĚNA VYZVEDNUTA &8» &d+" + fragments + " fragmentů; případné předměty dle konfigurace."));
                } else {
                    player.sendMessage(plugin.color("&5&lODMĚNA VYZVEDNUTA &8» &dPředměty podle konfigurace."));
                }
            } else {
                sender.sendMessage(plugin.color("&cOdměnu se nepodařilo vyzvednout; zkontroluj ID předmětů v konfiguraci."));
            }
            return true;
        }

        if (action.equals("start")) {
            if (args.length < 3 || args.length > 4) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween quest start <id> [hráč]"));
                return true;
            }
            Player target = resolveTarget(sender, args.length == 4 ? args[3] : null);
            if (target == null) return true;
            if (args.length == 4 && !mayManage(sender)) {
                sender.sendMessage(plugin.color("&cÚkol pro jiného hráče může spustit pouze správce nebo dialogová akce s oprávněním."));
                return true;
            }

            var manager = plugin.getQuestManager();
            String id = manager.normalizeId(args[2]);
            if (!manager.isValidId(id)) {
                sender.sendMessage(plugin.color("&cID úkolu může obsahovat pouze písmena a-z, čísla, podtržítka a pomlčky."));
            } else if (!manager.isConfiguredQuest(id)) {
                sender.sendMessage(plugin.color("&cTento quest není definovaný v quests.definitions."));
            } else if (manager.isCompleted(target.getUniqueId(), id)) {
                sender.sendMessage(plugin.color("&eTento úkol už má hráč dokončený a nelze ho opakovat."));
            } else if (manager.isActive(target.getUniqueId(), id)) {
                sender.sendMessage(plugin.color("&eTento úkol už je aktivní."));
            } else if (!manager.start(target, id)) {
                sender.sendMessage(plugin.color("&cÚkol se nepodařilo přijmout. Zkontroluj jeho konfiguraci."));
            } else {
                target.sendMessage(plugin.color("&5&lÚKOL PŘIJAT &8» &d" + manager.getTitle(id)));
                sender.sendMessage(plugin.color("&aÚkol &f" + id + " &abyl spuštěn hráči &f" + target.getName() + "&a."));
            }
            return true;
        }

        if (action.equals("progress")) {
            if (!mayManage(sender)) {
                sender.sendMessage(plugin.color("&cPostup úkolu může měnit jen správce nebo akce UplineDialogues spuštěná jako OP."));
                return true;
            }
            if (args.length != 4 && args.length != 5) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween quest progress <id> <počet> nebo /halloween quest progress <id> <hráč> <počet>"));
                return true;
            }
            Player target;
            int amount;
            if (args.length == 4) {
                target = resolveTarget(sender, null);
                amount = parseAmount(args[3]);
            } else {
                target = resolveTarget(sender, args[3]);
                amount = parseAmount(args[4]);
            }
            if (target == null) return true;
            String id = plugin.getQuestManager().normalizeId(args[2]);
            var manager = plugin.getQuestManager();
            if (!manager.isValidId(id)) {
                sender.sendMessage(plugin.color("&cNeplatné ID úkolu."));
            } else if (amount < 1 || amount > 10000) {
                sender.sendMessage(plugin.color("&cPočet musí být od 1 do 10000."));
            } else if (!manager.addProgress(target, id, amount)) {
                sender.sendMessage(plugin.color("&cÚkol není aktivní. Nejdřív jej spusť dialogovou volbou."));
            } else {
                sender.sendMessage(plugin.color("&aPostup úkolu &f" + id + " &abyla změněna hráči &f" + target.getName()
                        + " &ana &d" + manager.getProgress(target.getUniqueId(), id) + "/" + manager.getTarget(id) + "&a."));
            }
            return true;
        }

        if (action.equals("complete")) {
            if (!mayManage(sender)) {
                sender.sendMessage(plugin.color("&cDokončení úkolu může spustit jen správce nebo akce UplineDialogues spuštěná jako OP."));
                return true;
            }
            if (args.length != 3 && args.length != 4) {
                sender.sendMessage(plugin.color("&cPoužití: /halloween quest complete <id> [hráč]"));
                return true;
            }
            Player target = resolveTarget(sender, args.length == 4 ? args[3] : null);
            if (target == null) return true;
            String id = plugin.getQuestManager().normalizeId(args[2]);
            var manager = plugin.getQuestManager();
            if (!manager.isValidId(id)) {
                sender.sendMessage(plugin.color("&cNeplatné ID úkolu."));
            } else if (manager.isCompleted(target.getUniqueId(), id)) {
                sender.sendMessage(plugin.color("&eÚkol už byl dokončen; jeho odměnu nelze získat podruhé."));
            } else if (!manager.isActive(target.getUniqueId(), id)) {
                sender.sendMessage(plugin.color("&cÚkol není aktivní."));
            } else if (manager.getProgress(target.getUniqueId(), id) < manager.getTarget(id)) {
                sender.sendMessage(plugin.color("&cÚkol není připraven k dokončení: &d"
                        + manager.getProgress(target.getUniqueId(), id) + "&7/&d" + manager.getTarget(id) + "&c."));
            } else if (manager.complete(target, id)) {
                target.sendMessage(plugin.color("&5&lÚKOL DOKONČEN &8» &d" + manager.getTitle(id)));
                if (manager.isRewardPaid(target.getUniqueId(), id)) {
                    long reward = manager.getConfiguredFragmentReward(id);
                    if (reward > 0L) {
                        target.sendMessage(plugin.color("&7Jednorázová odměna: &d+" + reward + " fragmentů."));
                    } else {
                        target.sendMessage(plugin.color("&7Jednorázová odměna podle konfigurace byla vydána."));
                    }
                } else if (!manager.hasConfiguredReward(id)) {
                    target.sendMessage(plugin.color("&7Odměna zatím není nastavena; quest se zaznamenal bez odměny."));
                } else {
                    target.sendMessage(plugin.color("&eQuest byl uložen. Odměna zatím nebyla vyplacena; zkontroluj konfiguraci a použij claim."));
                }
                sender.sendMessage(plugin.color("&aÚkol &f" + id + " &abyl jednorázově dokončen hráčem &f" + target.getName() + "&a."));
            } else {
                sender.sendMessage(plugin.color("&cÚkol se nepodařilo dokončit. Zkontroluj jeho stav."));
            }
            return true;
        }

        sender.sendMessage(plugin.color("&cPoužití: /halloween quest <start|progress|complete|status> ..."));
        return true;
    }

    private boolean handleKey(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("give")) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween key give <počet> nebo /halloween key give <vzácnost> <počet> [hráč]."));
            return true;
        }
        if (!mayManageKeys(sender)) {
            sender.sendMessage(plugin.color("&cKlíče může vydávat pouze správce nebo explicitní dialogová akce s oprávněním."));
            return true;
        }

        Player target;
        KeyItemFactory.Rarity rarity = KeyItemFactory.Rarity.COMMON;
        int amount;
        if (args.length == 3) {
            target = resolveTarget(sender, null);
            amount = parseAmount(args[2]);
        } else if (args.length == 4) {
            KeyItemFactory.Rarity parsed = parseRarity(args[2]);
            if (parsed != null) {
                rarity = parsed;
                target = resolveTarget(sender, null);
                amount = parseAmount(args[3]);
            } else {
                target = resolveTarget(sender, args[2]);
                amount = parseAmount(args[3]);
            }
        } else if (args.length == 5) {
            KeyItemFactory.Rarity parsedTierFirst = parseRarity(args[2]);
            KeyItemFactory.Rarity parsedPlayerFirst = parseRarity(args[3]);
            if (parsedTierFirst != null) {
                rarity = parsedTierFirst;
                amount = parseAmount(args[3]);
                target = resolveTarget(sender, args[4]);
            } else if (parsedPlayerFirst != null) {
                target = resolveTarget(sender, args[2]);
                rarity = parsedPlayerFirst;
                amount = parseAmount(args[4]);
            } else {
                sender.sendMessage(plugin.color("&cVzácnost musí být common, rare nebo legendary."));
                return true;
            }
        } else {
            sender.sendMessage(plugin.color("&cPoužití: /halloween key give <počet> nebo /halloween key give <vzácnost> <počet> [hráč]."));
            return true;
        }
        if (target == null) return true;
        if (amount < 1 || amount > 2304) {
            sender.sendMessage(plugin.color("&cPočet klíčů musí být od 1 do 2304."));
            return true;
        }

        plugin.getKeyItemFactory().give(target, rarity, amount);
        target.sendMessage(plugin.color("&5&lKLÍČ TAJNÉ MÍSTNOSTI &8» &dObdržel jsi " + amount + "× "
                + rarity.displayName().toLowerCase(Locale.ROOT) + " klíč."));
        sender.sendMessage(plugin.color("&aVydáno " + amount + "× " + rarity.displayName().toLowerCase(Locale.ROOT)
                + " klíč hráči &f" + target.getName() + "&a."));
        return true;
    }

    private KeyItemFactory.Rarity parseRarity(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "common", "bezny", "běžný", "běžná" -> KeyItemFactory.Rarity.COMMON;
            case "rare", "vzacny", "vzácný", "vzácná" -> KeyItemFactory.Rarity.RARE;
            case "legendary", "legendarní", "legendární" -> KeyItemFactory.Rarity.LEGENDARY;
            default -> null;
        };
    }

    private boolean handleRooms(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        var rooms = plugin.getSecretRoomManager();
        if (action.equals("generate") || action.equals("test")) {
            if (!sender.hasPermission("halloweencore.admin")) {
                sender.sendMessage(plugin.color("&cRuční test generování je pouze pro správce."));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.color("&cRuční test generování spusť přímo ve hře v Overworldu."));
                return true;
            }
            int radius = args.length >= 3 ? parseAmount(args[2])
                    : plugin.getConfig().getInt("rooms.manual-search-radius-chunks", 3);
            if (radius < 1 || radius > 6) {
                sender.sendMessage(plugin.color("&cPoloměr musí být od 1 do 6 chunků."));
                return true;
            }
            sender.sendMessage(plugin.color("&7Hledám bezpečné místo v přírodním kameni v okruhu &d" + radius + " &7chunků..."));
            if (rooms.generateTestRoom(player, radius)) {
                player.sendMessage(plugin.color("&aTestovací tajná místnost byla vygenerována. Použij /locate halloweenroom."));
                rooms.locateNearest(player, 0.0D);
            } else {
                player.sendMessage(plugin.color("&cBezpečné místo se nepodařilo najít. Zkus větší okruh nebo jiné místo."));
            }
            return true;
        }
        if (action.equals("list") || action.equals("all")) {
            rooms.locateAll(sender);
            return true;
        }
        if (action.equals("verify") || action.equals("audit")) {
            rooms.verifyRecordedRooms(sender);
            return true;
        }
        if (!action.equals("status")) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween rooms <status|generate [radius-chunků]|list|verify>"));
            return true;
        }

        boolean enabled = plugin.getConfig().getBoolean("rooms.enabled", true);
        double chance = plugin.getConfig().getDouble("rooms.chance-per-new-chunk", 0.025) * 100.0;
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        sender.sendMessage(plugin.color("&5&lTAJNÉ PODZEMNÍ MÍSTNOSTI"));
        sender.sendMessage(plugin.color("&7Automatické generování: " + (enabled ? "&aaktivní" : "&cvypnuté")));
        sender.sendMessage(plugin.color("&7Šance při vygenerování nového Overworld chunku: &d"
                + String.format(Locale.ROOT, "%.1f", chance) + "%"));
        sender.sendMessage(plugin.color("&7Hloubka Y: &d" + plugin.getConfig().getInt("rooms.min-y", -36)
                + " &7až &d" + plugin.getConfig().getInt("rooms.max-y", -8)));
        sender.sendMessage(plugin.color("&7Zkontrolované chunky: &d" + rooms.getCheckedChunkCount()
                + " &8| &7prošly šancí: &d" + rooms.getChancePassedCount()));
        sender.sendMessage(plugin.color("&7Vygenerované místnosti: &d" + rooms.getGeneratedCount()
                + " &8| &7neúspěšné pokusy: &d" + rooms.getGenerationFailedCount()));
        sender.sendMessage(plugin.color("&7Poslední místnost: &d" + rooms.getLastRoomSummary()));
        sender.sendMessage(plugin.color("&7Test: &f/halloween rooms generate &7(pouze správce)"));
        sender.sendMessage(plugin.color("&7Kontrola truhel: &f/halloween rooms verify &7(pouze správce)"));
        sender.sendMessage(plugin.color("&7Hledání: &f/locate halloweenroom"));
        sender.sendMessage(plugin.color("&8Truhly nemají loot; obsah zatím nebyl domluven."));
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        return true;
    }

    private boolean handleLocate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween locate <room|all> [max-vzdálenost]"));
            sender.sendMessage(plugin.color("&7Zkráceně: /locate halloweenroom [max-vzdálenost]"));
            return true;
        }
        String target = args[1].toLowerCase(Locale.ROOT);
        if (target.equals("all") || target.equals("rooms")) {
            plugin.getSecretRoomManager().locateAll(sender);
            return true;
        }
        if (!List.of("room", "halloweenroom", "halloween_room", "secretroom", "secret_room").contains(target)) {
            sender.sendMessage(plugin.color("&cNeznámý cíl. Použij room nebo all."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cVyhledání nejbližší místnosti vyžaduje hráče ve hře."));
            return true;
        }
        if (args.length > 3) {
            sender.sendMessage(plugin.color("&cPoužití: /halloween locate room [max-vzdálenost]"));
            return true;
        }
        double maxDistance = 0.0D;
        if (args.length == 3) {
            try {
                maxDistance = Double.parseDouble(args[2]);
                if (!Double.isFinite(maxDistance) || maxDistance < 0.0D) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                sender.sendMessage(plugin.color("&cVzdálenost musí být nezáporné číslo v blocích."));
                return true;
            }
        }
        plugin.getSecretRoomManager().locateNearest(player, maxDistance);
        return true;
    }

    private boolean showFragments(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cFragmenty může zobrazit pouze hráč."));
            return true;
        }
        sender.sendMessage(plugin.color("&5&lFRAGMENTY &8» &d" + plugin.getQuestManager().getFragments(player.getUniqueId())));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(plugin.color("&cNa to nemáš oprávnění."));
            return true;
        }
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();
        sender.sendMessage(plugin.color("&aKonfigurace questů a tajných místností byla načtena."));
        return true;
    }

    private Player resolveTarget(CommandSender sender, String requestedName) {
        if (requestedName != null) {
            Player target = Bukkit.getPlayerExact(requestedName);
            if (target == null) {
                sender.sendMessage(plugin.color("&cHráč &f" + requestedName + " &cnení online."));
            }
            return target;
        }
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(plugin.color("&cZ konzole musíš uvést hráče."));
        return null;
    }

    private boolean mayManage(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("halloweencore.quest.manage");
    }

    private boolean mayManageKeys(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("halloweencore.key.give");
    }

    private int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        sender.sendMessage(plugin.color("&5&lHALLOWEENCORE — NOVÁ VERZE"));
        sender.sendMessage(plugin.color("&d/halloween quest status [id] &7- stav a postup úkolů"));
        sender.sendMessage(plugin.color("&d/halloween quest start <id> &7- přijmout úkol z rozhovoru"));
        sender.sendMessage(plugin.color("&d/halloween quest turnin <id> &7- zkontroluje a odebere požadované předměty; dokončí úkol"));
        sender.sendMessage(plugin.color("&d/halloween quest claim <id> &7- jednorázově vyzvednout později nastavenou odměnu"));
        sender.sendMessage(plugin.color("&d/halloween fragments &7- tvoje fragmenty z dokončených úkolů"));
        sender.sendMessage(plugin.color("&d/halloween locate room &7- nejbližší známá tajná místnost"));
        sender.sendMessage(plugin.color("&d/halloween rooms status &7- stav generování tajných místností"));
        sender.sendMessage(plugin.color("&d/halloween rooms generate [radius] &7- bezpečný test generování (admin)"));
        sender.sendMessage(plugin.color("&d/locate halloweenroom &7- nejbližší zaznamenaná místnost"));
        sender.sendMessage(plugin.color("&d/locateall halloweenroom &7- všechny místnosti v tomto světě (admin)"));
        sender.sendMessage(plugin.color("&d/halloween key give <počet> &7- běžné klíče sobě (admin)"));
        sender.sendMessage(plugin.color("&d/halloween key give <common|rare|legendary> <počet> [hráč] &7- klíče podle vzácnosti"));
        sender.sendMessage(plugin.color("&d/halloween rooms verify &7- kontrola evidovaných místností (admin)"));
        sender.sendMessage(plugin.color("&d/halloween reload &7- reload konfigurace (admin)"));
        sender.sendMessage(plugin.color("&8Moby, bossové a původní eventy jsou z této verze odstraněni."));
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("quest", "quests", "fragments", "rooms", "locate", "key", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("quest") || args[0].equalsIgnoreCase("quests"))) {
            return filter(List.of("start", "turnin", "progress", "complete", "claim", "status"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rooms")) {
            return filter(List.of("status", "generate", "verify", "list"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("locate")) {
            return filter(List.of("room", "rooms", "all", "halloweenroom", "secretroom"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("key")) {
            return filter(List.of("give"), args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("quest") || args[0].equalsIgnoreCase("quests"))
                && List.of("start", "turnin", "status", "claim", "complete").contains(args[1].toLowerCase(Locale.ROOT))) {
            return filter(new ArrayList<>(plugin.getQuestManager().getCatalogIds()), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) {
            return filter(List.of("common", "rare", "legendary", "1", "3", "5"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) {
            if (parseRarity(args[2]) != null) return filter(List.of("1", "3", "5"), args[3]);
            if (Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getName().equalsIgnoreCase(args[2]))) {
                return filter(List.of("1", "3", "5"), args[3]);
            }
            return filter(List.of("1", "3", "5"), args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) {
            if (parseRarity(args[2]) != null) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[4]);
            }
            if (parseRarity(args[3]) != null) return filter(List.of("1", "3", "5"), args[4]);
            return List.of();
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
