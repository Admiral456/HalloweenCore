package cz.halloween.core;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        if (!isValidQuestId(id)) {
            player.sendMessage(ChatColor.RED + "Neplatné ID úkolu.");
            return;
        }
        UUID uuid = player.getUniqueId();
        if (isCompleted(uuid, id)) {
            player.sendMessage(ChatColor.GRAY + "Tento úkol už je dokončený.");
            return;
        }
        if (!isStarted(uuid, id)) {
            player.sendMessage(ChatColor.RED + "Úkol musíš nejprve přijmout v dialogu.");
            return;
        }

        String playerQuestPath = questPath(uuid, id);
        String configuredQuestPath = "quest-rewards.quests." + id;
        String legacyQuestPath = "quest-rewards." + id;
        boolean modernEntry = plugin.getConfig().isConfigurationSection(configuredQuestPath);
        String rewardPath = modernEntry ? configuredQuestPath : legacyQuestPath;

        String difficulty = plugin.getConfig().getString(rewardPath + ".difficulty",
                plugin.getConfig().getString("quest-rewards.default-difficulty", "light"));
        difficulty = normalizeDifficulty(difficulty);
        String tierPath = "quest-rewards.difficulties." + difficulty;

        long fragments;
        if (plugin.getConfig().contains(rewardPath + ".fragments")) {
            // Backwards-compatible fixed reward override for existing server configs.
            fragments = Math.max(0L, plugin.getConfig().getLong(rewardPath + ".fragments", 0L));
        } else {
            long min = Math.max(0L, plugin.getConfig().getLong(rewardPath + ".min-fragments",
                    plugin.getConfig().getLong(tierPath + ".min-fragments", 3L)));
            long max = Math.max(min, plugin.getConfig().getLong(rewardPath + ".max-fragments",
                    plugin.getConfig().getLong(tierPath + ".max-fragments", min)));
            fragments = randomLongInclusive(min, max);
        }

        String keyRarity = rollKeyReward(rewardPath, tierPath);
        plugin.getPlayerData().set(playerQuestPath + ".completed", true);
        plugin.getPlayerData().set(playerQuestPath + ".completed-at", Instant.now().toString());
        plugin.getPlayerData().set(playerQuestPath + ".reward.difficulty", difficulty);
        plugin.getPlayerData().set(playerQuestPath + ".reward.fragments", fragments);
        if (keyRarity != null) {
            plugin.getPlayerData().set(playerQuestPath + ".reward.key-rarity", keyRarity);
        }

        if (fragments > 0L) {
            String fragmentPath = playerPath(uuid) + ".fragments";
            long current = getFragments(uuid);
            long next = current > Long.MAX_VALUE - fragments ? Long.MAX_VALUE : current + fragments;
            plugin.getPlayerData().set(fragmentPath, next);
        }
        // Persist the completed state before external commands are run, so retries cannot pay twice.
        plugin.savePlayerData();

        List<String> rewardCommands = plugin.getConfig().getStringList(rewardPath + ".commands");
        // Also support the legacy layout when a modern quest entry is used without commands.
        if (rewardCommands.isEmpty() && modernEntry) {
            rewardCommands = plugin.getConfig().getStringList(legacyQuestPath + ".commands");
        }
        for (String rawCommand : rewardCommands) {
            if (rawCommand == null || rawCommand.isBlank()) continue;
            String prepared = rawCommand.replace("%player%", player.getName())
                    .replace("%uuid%", uuid.toString()).replace("%quest%", id).trim();
            if (prepared.startsWith("/")) prepared = prepared.substring(1);
            if (!prepared.isBlank()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
        }

        player.sendMessage(ChatColor.GREEN + "Úkol dokončen: " + ChatColor.YELLOW + id);
        if (fragments > 0L) {
            player.sendMessage(ChatColor.GOLD + "Odměna: " + fragments + " fragmentů "
                    + ChatColor.GRAY + "(obtížnost: " + difficultyName(difficulty) + ").");
        }
        if (keyRarity != null) {
            ItemStack key = plugin.getKeyItemFactory().createKey(keyRarity, 1);
            java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(key);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.sendMessage(ChatColor.GOLD + "Navíc jsi získal " + KeyItemFactory.rarityName(keyRarity)
                    + " klíč tajné místnosti.");
        }
        if (fragments == 0L && keyRarity == null && rewardCommands.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Tento úkol nemá nastavenou další odměnu.");
        }
    }

    private String rollKeyReward(String rewardPath, String tierPath) {
        double common = readChance(rewardPath + ".key-chances.common",
                plugin.getConfig().getDouble(tierPath + ".key-chances.common", 0.0));
        double rare = readChance(rewardPath + ".key-chances.rare",
                plugin.getConfig().getDouble(tierPath + ".key-chances.rare", 0.0));
        double legendary = readChance(rewardPath + ".key-chances.legendary",
                plugin.getConfig().getDouble(tierPath + ".key-chances.legendary", 0.0));

        double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
        if (roll < legendary) return "legendary";
        roll -= legendary;
        if (roll < rare) return "rare";
        roll -= rare;
        if (roll < common) return "common";
        return null;
    }

    private double readChance(String path, double fallback) {
        return Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble(path, fallback)));
    }

    private long randomLongInclusive(long min, long max) {
        if (max <= min) return min;
        long width = max - min;
        if (width == Long.MAX_VALUE) return java.util.concurrent.ThreadLocalRandom.current().nextLong(min, max);
        return min + java.util.concurrent.ThreadLocalRandom.current().nextLong(width + 1L);
    }

    private String normalizeDifficulty(String value) {
        if (value == null) return "light";
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        if (normalized.equals("veryhard")) normalized = "very-hard";
        return switch (normalized) {
            case "medium", "hard", "very-hard" -> normalized;
            default -> "light";
        };
    }

    private String difficultyName(String difficulty) {
        return switch (difficulty) {
            case "medium" -> "střední";
            case "hard" -> "těžká";
            case "very-hard" -> "velmi těžká";
            default -> "lehká";
        };
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
