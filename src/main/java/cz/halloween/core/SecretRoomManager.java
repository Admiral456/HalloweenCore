package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SecretRoomManager implements Listener {
    private static final int ROOM_WIDTH = 7;
    private static final int ROOM_HEIGHT = 5;
    private static final int ROOM_DEPTH = 7;
    private static final Set<Material> NATURAL_STONE = Set.of(
            Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.CALCITE, Material.DRIPSTONE_BLOCK
    );

    private final HalloweenCore plugin;
    private final KeyItemFactory keyItemFactory;
    private final NamespacedKey lockedKey;
    private final File dataFile;
    private final YamlConfiguration data;

    public SecretRoomManager(HalloweenCore plugin, KeyItemFactory keyItemFactory) {
        this.plugin = plugin;
        this.keyItemFactory = keyItemFactory;
        this.lockedKey = new NamespacedKey(plugin, "secret_room_locked");
        this.dataFile = new File(plugin.getDataFolder(), "secret-rooms.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewChunk(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !plugin.getConfig().getBoolean("rooms.enabled", true)) return;
        Chunk chunk = event.getChunk();
        if (!isAllowedWorld(chunk.getWorld())) return;
        double chance = Math.max(0.0, Math.min(1.0,
                plugin.getConfig().getDouble("rooms.chance-per-new-chunk", 0.025)));
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        // Defer the actual block changes until the chunk-load event is complete.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded() && isAllowedWorld(chunk.getWorld())) {
                generateRoomInChunk(chunk);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.CHEST) return;

        BlockState state = clicked.getState();
        if (!(state instanceof Chest chest)) return;
        Byte locked = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.BYTE);
        if (locked == null || locked != (byte) 1) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean inMainHand = keyItemFactory.isKey(held);
        if (!inMainHand) {
            held = player.getInventory().getItemInOffHand();
        }
        if (!keyItemFactory.isKey(held)) {
            player.sendMessage(plugin.color("&5&lZAMČENÁ TRUHLA &8» &7Potřebuješ speciální klíč."));
            return;
        }

        consumeOne(player, inMainHand);
        chest.getPersistentDataContainer().remove(lockedKey);
        chest.update(true, false);
        player.sendMessage(plugin.color("&5&lTRUHLA ODEMČENA &8» &dKlíč byl spotřebován. Truhla zůstane odemčená."));
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 0.8f);

        Block chestBlock = clicked;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && chestBlock.getType() == Material.CHEST
                    && chestBlock.getState() instanceof Chest unlockedChest) {
                player.openInventory(unlockedChest.getBlockInventory());
            }
        });
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedChestBreak(BlockBreakEvent event) {
        if (!isLockedChest(event.getBlock())) return;
        event.setCancelled(true);
        if (event.getPlayer() != null) {
            event.getPlayer().sendMessage(plugin.color("&5&lZAMČENÁ TRUHLA &8» &7Bez speciálního klíče ji nelze otevřít ani rozbít."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isLockedChest);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isLockedChest);
    }

    private boolean isLockedChest(Block block) {
        if (block == null || block.getType() != Material.CHEST) return false;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return false;
        Byte locked = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.BYTE);
        return locked != null && locked == (byte) 1;
    }

    public int getGeneratedCount() {
        return Math.max(0, data.getInt("stats.generated", 0));
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save secret-room stats: " + ex.getMessage());
        }
    }

    private boolean generateRoomInChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight() + 8,
                plugin.getConfig().getInt("rooms.min-y", -36));
        int maxY = Math.min(world.getMaxHeight() - ROOM_HEIGHT - 2,
                plugin.getConfig().getInt("rooms.max-y", 24));
        if (maxY < minY) return false;

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int attempt = 0; attempt < 64; attempt++) {
            // Keep the whole 7x7 footprint away from chunk edges.
            int x = baseX + 2 + ThreadLocalRandom.current().nextInt(8);
            int z = baseZ + 2 + ThreadLocalRandom.current().nextInt(8);
            int y = minY + ThreadLocalRandom.current().nextInt(maxY - minY + 1);
            if (!isSolidNaturalRock(world, x, y, z)) continue;

            buildRoom(world, x, y, z);
            data.set("stats.generated", getGeneratedCount() + 1);
            String roomId = world.getName() + "_" + (x + ROOM_WIDTH / 2) + "_" + (y + 1) + "_" + (z + ROOM_DEPTH / 2);
            String roomPath = "rooms." + roomId;
            data.set(roomPath + ".world", world.getName());
            data.set(roomPath + ".x", x + ROOM_WIDTH / 2);
            data.set(roomPath + ".y", y + 1);
            data.set(roomPath + ".z", z + ROOM_DEPTH / 2);
            data.set("last.world", world.getName());
            data.set("last.x", x + ROOM_WIDTH / 2);
            data.set("last.y", y + 1);
            data.set("last.z", z + ROOM_DEPTH / 2);
            save();
            plugin.getLogger().fine("Generated a locked secret room in " + world.getName()
                    + " at " + (x + 3) + "," + (y + 1) + "," + (z + 3) + ".");
            return true;
        }
        plugin.getLogger().fine("No safe solid-stone volume found for a secret room in chunk "
                + chunk.getX() + "," + chunk.getZ() + " (" + world.getName() + ") after 64 attempts.");
        return false;
    }

    public Location findNearestRoom(Location origin) {
        if (origin == null || origin.getWorld() == null) return null;
        var rooms = data.getConfigurationSection("rooms");
        if (rooms == null) return null;
        return rooms.getKeys(false).stream()
                .map(id -> {
                    String path = "rooms." + id;
                    World world = Bukkit.getWorld(data.getString(path + ".world", ""));
                    if (world == null || !world.equals(origin.getWorld())) return null;
                    return new Location(world, data.getInt(path + ".x"), data.getInt(path + ".y"),
                            data.getInt(path + ".z"));
                })
                .filter(location -> location != null)
                .min(Comparator.comparingDouble(location -> location.distanceSquared(origin)))
                .orElse(null);
    }

    public boolean generateTestRoom(Location origin) {
        if (origin == null || origin.getWorld() == null || !isAllowedWorld(origin.getWorld())) return false;
        Chunk chunk = origin.getChunk();
        return generateRoomInChunk(chunk);
    }

    private boolean isSolidNaturalRock(World world, int x, int y, int z) {
        // Do not carve existing caves, ores, liquids, tile entities or player-built blocks.
        for (int dx = 0; dx < ROOM_WIDTH; dx++) {
            for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                for (int dz = 0; dz < ROOM_DEPTH; dz++) {
                    Material type = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (!NATURAL_STONE.contains(type)) return false;
                }
            }
        }
        return true;
    }

    private void buildRoom(World world, int x, int y, int z) {
        for (int dx = 0; dx < ROOM_WIDTH; dx++) {
            for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                for (int dz = 0; dz < ROOM_DEPTH; dz++) {
                    boolean shell = dx == 0 || dx == ROOM_WIDTH - 1
                            || dz == 0 || dz == ROOM_DEPTH - 1
                            || dy == 0 || dy == ROOM_HEIGHT - 1;
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    block.setType(shell ? Material.DEEPSLATE_BRICKS : Material.AIR, false);
                }
            }
        }

        Block chestBlock = world.getBlockAt(x + ROOM_WIDTH / 2, y + 1, z + ROOM_DEPTH / 2);
        chestBlock.setType(Material.CHEST, false);
        BlockState state = chestBlock.getState();
        if (state instanceof Chest chest) {
            chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.BYTE, (byte) 1);
            chest.update(true, false);
        }
        // The chest intentionally remains empty until its agreed contents are specified.
    }

    private boolean isAllowedWorld(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return false;
        List<String> blocked = plugin.getConfig().getStringList("rooms.blocked-worlds");
        for (String name : blocked) {
            if (name.equalsIgnoreCase(world.getName())) return false;
        }
        return true;
    }

    private void consumeOne(Player player, boolean mainHand) {
        ItemStack stack = mainHand ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (stack.getAmount() <= 1) {
            if (mainHand) player.getInventory().setItemInMainHand(null);
            else player.getInventory().setItemInOffHand(null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            if (mainHand) player.getInventory().setItemInMainHand(stack);
            else player.getInventory().setItemInOffHand(stack);
        }
    }
}
