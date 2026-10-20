package cz.halloween.core;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class KeyItemFactory {
    public static final String ITEM_ID = "warriorland_halloween:secret_key";
    private static final List<String> RARITIES = List.of("common", "rare", "legendary");

    private final HalloweenCore plugin;
    private final NamespacedKey marker;
    private final NamespacedKey rarityMarker;

    public KeyItemFactory(HalloweenCore plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "secret_room_key");
        rarityMarker = new NamespacedKey(plugin, "secret_room_key_rarity");
    }

    public ItemStack createKey(int amount) {
        return createKey("common", amount);
    }

    /**
     * Rarity variants reuse the existing single secret_key texture. Tier metadata is
     * stored in the item's persistent data, not inferred from display names.
     */
    public ItemStack createKey(String rarity, int amount) {
        String tier = normalizeRarity(rarity);
        ItemStack key = getItemsAdderKey();
        boolean custom = key != null;
        if (key == null) key = new ItemStack(Material.TRIPWIRE_HOOK);
        key.setAmount(Math.max(1, Math.min(64, amount)));

        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName(tier));
            meta.setLore(List.of(
                    ChatColor.GRAY + "Odemkne odpovídající tajnou podzemní truhlu.",
                    ChatColor.DARK_GRAY + "Vzácnost: " + rarityName(tier),
                    ChatColor.DARK_GRAY + (custom ? "HalloweenCore · ItemsAdder" : "HalloweenCore · vanilla")));
            meta.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(rarityMarker, PersistentDataType.STRING, tier);
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
        // Also accepts a base ItemsAdder key issued directly by UplineDialogues.
        return isItemsAdderKey(stack);
    }

    /** Base ItemsAdder keys without tier metadata are treated as common keys. */
    public String getRarity(ItemStack stack) {
        if (!isKey(stack)) return "";
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String saved = meta.getPersistentDataContainer().get(rarityMarker, PersistentDataType.STRING);
            if (saved != null && RARITIES.contains(saved.toLowerCase(Locale.ROOT))) {
                return saved.toLowerCase(Locale.ROOT);
            }
        }
        return "common";
    }

    public static boolean isValidRarity(String rarity) {
        return rarity != null && RARITIES.contains(rarity.toLowerCase(Locale.ROOT));
    }

    public static String normalizeRarity(String rarity) {
        if (rarity == null) return "common";
        String value = rarity.toLowerCase(Locale.ROOT);
        return RARITIES.contains(value) ? value : "common";
    }

    public static String rarityName(String rarity) {
        return switch (normalizeRarity(rarity)) {
            case "rare" -> "vzácná";
            case "legendary" -> "legendární";
            default -> "obyčejná";
        };
    }

    private String displayName(String rarity) {
        return switch (rarity) {
            case "rare" -> ChatColor.AQUA + "" + ChatColor.BOLD + "Vzácný klíč tajné místnosti";
            case "legendary" -> ChatColor.GOLD + "" + ChatColor.BOLD + "Legendární klíč tajné místnosti";
            default -> ChatColor.GRAY + "Obyčejný klíč tajné místnosti";
        };
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
