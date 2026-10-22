package cz.halloween.core;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class DailyQuestManager implements Listener, CommandExecutor, TabCompleter {
    private record Task(String id, String title, String description, int target) {}

    private static final List<Task> TASKS = List.of(
            new Task("zombies", "Lov na nemrtvé", "Zabij 8 zombie.", 8),
            new Task("skeletons", "Kostlivci po setmění", "Zabij 8 kostlivců.", 8),
            new Task("iron", "Železná zásoba", "Vytěž 16 železných rud.", 16),
            new Task("pumpkins", "Dýňová sklizeň", "Rozbij 12 dýní.", 12));

    private final HalloweenCore plugin;
    private final Random random = new Random();

    public DailyQuestManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Příkaz /quest může použít jen hráč.");
            return true;
        }
        if (!plugin.isSeasonActive()) {
            player.sendMessage(ChatColor.RED + "Halloween sezóna právě není aktivní.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("daily-quest.enabled", true)) {
            player.sendMessage(ChatColor.RED + "Denní úkoly jsou vypnuté.");
            return true;
        }

        String path = path(player.getUniqueId());
        ensureAssigned(player, path, true);
        show(player, path);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.isSeasonActive()) return;

        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("daily-quest.enabled", true)) {
            return;
        }

        String path = path(player.getUniqueId());
        if (ensureAssigned(player, path, true)) {
            show(player, path);
        }
    }

    private boolean ensureAssigned(Player player, String path, boolean notify) {
        String today = today();
        if (today.equals(plugin.getPlayerData().getString(path + ".date", ""))) {
            return false;
        }
        assign(player, path, today, notify);
        return true;
    }

    private void assign(Player player, String path, String today, boolean notify) {
        String previousId = plugin.getPlayerData().getString(path + ".id", "");
        List<Task> choices = TASKS.stream()
                .filter(task -> !task.id().equals(previousId))
                .toList();
        Task task = choices.isEmpty()
                ? TASKS.get(random.nextInt(TASKS.size()))
                : choices.get(random.nextInt(choices.size()));

        plugin.getPlayerData().set(path + ".date", today);
        plugin.getPlayerData().set(path + ".id", task.id());
        plugin.getPlayerData().set(path + ".title", task.title());
        plugin.getPlayerData().set(path + ".description", task.description());
        plugin.getPlayerData().set(path + ".target", task.target());
        plugin.getPlayerData().set(path + ".progress", 0);
        plugin.getPlayerData().set(path + ".completed", false);
        plugin.savePlayerData();

        if (notify) {
            player.sendMessage(ChatColor.DARK_PURPLE + "☾ "
                    + ChatColor.GOLD + "Dostal jsi nový denní úkol!");
        }
    }

    private void show(Player player, String path) {
        String title = plugin.getPlayerData().getString(path + ".title", "Denní úkol");
        int progress = plugin.getPlayerData().getInt(path + ".progress", 0);
        int target = Math.max(1, plugin.getPlayerData().getInt(path + ".target", 1));
        if (plugin.getPlayerData().getBoolean(path + ".completed", false)) {
            player.sendMessage(ChatColor.GREEN + "Dnešní úkol už máš hotový: " + title);
            return;
        }

        int reward = Math.max(0, plugin.getConfig().getInt("daily-quest.reward-fragments", 1));
        player.sendMessage(ChatColor.DARK_PURPLE + "╔════════ H A L L O W E E N ════════╗");
        player.sendMessage(ChatColor.GOLD + "☾ ÚKOL: " + ChatColor.YELLOW + title);
        player.sendMessage(ChatColor.GRAY
                + plugin.getPlayerData().getString(path + ".description", ""));
        player.sendMessage(ChatColor.GRAY + "Postup: " + ChatColor.GOLD
                + Math.min(progress, target) + ChatColor.GRAY + "/" + ChatColor.GOLD + target);
        player.sendMessage(ChatColor.DARK_GRAY + "Odměna: " + ChatColor.GOLD
                + reward + (reward == 1 ? " fragment" : " fragmentů"));
        player.sendMessage(ChatColor.DARK_PURPLE + "╚══════════════════════════════════╝");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKilled(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) {
            return;
        }
        if (event.getEntityType() == EntityType.ZOMBIE) {
            increment(player, "zombies");
        } else if (event.getEntityType() == EntityType.SKELETON) {
            increment(player, "skeletons");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material material = event.getBlock().getType();
        if (material == Material.IRON_ORE || material == Material.DEEPSLATE_IRON_ORE) {
            increment(event.getPlayer(), "iron");
        } else if (material == Material.PUMPKIN || material == Material.CARVED_PUMPKIN) {
            increment(event.getPlayer(), "pumpkins");
        }
    }

    private void increment(Player player, String taskId) {
        if (!plugin.isSeasonActive()) return;

        if (!plugin.getConfig().getBoolean("daily-quest.enabled", true)) {
            return;
        }

        String path = path(player.getUniqueId());
        // Handles players who stay online over midnight as well as a plugin reload
        // while the player is online. The activity that triggers the reset is not counted.
        if (ensureAssigned(player, path, true)) {
            show(player, path);
            return;
        }
        if (!taskId.equals(plugin.getPlayerData().getString(path + ".id", ""))
                || plugin.getPlayerData().getBoolean(path + ".completed", false)) {
            return;
        }

        int target = Math.max(1, plugin.getPlayerData().getInt(path + ".target", 1));
        int progress = Math.min(target,
                Math.max(0, plugin.getPlayerData().getInt(path + ".progress", 0)) + 1);
        plugin.getPlayerData().set(path + ".progress", progress);

        if (progress >= target) {
            plugin.getPlayerData().set(path + ".completed", true);
            long reward = Math.max(0L, plugin.getConfig().getLong("daily-quest.reward-fragments", 1));
            String balancePath = "players." + player.getUniqueId() + ".fragments";
            long balance = Math.max(0L, plugin.getPlayerData().getLong(balancePath, 0));
            plugin.getPlayerData().set(balancePath,
                    balance > Long.MAX_VALUE - reward ? Long.MAX_VALUE : balance + reward);
            player.sendMessage(ChatColor.GREEN + "Splnil jsi denní úkol! " + ChatColor.GOLD
                    + "+" + reward + (reward == 1 ? " fragment." : " fragmentů."));
        } else {
            player.sendMessage(ChatColor.GRAY + "Denní úkol: "
                    + ChatColor.YELLOW + progress + "/" + target);
        }
        plugin.savePlayerData();
    }

    private String path(UUID uuid) {
        return "players." + uuid + ".daily-quest";
    }

    private String today() {
        return LocalDate.now(ZoneId.of("Europe/Prague")).toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
