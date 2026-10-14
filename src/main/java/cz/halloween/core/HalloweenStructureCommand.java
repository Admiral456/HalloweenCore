package cz.halloween.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class HalloweenStructureCommand implements CommandExecutor, TabCompleter {
    private final HalloweenCore plugin;

    public HalloweenStructureCommand(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("halloweencore.structures") && !sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(plugin.color("&cNa stavbu Halloween struktur nemáš oprávnění."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cStrukturu vyvolej ve hře: /spawn <hřbitov|vesnice|aréna|vampirearena|goblinarena>."));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(plugin.color("&ePoužití: &6/spawn <hřbitov|vesnice|aréna|vampirearena|goblinarena>"));
            sender.sendMessage(plugin.color("&7Umístění před tebou proběhne automaticky — bez souřadnic a potvrzení."));
            sender.sendMessage(plugin.color("&7Při konfliktu s jiným pluginem použij /halloween spawn <typ>."));
            return true;
        }
        HalloweenStructureManager manager = plugin.getStructureManager();
        if (manager == null) {
            sender.sendMessage(plugin.color("&cGenerátor struktur není dostupný."));
            return true;
        }
        manager.buildBeforePlayer(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || (!sender.hasPermission("halloweencore.structures")
                && !sender.hasPermission("halloweencore.admin"))) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("hřbitov", "vesnice", "aréna", "vampirearena", "goblinarena").stream()
                .filter(value -> value.startsWith(prefix)).toList();
    }
}
