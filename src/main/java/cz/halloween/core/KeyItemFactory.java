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
    /** Legacy ID remains the common key for existing configurations. */
    public static final String ITEMSADDER_ID = "warriorland_halloween:secret_room_key";

    public enum Rarity {
        COMMON("common", "Běžný", "&f", "warriorland_halloween:secret_room_key"),
        RARE("rare", "Vzácný", "&b", "warriorland_halloween:secret_room_key_rare"),
        LEGENDARY("legendary", "Legendární", "&6", "warriorland_halloween:secret_room_key_legendary");

        private final String id;
        private final String displayName;
        private final String color;
        private final String defaultItemId;

        Rarity(String id, String displayName, String color, String defaultItemId) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
            this.defaultItemId = defaultItemId;
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public String color() { return color; }
        public String defaultItemId() { return defaultItemId; }

        public static Rarity fromId(String value) {
            if (value != null) {
                for (Rarity rarity : values()) {
                    if (rarity.id.equalsIgnoreCase(value)) return rarity;
                }
            }
            return COMMON;
        }
    }

    private final HalloweenCore plugin;
    private final NamespacedKey markerKey;
    private final NamespacedKey rarityKey;
    private boolean warnedItemsAdder;

    public KeyItemFactory(HalloweenCore plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "secret_room_key");
        this.rarityKey = new NamespacedKey(plugin, "secret_room_key_rarity");
    }

    public ItemStack createKey() {
        return createKey(Rarity.COMMON);
    }

    public ItemStack createKey(Rarity rarity) {
        if (rarity == null) rarity = Rarity.COMMON;
        ItemStack stack = createItemsAdderStack(getConfiguredItemId(rarity));
        if (stack == null) {
            stack = new ItemStack(Material.TRIPWIRE_HOOK);
        } else {
            stack = stack.clone();
        }
        stack.setAmount(1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color(rarity.color() + "&l" + rarity.displayName()
                    + " klíč tajné místnosti"));
            meta.setLore(List.of(
                    plugin.color("&7Otevírá truhlu vzácnosti: " + rarity.color() + rarity.displayName()),
                    plugin.color("&8Spotřebuje se při prvním odemčení.")
            ));
            // Keep the old BYTE marker so keys already in inventories remain valid as COMMON.
            meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity.id());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String getConfiguredItemId(Rarity rarity) {
        String configured = plugin.getConfig().getString("rooms.key-item-ids." + rarity.id(),
                rarity.defaultItemId());
        if (configured == null || configured.isBlank()) {
            configured = rarity == Rarity.COMMON
                    ? plugin.getConfig().getString("rooms.key-item-id", ITEMSADDER_ID)
                    : rarity.defaultItemId();
        }
        return configured == null || configured.isBlank() ? rarity.defaultItemId() : configured;
    }

    public ItemStack createConfiguredItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        for (Rarity rarity : Rarity.values()) {
            if (itemId.equalsIgnoreCase(getConfiguredItemId(rarity))
                    || itemId.equalsIgnoreCase(rarity.defaultItemId())) {
                return createKey(rarity);
            }
        }
        String legacyId = plugin.getConfig().getString("rooms.key-item-id", ITEMSADDER_ID);
        if (itemId.equalsIgnoreCase(legacyId) || itemId.equalsIgnoreCase(ITEMSADDER_ID)) {
            return createKey(Rarity.COMMON);
        }
        if (itemId.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            Material material = Material.matchMaterial(itemId.substring("minecraft:".length()));
            if (material == null || material.isAir()) return null;
            return new ItemStack(material);
        }
        return createItemsAdderStack(itemId);
    }

    public boolean isKey(ItemStack stack) {
        return getRarity(stack) != null;
    }

    public boolean isKey(ItemStack stack, Rarity required) {
        return required != null && required == getRarity(stack);
    }

    public Rarity getRarity(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        Byte marker = pdc.get(markerKey, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return null;
        String rarity = pdc.get(rarityKey, PersistentDataType.STRING);
        return rarity == null ? Rarity.COMMON : Rarity.fromId(rarity);
    }

    public void give(Player player, int amount) {
        give(player, Rarity.COMMON, amount);
    }

    public void give(Player player, Rarity rarity, int amount) {
        int remaining = Math.max(1, Math.min(amount, 2304));
        while (remaining > 0) {
            ItemStack key = createKey(rarity);
            int count = Math.min(remaining, key.getMaxStackSize());
            key.setAmount(count);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(key);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= count;
        }
    }

    private ItemStack createItemsAdderStack(String itemId) {
        org.bukkit.plugin.Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null) return null;
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
            warnItemsAdder("Could not request an item from the ItemsAdder API; using a vanilla fallback for a key if needed. "
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
