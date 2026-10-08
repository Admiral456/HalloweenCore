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

    public boolean isSupportedVersion() {
        String minimum = plugin.getConfig().getString("itemsadder.minimum-version", "4.0.13");
        var pluginInstance = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        if (pluginInstance == null) return false;
        return compareVersions(pluginInstance.getDescription().getVersion(), minimum) >= 0;
    }

    private int compareVersions(String actual, String minimum) {
        int[] a = versionParts(actual);
        int[] b = versionParts(minimum);
        for (int idx = 0; idx < Math.max(a.length, b.length); idx++) {
            int av = idx < a.length ? a[idx] : 0;
            int bv = idx < b.length ? b[idx] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private int[] versionParts(String value) {
        if (value == null || value.isBlank()) return new int[] {0};
        String[] parts = value.split("\\.");
        int[] result = new int[Math.min(parts.length, 3)];
        for (int idx = 0; idx < result.length; idx++) {
            String digits = parts[idx].replaceAll("[^0-9].*$", "");
            try {
                result[idx] = Integer.parseInt(digits.isBlank() ? "0" : digits);
            } catch (NumberFormatException ignored) {
                result[idx] = 0;
            }
        }
        return result;
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
