package cz.halloween.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public final class HalloweenPlaceholderExpansion extends PlaceholderExpansion {
    private final HalloweenCore plugin;

    public HalloweenPlaceholderExpansion(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "halloween";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null || params == null) return "";
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("fragments")) {
            return Long.toString(plugin.getQuestManager().getFragments(player.getUniqueId()));
        }
        if (key.startsWith("villager_quest_")) {
            HalloweenQuestManager manager = plugin.getQuestManager();
            String field = key.substring("villager_quest_".length());
            String id = manager.getVillagerQuestId(player.getUniqueId());
            String status = manager.getVillagerQuestStatus(player.getUniqueId());
            if (field.equals("status")) return status;
            if (field.equals("has_quest")) return Boolean.toString(manager.hasVillagerQuest(player.getUniqueId()));
            if (id == null || id.isBlank() || !manager.isConfiguredQuest(id)) {
                return switch (field) {
                    case "id", "title", "description", "difficulty", "reward", "reward_items", "story" -> "";
                    default -> null;
                };
            }
            return switch (field) {
                case "id" -> id;
                case "title" -> manager.getTitle(id);
                case "description" -> manager.getDescription(id);
                case "story" -> plugin.getConfig().getString("quests.definitions." + id + ".dialogue-story", manager.getDescription(id));
                case "difficulty" -> manager.getDifficulty(id);
                case "reward" -> Long.toString(manager.getConfiguredFragmentReward(id));
                case "reward_items" -> manager.getConfiguredRewardItemsDescription(id);
                default -> null;
            };
        }
        if (!key.startsWith("quest_")) return null;

        String[] suffixes = {"_started", "_progress", "_completed"};
        for (String suffix : suffixes) {
            if (!key.endsWith(suffix)) continue;
            String id = key.substring("quest_".length(), key.length() - suffix.length());
            if (!plugin.getQuestManager().isValidId(id)) return "";
            return switch (suffix) {
                case "_started" -> Boolean.toString(plugin.getQuestManager().isActive(player.getUniqueId(), id)
                        || plugin.getQuestManager().isCompleted(player.getUniqueId(), id));
                case "_progress" -> Integer.toString(plugin.getQuestManager().getProgress(player.getUniqueId(), id));
                case "_completed" -> Boolean.toString(plugin.getQuestManager().isCompleted(player.getUniqueId(), id));
                default -> "";
            };
        }
        return null;
    }
}
