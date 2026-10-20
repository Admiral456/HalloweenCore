package cz.halloween.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

public final class HalloweenQuestManager {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,47}");

    private final HalloweenCore plugin;
    private final File dataFile;
    private final YamlConfiguration data;

    public HalloweenQuestManager(HalloweenCore plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data directory.");
        }
        this.dataFile = new File(plugin.getDataFolder(), "quest-data.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public boolean isValidId(String id) {
        return id != null && VALID_ID.matcher(id.toLowerCase(java.util.Locale.ROOT)).matches();
    }

    public String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(java.util.Locale.ROOT);
    }

    public boolean start(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || isCompleted(player.getUniqueId(), id)
                || isActive(player.getUniqueId(), id)) {
            return false;
        }
        String root = questRoot(player.getUniqueId(), id);
        data.set(root + ".status", "active");
        data.set(root + ".progress", 0);
        data.set(root + ".reward-paid", false);
        save();
        return true;
    }

    public boolean addProgress(Player player, String rawId, int amount) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || amount < 1 || amount > 10000
                || !isActive(player.getUniqueId(), id)) {
            return false;
        }
        int previous = getProgress(player.getUniqueId(), id);
        int next = (int) Math.min(getTarget(id), (long) previous + amount);
        data.set(questRoot(player.getUniqueId(), id) + ".progress", next);
        save();
        return true;
    }

    /**
     * Completes a started quest only after its configured progress target is reached.
     * Fragment payout is config-driven and defaults to zero until the owner sets a reward.
     */
    public boolean complete(Player player, String rawId) {
        String id = normalizeId(rawId);
        if (player == null || !isValidId(id) || !isActive(player.getUniqueId(), id)
                || getProgress(player.getUniqueId(), id) < getTarget(id)) {
            return false;
        }

        String root = questRoot(player.getUniqueId(), id);
        if (data.getBoolean(root + ".reward-paid", false)) {
            return false;
        }

        long reward = getConfiguredFragmentReward(id);
        data.set(root + ".status", "completed");
        data.set(root + ".reward-paid", true);
        if (reward > 0L) {
            data.set(playerRoot(player.getUniqueId()) + ".fragments",
                    Math.addExact(getFragments(player.getUniqueId()), reward));
        }
        // Completion marker and any configured fragment payout are saved together.
        save();
        return true;
    }

    public boolean isActive(UUID playerId, String id) {
        return "active".equalsIgnoreCase(data.getString(questRoot(playerId, id) + ".status", "none"));
    }

    public boolean isCompleted(UUID playerId, String id) {
        return "completed".equalsIgnoreCase(data.getString(questRoot(playerId, id) + ".status", "none"));
    }

    public String getStatus(UUID playerId, String id) {
        return data.getString(questRoot(playerId, id) + ".status", "none");
    }

    public int getProgress(UUID playerId, String id) {
        return Math.max(0, data.getInt(questRoot(playerId, id) + ".progress", 0));
    }

    public int getTarget(String id) {
        int configured = plugin.getConfig().getInt("quests.definitions." + id + ".target", 1);
        return Math.max(1, Math.min(configured, 100000));
    }

    public long getConfiguredFragmentReward(String id) {
        return Math.max(0L, plugin.getConfig().getLong("quests.definitions." + id + ".reward-fragments", 0L));
    }

    public String getTitle(String id) {
        String configured = plugin.getConfig().getString("quests.definitions." + id + ".title");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String[] words = id.replace('-', ' ').replace('_', ' ').split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (title.length() > 0) title.append(' ');
            title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }

    public long getFragments(UUID playerId) {
        return Math.max(0L, data.getLong(playerRoot(playerId) + ".fragments", 0L));
    }

    public Set<String> getQuestIds(UUID playerId) {
        ConfigurationSection section = data.getConfigurationSection(playerRoot(playerId) + ".quests");
        if (section == null) {
            return Set.of();
        }
        return new TreeSet<>(section.getKeys(false));
    }

    public void showStatus(Player player, String requestedId) {
        UUID playerId = player.getUniqueId();
        player.sendMessage(plugin.color("&8&m--------------------------------"));
        player.sendMessage(plugin.color("&5&lÚKOLY"));
        player.sendMessage(plugin.color("&7Fragmenty: &d" + getFragments(playerId)));

        if (requestedId != null && !requestedId.isBlank()) {
            String id = normalizeId(requestedId);
            if (!isValidId(id)) {
                player.sendMessage(plugin.color("&cNeplatné ID úkolu."));
            } else {
                String status = getStatus(playerId, id);
                player.sendMessage(plugin.color("&f" + getTitle(id) + " &8(" + id + ")"));
                player.sendMessage(plugin.color("&7Stav: &e" + status));
                player.sendMessage(plugin.color("&7Postup: &d" + getProgress(playerId, id) + "&7/&d" + getTarget(id)));
                player.sendMessage(plugin.color("&7Jednorázová odměna ve fragmentech: &d"
                        + (status.equals("completed") ? "již vyřízeno" : getConfiguredFragmentReward(id))));
            }
            player.sendMessage(plugin.color("&8&m--------------------------------"));
            return;
        }

        Set<String> ids = getQuestIds(playerId);
        if (ids.isEmpty()) {
            player.sendMessage(plugin.color("&7Zatím nemáš přijaté žádné úkoly."));
        } else {
            for (String id : ids) {
                String status = getStatus(playerId, id);
                player.sendMessage(plugin.color((status.equals("completed") ? "&a✔ " : "&e• ")
                        + "&f" + getTitle(id) + " &8[" + id + "] &7"
                        + (status.equals("completed") ? "dokončeno" :
                        getProgress(playerId, id) + "/" + getTarget(id))));
            }
        }
        player.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save quest progress: " + ex.getMessage());
        }
    }

    private String playerRoot(UUID playerId) {
        return "players." + playerId;
    }

    private String questRoot(UUID playerId, String id) {
        return playerRoot(playerId) + ".quests." + id;
    }
}
