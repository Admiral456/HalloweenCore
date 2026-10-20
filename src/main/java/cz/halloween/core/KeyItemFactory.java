package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class KeyItemFactory {
    public static final String ITEMSADDER_ID = "warriorland_halloween:secret_room_key";

    private final HalloweenCore plugin;
    private final NamespacedKey markerKey;
    private boolean warnedItemsAdder;

    public KeyItemFactory(HalloweenCore plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "secret_room_key");
    }

    public ItemStack createKey() {
        String configuredId = plugin.getConfig().getString("rooms.key-item-id", ITEMSADDER_ID);
        if (configuredId == null || configuredId.isBlank()) configuredId = ITEMSADDER_ID;
        ItemStack stack = createItemsAdderStack(configuredId);
        if (stack == null) {
            stack = new ItemStack(Material.TRIPWIRE_HOOK);
        } else {
            stack = stack.clone();
        }
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color("&5&lKlíč skryté místnosti"));
            meta.setLore(List.of(
                    plugin.color("&7Otevírá zamčené truhly ve skrytých podzemních místnostech."),
                    plugin.color("&8Spotřebuje se při prvním odemčení.")
            ));
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public ItemStack createConfiguredItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        String configuredKeyId = plugin.getConfig().getString("rooms.key-item-id", ITEMSADDER_ID);
        if (configuredKeyId == null || configuredKeyId.isBlank()) configuredKeyId = ITEMSADDER_ID;
        if (itemId.equalsIgnoreCase(configuredKeyId) || itemId.equalsIgnoreCase(ITEMSADDER_ID)) {
            return createKey();
        }
        if (itemId.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            Material material = Material.matchMaterial(itemId.substring("minecraft:".length()));
            if (material == null || material.isAir()) return null;
            return new ItemStack(material);
        }
        return createItemsAdderStack(itemId);
    }

    public boolean isKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        Byte marker = stack.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    public void give(Player player, int amount) {
        int remaining = Math.max(1, Math.min(amount, 2304));
        while (remaining > 0) {
            ItemStack key = createKey();
            int count = Math.min(remaining, key.getMaxStackSize());
            key.setAmount(count);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(key);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= count;
        }
    }

    private ItemStack createItemsAdderStack(String itemId) {
        org.bukkit.plugin.Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null) {
            return null;
        }
        try {
            ClassLoader loader = itemsAdder.getClass().getClassLoader();
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack", true, loader);
            Method getInstance = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstance.invoke(null, itemId);
            if (customStack == null) {
                warnItemsAdder("ItemsAdder is present, but " + itemId + " is not registered yet.");
                return null;
            }
            Object result = customStackClass.getMethod("getItemStack").invoke(customStack);
            return result instanceof ItemStack item ? item : null;
        } catch (ReflectiveOperationException | LinkageError ex) {
            warnItemsAdder("Could not request an item from the ItemsAdder API; using a vanilla fallback for the key if needed. "
                    + ex.getClass().getSimpleName());
            return null;
        }
    }

    private void warnItemsAdder(String message) {
        if (warnedItemsAdder) return;
        warnedItemsAdder = true;
        plugin.getLogger().warning(message);
    }
}
