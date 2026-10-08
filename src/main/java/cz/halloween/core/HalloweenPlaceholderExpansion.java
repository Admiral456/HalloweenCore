package cz.halloween.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class HalloweenPlaceholderExpansion extends PlaceholderExpansion {
    private final HalloweenCore plugin;

    public HalloweenPlaceholderExpansion(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "halloween";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Admiral456";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("event_enabled")) {
            return Boolean.toString(plugin.isEventEnabled());
        }
        if (key.equals("server_fragments")) {
            return Long.toString(plugin.getService().getServerFragments());
        }
        if (key.equals("global_goal")) {
            return Long.toString(plugin.getService().getGlobalGoal());
        }
        if (key.equals("global_percent")) {
            return String.format(Locale.ROOT, "%.1f", plugin.getService().getGlobalProgressPercent());
        }
        if (key.equals("finale_unlocked")) {
            return Boolean.toString(plugin.getDataManager().isFinaleUnlocked());
        }
        if (key.equals("event")) {
            String active = plugin.getEventManager() == null ? null : plugin.getEventManager().getActiveEventId();
            return active == null ? "none" : active;
        }
        if (key.equals("event_remaining")) {
            return Long.toString(plugin.getEventManager() == null ? 0L : plugin.getEventManager().getRemainingSeconds());
        }

        if (player == null) return null;

        if (key.equals("fragments")) {
            return Long.toString(plugin.getService().getFragments(player.getUniqueId()));
        }
        if (key.equals("lifetime_fragments")) {
            return Long.toString(plugin.getDataManager().getLifetimeFragments(player.getUniqueId()));
        }
        if (key.equals("curse_level")) {
            return Integer.toString(plugin.getService().getCurseLevel(player.getUniqueId()));
        }
        if (key.equals("curse_name")) {
            return plugin.getService().getCurseName(player.getUniqueId());
        }
        if (key.equals("multiplier")) {
            return String.format(Locale.ROOT, "%.2f", plugin.getService().getFragmentMultiplier(player.getUniqueId(), "mob-kill"));
        }
        if (key.equals("streak")) {
            return Integer.toString(plugin.getDataManager().getStreak(player.getUniqueId()));
        }
        if (key.equals("village_discovered")) {
            return Boolean.toString(plugin.getDataManager().hasDiscoveredVillage(player.getUniqueId()));
        }
        if (key.equals("vampire_boss_phase")) {
            return Integer.toString(plugin.getVampireEncounterManager() == null
                    ? 0 : plugin.getVampireEncounterManager().getPhase());
        }
        if (key.equals("vampire_boss_hp_percent")) {
            double health = plugin.getBossManager() == null ? 0.0D : plugin.getBossManager().getVampireBossHealthPercent();
            return String.format(Locale.ROOT, "%.1f", health * 100.0D);
        }
        if (key.equals("vampire_boss_participants")) {
            return Integer.toString(plugin.getVampireEncounterManager() == null
                    ? 0 : plugin.getVampireEncounterManager().getParticipantCount());
        }
        if (key.equals("vampire_boss_active")) {
            return Boolean.toString(plugin.getVampireEncounterManager() != null
                    && plugin.getVampireEncounterManager().isActive());
        }

        return null;
    }
}
