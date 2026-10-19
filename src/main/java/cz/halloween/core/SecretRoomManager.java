package cz.halloween.core;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

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
        int x = chunkX + 4 + random.nextInt(6);
        int z = chunkZ + 4 + random.nextInt(6);
        int surfaceY = world.getHighestBlockYAt(x + 2, z + 2);
        int minDepth = Math.max(12, plugin.getConfig().getInt("secret-rooms.min-depth-below-surface", 16));
        int maxDepth = Math.max(minDepth, plugin.getConfig().getInt("secret-rooms.max-depth-below-surface", 28));
        int y = surfaceY - minDepth - random.nextInt(maxDepth - minDepth + 1);
        if (y < world.getMinHeight() + 8 || y + 3 >= surfaceY - 8) return;
        if (!isNaturalRockVolume(world, x, y, z)) return;

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
        rooms.set(path + ".locked", true);
        saveRooms();
        plugin.getLogger().info("Vygenerována tajná místnost v novém chunku: "
                + world.getName() + " " + chestX + "," + chestY + "," + chestZ);
    }

    private boolean isNaturalRockVolume(World world, int x, int y, int z) {
        for (int checkX = x - 4; checkX <= x + 5; checkX++) {
            for (int checkY = y - 1; checkY <= y + 3; checkY++) {
                for (int checkZ = z - 1; checkZ <= z + 5; checkZ++) {
                    if (!isNaturalRock(world.getBlockAt(checkX, checkY, checkZ).getType())) return false;
                }
            }
        }
        return true;
    }

    private boolean isNaturalRock(Material material) {
        return material == Material.STONE || material == Material.DEEPSLATE || material == Material.TUFF
                || material == Material.GRANITE || material == Material.DIORITE || material == Material.ANDESITE
                || material == Material.CALCITE || material == Material.DRIPSTONE_BLOCK
                || material == Material.SMOOTH_BASALT;
    }

    private boolean isAllowedWorld(World world) {
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

        if (!consumeKey(event.getPlayer().getInventory())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Truhla je zamčená. Potřebuješ speciální klíč.");
            return;
        }
        rooms.set(path + ".locked", false);
        rooms.set(path + ".unlocked-by", event.getPlayer().getUniqueId().toString());
        saveRooms();
        event.getPlayer().sendMessage(ChatColor.GOLD + "Použil jsi speciální klíč a odemkl tajnou truhlu.");
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

    private boolean isLockedChest(Block block) {
        if (block.getType() != Material.CHEST) return false;
        String path = roomPath(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        return rooms.getBoolean(path + ".locked", false);
    }

    private boolean consumeKey(PlayerInventory inventory) {
        ItemStack offHand = inventory.getItemInOffHand();
        if (keyFactory.isKey(offHand)) {
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
            if (!keyFactory.isKey(stack)) continue;
            if (stack.getAmount() <= 1) inventory.setItem(slot, null);
            else {
                stack.setAmount(stack.getAmount() - 1);
                inventory.setItem(slot, stack);
            }
            return true;
        }
        return false;
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
