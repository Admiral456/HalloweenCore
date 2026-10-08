package cz.halloween.core;

import java.util.UUID;

public final class HalloweenServiceImpl implements HalloweenService {
    private final HalloweenCore plugin;

    public HalloweenServiceImpl(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public long getFragments(UUID playerId) {
        return plugin.getDataManager().getFragments(playerId);
    }

    @Override
    public long addFragments(UUID playerId, long amount, String source) {
        if (!plugin.isEventEnabled() || amount <= 0L) return getFragments(playerId);

        long before = getServerFragments();
        long updated = plugin.getDataManager().addFragments(playerId, amount);

        if (plugin.getConfig().getBoolean("notifications.fragment-gain", true)) {
            plugin.notifyFragmentGain(playerId, amount);
        }

        long after = getServerFragments();
        long goal = getGlobalGoal();
        if (goal > 0L && before < goal && after >= goal) {
            plugin.broadcastGlobalGoalReached();
        }

        return updated;
    }

    @Override
    public long getServerFragments() {
        return plugin.getDataManager().getServerFragments();
    }

    @Override
    public long getGlobalGoal() {
        return Math.max(0L, plugin.getConfig().getLong("global-goal", 100000L));
    }

    @Override
    public boolean isEventEnabled() {
        return plugin.isEventEnabled();
    }

    @Override
    public double getGlobalProgressPercent() {
        long goal = getGlobalGoal();
        if (goal <= 0L) return 100.0D;
        return Math.min(100.0D, (getServerFragments() * 100.0D) / goal);
    }
}
