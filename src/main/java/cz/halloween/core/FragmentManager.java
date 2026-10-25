package cz.halloween.core;

import java.util.UUID;

/**
 * Reads the persistent virtual Halloween fragment balance.
 *
 * Dialog/NPC quest actions intentionally do not live in HalloweenCore.
 * Daily and weekly contracts, Jobs and the shop use the same saved balance.
 */
public final class FragmentManager {
    private final HalloweenCore plugin;

    public FragmentManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public long getFragments(UUID uuid) {
        if (uuid == null) return 0L;
        return Math.max(0L, plugin.getPlayerData().getLong("players." + uuid + ".fragments", 0L));
    }
}
