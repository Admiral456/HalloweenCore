package cz.halloween.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class DialogQuestManager {
    private final HalloweenCore plugin;

    public DialogQuestManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isValidQuestId(String id) {
        return id != null && id.matches("[a-z0-9_-]{1,48}");
    }

    private String playerPath(UUID uuid) {
        return "players." + uuid;
    }

    private String questPath(UUID uuid, String id) {
        return playerPath(uuid) + ".quests." + id;
    }

    public long getFragments(UUID uuid) {
        return Math.max(0L, plugin.getPlayerData().getLong(playerPath(uuid) + ".fragments", 0L));
    }

    public int getQuestProgress(UUID uuid, String id) {
        if (!isValidQuestId(id)) return 0;
        return Math.max(0, plugin.getPlayerData().getInt(questPath(uuid, id) + ".progress", 0));
    }

    public boolean isStarted(UUID uuid, String id) {
        return isValidQuestId(id) && plugin.getPlayerData().getBoolean(questPath(uuid, id) + ".started", false);
    }

    public boolean isCompleted(UUID uuid, String id) {
        return isValidQuestId(id) && plugin.getPlayerData().getBoolean(questPath(uuid, id) + ".completed", false);
    }

    public void startQuest(Player player, String id) {
        UUID uuid = player.getUniqueId();
        if (isCompleted(uuid, id)) {
            player.sendMessage(ChatColor.GRAY + "Tento úkol už máš dokončený.");
            return;
        }
        String path = questPath(uuid, id);
        if (plugin.getPlayerData().getBoolean(path + ".started", false)) {
            player.sendMessage(ChatColor.GRAY + "Tento úkol už máš přijatý.");
            return;
        }
        plugin.getPlayerData().set(path + ".started", true);
        plugin.getPlayerData().set(path + ".progress", 0);
        plugin.getPlayerData().set(path + ".started-at", Instant.now().toString());
        plugin.savePlayerData();
        player.sendMessage(ChatColor.GREEN + "Úkol přijat: " + ChatColor.YELLOW + id);
    }

    public void progressQuest(Player player, String id, int amount) {
        if (amount < 1 || amount > 100000) {
            player.sendMessage(ChatColor.RED + "Přírůstek postupu musí být v rozsahu 1 až 100000.");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!isStarted(uuid, id)) {
            player.sendMessage(ChatColor.RED + "Nejdřív musíš úkol přijmout u zadavatele.");
            return;
        }
        if (isCompleted(uuid, id)) {
            player.sendMessage(ChatColor.GRAY + "Dokončený úkol už nelze dál posouvat.");
            return;
        }
        String path = questPath(uuid, id);
        int next = (int) Math.min(2_000_000_000L, (long) getQuestProgress(uuid, id) + amount);
        plugin.getPlayerData().set(path + ".progress", next);
        plugin.savePlayerData();
        player.sendMessage(ChatColor.GREEN + "Postup úkolu " + ChatColor.YELLOW + id
                + ChatColor.GREEN + ": " + ChatColor.YELLOW + next);
    }

    public void completeQuest(Player player, String id) {
        UUID uuid = player.getUniqueId();
        if (isCompleted(uuid, id)) {
            player.sendMessage(ChatColor.GRAY + "Tento úkol už je dokončený.");
            return;
        }
        if (!isStarted(uuid, id)) {
            player.sendMessage(ChatColor.RED + "Úkol musíš nejprve přijmout v dialogu.");
            return;
        }

        String path = questPath(uuid, id);
        plugin.getPlayerData().set(path + ".completed", true);
        plugin.getPlayerData().set(path + ".completed-at", Instant.now().toString());
        String rewardPath = "quest-rewards." + id;
        long fragments = Math.max(0L, plugin.getConfig().getLong(rewardPath + ".fragments", 0L));
        if (fragments > 0L) {
            String fragmentPath = playerPath(uuid) + ".fragments";
            plugin.getPlayerData().set(fragmentPath, getFragments(uuid) + fragments);
        }
        // Completed state and configured fragments are persisted before any configured command is executed.
        plugin.savePlayerData();

        List<String> rewardCommands = plugin.getConfig().getStringList(rewardPath + ".commands");
        for (String rawCommand : rewardCommands) {
            if (rawCommand == null || rawCommand.isBlank()) continue;
            String prepared = rawCommand.replace("%player%", player.getName())
                    .replace("%uuid%", uuid.toString()).replace("%quest%", id).trim();
            if (prepared.startsWith("/")) prepared = prepared.substring(1);
            if (!prepared.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
        }
        player.sendMessage(ChatColor.GREEN + "Úkol dokončen: " + ChatColor.YELLOW + id);
        if (fragments > 0L) player.sendMessage(ChatColor.GOLD + "Získal jsi " + fragments + " fragmentů.");
        if (fragments == 0L && rewardCommands.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Pro tento úkol zatím není nakonfigurovaná žádná odměna.");
        }
    }

    public void sendQuestStatus(Player player, String id) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getPlayerData().contains(questPath(uuid, id))) {
            player.sendMessage(ChatColor.GRAY + "Úkol " + id + " zatím nemáš v evidenci.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Úkol " + ChatColor.YELLOW + id + ChatColor.GOLD + ": "
                + status(uuid, id) + ", postup: " + ChatColor.YELLOW + getQuestProgress(uuid, id));
    }

    public void sendQuestList(Player player) {
        ConfigurationSection section = plugin.getPlayerData()
                .getConfigurationSection(playerPath(player.getUniqueId()) + ".quests");
        if (section == null || section.getKeys(false).isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Zatím nemáš žádné přijaté úkoly.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "Tvoje questy:");
        for (String id : section.getKeys(false)) {
            player.sendMessage(ChatColor.YELLOW + "- " + id + ChatColor.GRAY + " — "
                    + status(player.getUniqueId(), id) + ", postup " + getQuestProgress(player.getUniqueId(), id));
        }
    }

    private String status(UUID uuid, String id) {
        if (isCompleted(uuid, id)) return "dokončeno";
        return isStarted(uuid, id) ? "probíhá" : "nepřijato";
    }
}
