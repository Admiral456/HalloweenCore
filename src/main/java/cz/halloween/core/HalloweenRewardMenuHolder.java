package cz.halloween.core;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class HalloweenRewardMenuHolder implements InventoryHolder {
    private final Map<Integer, String> rewardSlots = new HashMap<>();
    private Inventory inventory;

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    public void bindReward(int slot, String rewardId) {
        rewardSlots.put(slot, rewardId);
    }

    public String getRewardId(int slot) {
        return rewardSlots.get(slot);
    }

    public Map<Integer, String> getRewardSlots() {
        return Collections.unmodifiableMap(rewardSlots);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
