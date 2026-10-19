package cz.halloween.core;

import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class KeyItemFactory {
    public static final String ITEM_ID = "warriorland_halloween:secret_key";
    private final HalloweenCore plugin;
    private final NamespacedKey marker;

    public KeyItemFactory(HalloweenCore plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "secret_room_key");
    }

    public ItemStack createKey(int amount) {
        ItemStack key = getItemsAdderKey();
        boolean custom = key != null;
        if (key == null) key = new ItemStack(Material.TRIPWIRE_HOOK);
        key.setAmount(Math.max(1, Math.min(64, amount)));
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            if (!custom) {
                meta.setDisplayName(ChatColor.GOLD + "Klíč tajné místnosti");
                meta.setLore(List.of(
                        ChatColor.GRAY + "Odemkne zamčenou truhlu v tajné podzemní místnosti.",
                        ChatColor.DARK_GRAY + "HalloweenCore"));
            }
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            key.setItemMeta(meta);
        }
        return key;
    }

    public boolean isKey(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer()
                .getOrDefault(marker, PersistentDataType.BYTE, (byte) 0) == 1) {
            return true;
        }
        // This also accepts the same ItemsAdder item handed out directly by UplineDialogues.
        return isItemsAdderKey(stack);
    }

    private boolean isItemsAdderKey(ItemStack stack) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) return false;
        try {
            Class<?> type = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = type.getMethod("byItemStack", ItemStack.class).invoke(null, stack);
            if (customStack == null) return false;
            Object id = type.getMethod("getNamespacedID").invoke(customStack);
            return id instanceof String value && ITEM_ID.equalsIgnoreCase(value);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }

    private ItemStack getItemsAdderKey() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) return null;
        try {
            Class<?> type = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = type.getMethod("getInstance", String.class);
            Object customStack = getInstance.invoke(null, ITEM_ID);
            if (customStack == null) return null;
            Object result = type.getMethod("getItemStack").invoke(customStack);
            return result instanceof ItemStack item ? item.clone() : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Texturovaný klíč z ItemsAdder není dostupný; použije se vanilla klíč.");
            return null;
        }
    }
}
