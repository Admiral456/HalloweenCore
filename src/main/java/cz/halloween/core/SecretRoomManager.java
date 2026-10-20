package cz.halloween.core;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

public final class SecretRoomManager implements Listener {
    private final HalloweenCore plugin;
    private final KeyItemFactory keyFactory;
    private final Random random = new Random();
    private final File roomsFile;
    private final YamlConfiguration rooms;

    public SecretRoomManager(HalloweenCore plugin, KeyItemFactory keyFactory) {
        this.plugin = plugin;
        this.keyFactory = keyFactory;
        roomsFile = new File(plugin.getDataFolder(), "secret-rooms.yml");
        rooms = YamlConfiguration.loadConfiguration(roomsFile);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !plugin.getConfig().getBoolean("secret-rooms.enabled", true)) return;
        Chunk chunk = event.getChunk();
        if (!isAllowedWorld(chunk.getWorld())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded() && isAllowedWorld(chunk.getWorld())) tryGenerateRoom(chunk);
        });
    }

    private void tryGenerateRoom(Chunk chunk) {
        double chance = Math.max(0.0, Math.min(1.0,
                plugin.getConfig().getDouble("secret-rooms.chance-per-new-chunk", 0.01)));
        if (random.nextDouble() >= chance) return;

        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;

        // Retry a handful of positions in the same new chunk so ordinary ores/caves
        // do not make the feature practically impossible to discover.
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = chunkX + 4 + random.nextInt(6);
            int z = chunkZ + 4 + random.nextInt(6);
            int surfaceY = world.getHighestBlockYAt(x + 2, z + 2);
            int minDepth = Math.max(12, plugin.getConfig().getInt("secret-rooms.min-depth-below-surface", 16));
            int maxDepth = Math.max(minDepth, plugin.getConfig().getInt("secret-rooms.max-depth-below-surface", 28));
            int y = surfaceY - minDepth - random.nextInt(maxDepth - minDepth + 1);
            if (y < world.getMinHeight() + 8 || y + 3 >= surfaceY - 8) continue;
            if (!isNaturalRockVolume(world, x, y, z)) continue;
            buildRoom(world, x, y, z);
            return;
        }
    }

    private void buildRoom(World world, int x, int y, int z) {
        for (int dx = 0; dx < 5; dx++) {
            for (int dy = 0; dy < 4; dy++) {
                for (int dz = 0; dz < 5; dz++) {
                    boolean wall = dx == 0 || dx == 4 || dy == 0 || dy == 3 || dz == 0 || dz == 4;
                    world.getBlockAt(x + dx, y + dy, z + dz)
                            .setType(wall ? Material.STONE_BRICKS : Material.AIR, false);
                }
            }
        }
        // Hidden entrance: a short tunnel with one natural-stone block left at its outer end.
        for (int dx = 1; dx <= 3; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                world.getBlockAt(x - dx, y + dy, z + 2).setType(Material.AIR, false);
            }
        }

        int chestX = x + 2;
        int chestY = y + 1;
        int chestZ = z + 2;
        Block chestBlock = world.getBlockAt(chestX, chestY, chestZ);
        chestBlock.setType(Material.CHEST, false);
        BlockState state = chestBlock.getState();
        if (state instanceof org.bukkit.block.Chest chest) chest.update(true, false);

        String path = roomPath(world.getUID(), chestX, chestY, chestZ);
        rooms.set(path + ".world-name", world.getName());
        rooms.set(path + ".x", chestX);
        rooms.set(path + ".y", chestY);
        rooms.set(path + ".z", chestZ);
        rooms.set(path + ".rarity", rollRoomRarity());
        rooms.set(path + ".locked", true);
        rooms.set(path + ".loot-generated", false);
        saveRooms();
        plugin.getLogger().info("Vygenerována tajná místnost v novém chunku: "
                + world.getName() + " " + chestX + "," + chestY + "," + chestZ);
    }

    private boolean isNaturalRockVolume(World world, int x, int y, int z) {
        // Validate the actual room volume, the tunnel and its concealed cap;
        // do not reject a candidate merely because there is an ore nearby.
        for (int checkX = x; checkX <= x + 4; checkX++) {
            for (int checkY = y; checkY <= y + 3; checkY++) {
                for (int checkZ = z; checkZ <= z + 4; checkZ++) {
                    if (!isNaturalRock(world.getBlockAt(checkX, checkY, checkZ).getType())) return false;
                }
            }
        }
        for (int dx = 1; dx <= 4; dx++) {
            for (int dy = 1; dy <= 2; dy++) {
                if (!isNaturalRock(world.getBlockAt(x - dx, y + dy, z + 2).getType())) return false;
            }
        }
        return true;
    }

    private boolean isNaturalRock(Material material) {
        return switch (material) {
            case STONE, DEEPSLATE, TUFF, GRANITE, DIORITE, ANDESITE, CALCITE,
                 DRIPSTONE_BLOCK, SMOOTH_BASALT,
                 COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> true;
            default -> false;
        };
    }

    private boolean isAllowedWorld(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return false;
        for (String denied : plugin.getConfig().getStringList("secret-rooms.excluded-worlds")) {
            if (world.getName().equalsIgnoreCase(denied)) return false;
        }
        List<String> allowed = plugin.getConfig().getStringList("secret-rooms.worlds");
        if (allowed.isEmpty()) return false;
        for (String entry : allowed) {
            if (entry.equals("*") || world.getName().equalsIgnoreCase(entry)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;
        String path = roomPath(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (!rooms.contains(path + ".locked") || !rooms.getBoolean(path + ".locked", false)) return;

        String rarity = normalizeRarity(rooms.getString(path + ".rarity", "common"));
        PlayerInventory inventory = event.getPlayer().getInventory();
        if (!hasMatchingKey(inventory, rarity)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Truhla je zamčená. Potřebuješ "
                    + rarityName(rarity) + " klíč.");
            return;
        }
        if (!consumeKey(inventory, rarity)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Nepodařilo se použít odpovídající klíč.");
            return;
        }

        // The loot is generated once, when the correct key unlocks this specific chest.
        // The tier and reward marker are saved, so the chest cannot reroll its rewards.
        populateChest(block, rarity);
        long fragments = rollFragmentReward(rarity);
        if (fragments > 0) {
            String balancePath = "players." + event.getPlayer().getUniqueId() + ".fragments";
            long current = Math.max(0L, plugin.getPlayerData().getLong(balancePath, 0L));
            long next = current > Long.MAX_VALUE - fragments ? Long.MAX_VALUE : current + fragments;
            plugin.getPlayerData().set(balancePath, next);
        }

        rooms.set(path + ".locked", false);
        rooms.set(path + ".loot-generated", true);
        rooms.set(path + ".fragments-awarded", fragments);
        rooms.set(path + ".unlocked-by", event.getPlayer().getUniqueId().toString());
        rooms.set(path + ".unlocked-at", java.time.Instant.now().toString());
        plugin.savePlayerData();
        saveRooms();

        event.getPlayer().sendMessage(ChatColor.GOLD + "Odemkl jsi " + rarityName(rarity)
                + " tajnou truhlu.");
        if (fragments > 0) {
            event.getPlayer().sendMessage(ChatColor.GOLD + "Odměna: " + fragments + " fragmentů.");
        }
        event.getPlayer().sendMessage(ChatColor.GRAY + "Obsah truhly byl vytvořen jednorázově.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;
        String path = roomPath(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (rooms.getBoolean(path + ".locked", false)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Zamčenou truhlu nelze rozbít; potřebuješ klíč.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isLockedChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isLockedChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedChestAutomation(InventoryMoveItemEvent event) {
        if (isLockedChestInventory(event.getSource()) || isLockedChestInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isLockedChestInventory(Inventory inventory) {
        Object holder = inventory.getHolder();
        return holder instanceof org.bukkit.block.Chest chest && isLockedChest(chest.getBlock());
    }

    private boolean isLockedChest(Block block) {
        if (block.getType() != Material.CHEST) return false;
        String path = roomPath(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        return rooms.getBoolean(path + ".locked", false);
    }

    private boolean hasMatchingKey(PlayerInventory inventory, String requiredRarity) {
        ItemStack offHand = inventory.getItemInOffHand();
        if (requiredRarity.equals(keyFactory.getRarity(offHand))) return true;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (requiredRarity.equals(keyFactory.getRarity(stack))) return true;
        }
        return false;
    }

    private boolean consumeKey(PlayerInventory inventory, String requiredRarity) {
        ItemStack offHand = inventory.getItemInOffHand();
        if (requiredRarity.equals(keyFactory.getRarity(offHand))) {
            if (offHand.getAmount() <= 1) inventory.setItemInOffHand(null);
            else {
                offHand.setAmount(offHand.getAmount() - 1);
                inventory.setItemInOffHand(offHand);
            }
            return true;
        }
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (!requiredRarity.equals(keyFactory.getRarity(stack))) continue;
            if (stack.getAmount() <= 1) inventory.setItem(slot, null);
            else {
                stack.setAmount(stack.getAmount() - 1);
                inventory.setItem(slot, stack);
            }
            return true;
        }
        return false;
    }

    private String rollRoomRarity() {
        int common = Math.max(0, Math.min(1_000_000,
                plugin.getConfig().getInt("secret-rooms.rarity-weights.common", 70)));
        int rare = Math.max(0, Math.min(1_000_000,
                plugin.getConfig().getInt("secret-rooms.rarity-weights.rare", 25)));
        int legendary = Math.max(0, Math.min(1_000_000,
                plugin.getConfig().getInt("secret-rooms.rarity-weights.legendary", 5)));
        int total = common + rare + legendary;
        if (total <= 0) return "common";
        int roll = random.nextInt(total);
        if (roll < common) return "common";
        roll -= common;
        if (roll < rare) return "rare";
        return "legendary";
    }

    private String normalizeRarity(String rarity) {
        return KeyItemFactory.normalizeRarity(rarity);
    }

    private String rarityName(String rarity) {
        return switch (normalizeRarity(rarity)) {
            case "rare" -> "vzácnou";
            case "legendary" -> "legendární";
            default -> "obyčejnou";
        };
    }

    private long rollFragmentReward(String rarity) {
        String path = "secret-rooms.loot." + normalizeRarity(rarity);
        long min = Math.max(0L, plugin.getConfig().getLong(path + ".min-fragments", 0L));
        long max = Math.max(min, plugin.getConfig().getLong(path + ".max-fragments", min));
        if (max <= min) return min;
        return min + random.nextLong(max - min + 1L);
    }

    private void populateChest(Block block, String rarity) {
        BlockState state = block.getState();
        if (!(state instanceof org.bukkit.block.Chest chest)) return;
        Inventory inventory = chest.getBlockInventory();
        inventory.clear();

        String path = "secret-rooms.loot." + normalizeRarity(rarity) + ".items";
        List<Map<?, ?>> entries = plugin.getConfig().getMapList(path);
        for (Map<?, ?> entry : entries) {
            String materialName = stringValue(entry.get("material"), "");
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir()) {
                plugin.getLogger().warning("Neznámý materiál v " + path + ": " + materialName);
                continue;
            }
            double chance = doubleValue(entry.get("chance"), 1.0);
            if (random.nextDouble() >= Math.max(0.0, Math.min(1.0, chance))) continue;

            int min = Math.max(1, intValue(entry.get("min"), 1));
            int max = Math.max(min, intValue(entry.get("max"), min));
            int amount = min == max ? min : min + random.nextInt(max - min + 1);
            amount = Math.min(amount, material.getMaxStackSize());
            ItemStack item = new ItemStack(material, amount);
            applyRandomEnchantment(item, entry.get("enchantments"));

            Map<Integer, ItemStack> leftovers = inventory.addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), leftover);
            }
        }
        chest.update(true, false);
    }

    private void applyRandomEnchantment(ItemStack item, Object configured) {
        if (!(configured instanceof List<?> options) || options.isEmpty()) return;
        String selected = String.valueOf(options.get(random.nextInt(options.size())));
        int separator = selected.lastIndexOf(':');
        if (separator <= 0 || separator >= selected.length() - 1) return;

        String enchantmentId = selected.substring(0, separator).toLowerCase(Locale.ROOT);
        int level;
        try {
            level = Integer.parseInt(selected.substring(separator + 1));
        } catch (NumberFormatException exception) {
            return;
        }
        if (level < 1 || level > 5) return;

        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentId));
        if (enchantment == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            bookMeta.addStoredEnchant(enchantment, level, true);
            item.setItemMeta(bookMeta);
        } else if (meta != null) {
            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private double doubleValue(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String roomPath(UUID worldUid, int x, int y, int z) {
        return "rooms." + worldUid.toString().replace("-", "") + "." + x + "_" + y + "_" + z;
    }

    public int getRoomCount() {
        ConfigurationSection root = rooms.getConfigurationSection("rooms");
        if (root == null) return 0;
        int count = 0;
        for (String worldId : root.getKeys(false)) {
            ConfigurationSection world = root.getConfigurationSection(worldId);
            if (world != null) count += world.getKeys(false).size();
        }
        return count;
    }

    public void saveRooms() {
        try {
            rooms.save(roomsFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Nepodařilo se uložit secret-rooms.yml: " + exception.getMessage());
        }
    }
}
