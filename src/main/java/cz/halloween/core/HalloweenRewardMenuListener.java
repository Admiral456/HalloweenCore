package cz.halloween.core;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class HalloweenRewardMenuListener implements Listener {
    private final HalloweenCore plugin;

    public HalloweenRewardMenuListener(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HalloweenRewardMenuHolder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String rewardId = holder.getRewardId(event.getRawSlot());
        if (rewardId == null) return;

        plugin.getRewardManager().claim(player, rewardId);
        player.closeInventory();
    }

    @EventHandler(ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof HalloweenRewardMenuHolder)) return;
        event.setCancelled(true);
    }
}
