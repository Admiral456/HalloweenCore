package cz.halloween.core;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class HalloweenItemManager {
    private final HalloweenCore plugin;
    private final Set<String> warnedMissing = new HashSet<>();

    public HalloweenItemManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public ItemStack getItemsAdderItem(String namespacedId, int amount) {
        if (namespacedId == null || namespacedId.isBlank()) return null;

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstance.invoke(null, namespacedId);
            if (customStack == null) return null;

            Method getItemStack = customStackClass.getMethod("getItemStack");
            Object raw = getItemStack.invoke(customStack);
            if (!(raw instanceof ItemStack item)) return null;

            ItemStack clone = item.clone();
            clone.setAmount(Math.max(1, amount));
            return clone;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    public boolean isItemsAdderAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public boolean isCustomItemAvailable(String namespacedId) {
        return getItemsAdderItem(namespacedId, 1) != null;
    }

    public void warnIfMissing(String namespacedId, String context) {
        if (namespacedId == null || namespacedId.isBlank() || isItemsAdderAvailable() && getItemsAdderItem(namespacedId, 1) != null) {
            return;
        }

        String warningKey = namespacedId + "|" + context;
        if (warnedMissing.add(warningKey)) {
            plugin.getLogger().warning("ItemsAdder item '" + namespacedId + "' is unavailable for " + context
                    + ". HalloweenCore will use the configured vanilla fallback.");
        }
    }
}
