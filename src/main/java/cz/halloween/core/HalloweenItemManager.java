package cz.halloween.core;

import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
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

            String implementation = raw.getClass().getName();
            if (!implementation.startsWith("org.bukkit.")) {
                String warningKey = namespacedId + "|unsafe-itemstack";
                if (warnedMissing.add(warningKey)) {
                    plugin.getLogger().warning("ItemsAdder returned an unsupported ItemStack implementation for '"
                            + namespacedId + "' (" + implementation + "). Using vanilla fallback.");
                }
                return null;
            }

            ItemStack clone = item.clone();
            clone.setAmount(Math.max(1, amount));
            return clone;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Portable fallback-backed relics. If the named ItemsAdder IDs exist, their actual
     * custom models/textures are retained; otherwise a functional vanilla base item is
     * returned and marked with the same server-side identity.
     */
    public ItemStack createBatwingElytra() {
        return createPortableRelic(
                "warriorland_halloween:batwing_elytra",
                "batwing-elytra",
                Material.ELYTRA,
                "&5&lBatwing Elytra",
                List.of("&7Custom bat-wing relic", "&7Sneak while gliding: Bat Dash", "&8Cooldown: 20 seconds"),
                true
        );
    }

    public ItemStack createNocturneSovereignCowl() {
        return createPortableRelic(
                "warriorland_halloween:nocturne_sovereign_cowl",
                "nocturne-sovereign-cowl",
                Material.CARVED_PUMPKIN,
                "&8&lNocturne Sovereign Cowl",
                List.of("&7Wear on your head for night vision", "&7Sneak: reveal and slow nearby hostile mobs", "&8Cooldown: 30 seconds"),
                false
        );
    }

    public ItemStack createHauntedMap() {
        return createPortableRelic(
                plugin.getConfig().getString("special-mobs.map-item-id", "warriorland_halloween:haunted_map"),
                "haunted-map-2026",
                Material.PAPER,
                "&5&lProkletá mapa",
                List.of("&7Ukazuje cestu k Haunted Village."),
                false
        );
    }

    public ItemStack createCursedCandy() {
        return createPortableRelic(
                plugin.getConfig().getString("special-mobs.relic-item-id", "warriorland_halloween:cursed_candy"),
                "cursed-candy-2026",
                Material.COOKIE,
                "&6&lProkleté cukroví",
                List.of("&7Po snědení získáš krátké Halloween efekty."),
                false
        );
    }

    public ItemStack createPortableRelic(String itemsAdderId, String relicIdentity,
                                         Material fallbackMaterial, String displayName,
                                         java.util.List<String> lore, boolean elytra) {
        ItemStack item = itemsAdderId == null || itemsAdderId.isBlank()
                ? null : getItemsAdderItem(itemsAdderId, 1);
        if (item == null) item = new ItemStack(fallbackMaterial, 1);

        var meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
        meta.setLore(lore.stream()
                .map(line -> org.bukkit.ChatColor.translateAlternateColorCodes('&', line))
                .toList());
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "halloween_relic"),
                PersistentDataType.STRING,
                relicIdentity
        );
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true);
        if (elytra) meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);
        item.setItemMeta(meta);
        return item;
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
