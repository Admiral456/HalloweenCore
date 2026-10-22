/* Adapted from SpookySeason 1.7.0 by BoondockSulfur; MIT license. See licenses/spookyseason-MIT.txt. */
package cz.halloween.core.spooky.cmd;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import cz.halloween.core.HalloweenCore;
import cz.halloween.core.spooky.feature.HalloweenBossManager;
import cz.halloween.core.spooky.lang.Lang;
import cz.halloween.core.spooky.prefs.PlayerStats;

public class SpookyToggleCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> PLAYER_SUBS = List.of("optout", "top", "menu");
    private static final List<String> ALL_SUBS = List.of("on", "off", "status", "reload", "rewards", "spawn", "optout", "top", "menu");
    private static final List<String> SPAWN_SUBS = List.of("boss");

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("command.spooky.usage", "label", label));
            return true;
        }
        block11 : switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on": {
                if (!this.checkAdmin(sender)) {
                    return true;
                }
                HalloweenCore.get().getConfig().set("active", (Object)true);
                HalloweenCore.get().saveConfig();
                HalloweenCore.get().startAll();
                sender.sendMessage(Lang.get("command.spooky.enabled", new String[0]));
                break;
            }
            case "off": {
                if (!this.checkAdmin(sender)) {
                    return true;
                }
                HalloweenCore.get().getConfig().set("active", (Object)false);
                HalloweenCore.get().saveConfig();
                HalloweenCore.get().stopAll();
                sender.sendMessage(Lang.get("command.spooky.disabled", new String[0]));
                break;
            }
            case "status": {
                if (!this.checkAdmin(sender)) {
                    return true;
                }
                sender.sendMessage(HalloweenCore.get().isSeasonActive() ? Lang.get("command.spooky.status-active", new String[0]) : Lang.get("command.spooky.status-inactive", new String[0]));
                break;
            }
            case "reload": {
                if (!this.checkAdmin(sender)) {
                    return true;
                }
                HalloweenCore.get().reload();
                sender.sendMessage(Lang.get("command.spooky.reloaded", new String[0]));
                break;
            }
            case "rewards": {
                if (!this.checkAdmin(sender)) {
                    return true;
                }
                HalloweenCore.get().rewards().distributeManually();
                sender.sendMessage(Lang.get("command.spooky.rewards-distributed", new String[0]));
                break;
            }
            case "spawn": {
                if (!this.checkAdmin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
                    return true;
                }
                Player p = (Player)sender;
                if (args.length < 2) {
                    sender.sendMessage(Lang.get("command.spooky.spawn-usage", "label", label));
                    return true;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "boss": {
                        HalloweenBossManager bossMgr = HalloweenCore.get().boss();
                        bossMgr.forceSpawn(p);
                        p.sendMessage(Lang.get("command.spooky.spawn-boss", new String[0]));
                        break block11;
                    }
                }
                sender.sendMessage(Lang.get("command.spooky.spawn-usage", "label", label));
                break;
            }
            case "optout": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
                    return true;
                }
                Player p = (Player)sender;
                boolean optedOut = HalloweenCore.get().prefs().toggleOptOut(p.getUniqueId());
                p.sendMessage(optedOut ? Lang.get("command.spooky.optout-on", new String[0]) : Lang.get("command.spooky.optout-off", new String[0]));
                break;
            }
            case "top": {
                if (!HalloweenCore.get().getConfig().getBoolean("treatLeaderboard.enabled", true)) {
                    sender.sendMessage(Lang.get("command.spooky.top-empty", new String[0]));
                    break;
                }
                int limit = 10;
                List<Map.Entry<UUID, Integer>> top = HalloweenCore.get().stats().getTopTreats(limit);
                if (top.isEmpty()) {
                    sender.sendMessage(Lang.get("command.spooky.top-empty", new String[0]));
                    break;
                }
                sender.sendMessage(Lang.get("command.spooky.top-header", new String[0]));
                int rank = 1;
                for (Map.Entry<UUID, Integer> entry : top) {
                    String name = PlayerStats.resolveName(entry.getKey());
                    sender.sendMessage(Lang.get("command.spooky.top-entry", "rank", String.valueOf(rank++), "player", name, "treats", String.valueOf(entry.getValue())));
                }
                break;
            }
            case "menu": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
                    return true;
                }
                Player p = (Player)sender;
                HalloweenCore.get().menu().open(p);
                break;
            }
            default: {
                sender.sendMessage(Lang.get("command.unknown-arg", new String[0]));
            }
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = sender.hasPermission("spooky.admin") ? ALL_SUBS : PLAYER_SUBS;
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn") && sender.hasPermission("spooky.admin")) {
            return SPAWN_SUBS.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private boolean checkAdmin(CommandSender sender) {
        if (sender.hasPermission("spooky.admin")) {
            return true;
        }
        sender.sendMessage(Lang.get("command.spooky.no-permission", new String[0]));
        return false;
    }
}

