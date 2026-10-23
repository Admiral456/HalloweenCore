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
        FragmentManager fragments = plugin.getFragmentManager();
        UUID uuid = player.getUniqueId();
        if (key.equals("fragments")) return Long.toString(fragments.getFragments(uuid));
        return "";
    }
}
