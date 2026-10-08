package cz.halloween.core;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class HalloweenQuitListener implements Listener {
    private final HalloweenCore plugin;

    public HalloweenQuitListener(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDataManager() != null) {
            plugin.getDataManager().save();
        }
    }
}
