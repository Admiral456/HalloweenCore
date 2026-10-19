package cz.halloween.core;

import java.util.Locale;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class HalloweenPlaceholders extends PlaceholderExpansion {
    private final HalloweenCore plugin;

    public HalloweenPlaceholders(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "halloween"; }
    @Override public String getAuthor() { return "HalloweenCore"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null || player.getUniqueId() == null) return "";
        String key = params.toLowerCase(Locale.ROOT);
        DialogQuestManager quests = plugin.getQuestManager();
        UUID uuid = player.getUniqueId();
        if (key.equals("fragments")) return Long.toString(quests.getFragments(uuid));
        if (key.startsWith("quest_progress_")) {
            String id = key.substring("quest_progress_".length());
            return DialogQuestManager.isValidQuestId(id) ? Integer.toString(quests.getQuestProgress(uuid, id)) : "";
        }
        if (key.startsWith("quest_started_")) {
            String id = key.substring("quest_started_".length());
            return DialogQuestManager.isValidQuestId(id) ? Boolean.toString(quests.isStarted(uuid, id)) : "";
        }
        if (key.startsWith("quest_completed_")) {
            String id = key.substring("quest_completed_".length());
            return DialogQuestManager.isValidQuestId(id) ? Boolean.toString(quests.isCompleted(uuid, id)) : "";
        }
        return "";
    }
}
