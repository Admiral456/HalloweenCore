package cz.halloween.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla underground treasure caches, protected until a tagged key is used.
 *
 * Natural generation only runs on newly generated Overworld chunks, so enabling this
 * feature does not retrofit or destructively scan existing player-built areas.
 */
public final class HalloweenCacheManager implements Listener {
    private static final List<Material> NATURAL_STONE = List.of(
            Material.STONE,
            Material.DEEPSLATE,
            Material.TUFF,
            Material.ANDESITE,
            Material.DIORITE,
            Material.GRANITE,
            Material.CALCITE,
            Material.DRIPSTONE_BLOCK
    );
    private static final Material[] LOOT = {
            Material.IRON_INGOT,
            Material.GOLD_INGOT,
            Material.EMERALD,
            Material.AMETHYST_SHARD,
            Material.EXPERIENCE_BOTTLE,
            Material.BONE,
            Material.STRING,
            Material.PUMPKIN_SEEDS,
            Material.GOLDEN_APPLE
    };

    private final HalloweenCore plugin;
    private final NamespacedKey lockedCacheKey;
    private final NamespacedKey cacheKeyItemKey;

    public HalloweenCacheManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.lockedCacheKey = new NamespacedKey(plugin, "locked_underground_cache");
        this.cacheKeyItemKey = new NamespacedKey(plugin, "underground_cache_key");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("underground-caches.enabled", true);
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        if (!isEnabled()
                || !plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("underground-caches.generation.enabled", true)
                || !event.isNewChunk()) return;

        Chunk chunk = event.getChunk();
        if (chunk.getWorld().getEnvironment() != World.Environment.NORMAL) return;

        double chance = clamp(plugin.getConfig().getDouble(
                "underground-caches.generation.chance-per-new-chunk", 0.10D), 0.0D, 1.0D);
        if (chance <= 0.0D) return;

        long seed = chunk.getWorld().getSeed()
                ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L)
                ^ 0x434143484548414CL;
        Random random = new Random(seed);
        if (random.nextDouble() >= chance) return;

        int attempts = Math.max(1, Math.min(64,
                plugin.getConfig().getInt("underground-caches.generation.attempts-per-chunk", 32)));
        int configuredMinY = plugin.getConfig().getInt("underground-caches.generation.min-y", -48);
        int configuredMaxY = plugin.getConfig().getInt("underground-caches.generation.max-y", 24);
        int minY = Math.max(chunk.getWorld().getMinHeight() + 2, configuredMinY);
        int maxY = Math.min(chunk.getWorld().getMaxHeight() - 3, configuredMaxY);
        if (minY > maxY) {
            plugin.getLogger().warning("Underground cache generation skipped because min-y exceeds max-y.");
            return;
        }

        for (int i = 0; i < attempts; i++) {
            // Keep the candidate at least one block inside the chunk edge. The
            // 3x3x3 safety scan then cannot trigger neighbor-chunk loads.
            int localX = random.nextInt(1, 15);
            int localZ = random.nextInt(1, 15);
            int x = (chunk.getX() << 4) + localX;
            int z = (chunk.getZ() << 4) + localZ;
            int y = random.nextInt(minY, maxY + 1);
            Block target = chunk.getBlock(localX, y, localZ);
            if (!isNaturalCacheTarget(target)) continue;
            if (createLockedCache(target, random)) {
                plugin.getLogger().fine("Generated locked Halloween cache at "
                        + chunk.getWorld().getName() + " " + x + " " + y + " " + z);
                return; // Never generate more than one cache in a chunk.
            }
        }
    }

    public boolean spawnTestCache(Player creator) {
        if (creator == null || !creator.isOnline() || !isEnabled()) return false;
        Vector facing = creator.getLocation().getDirection().setY(0.0D);
        if (facing.lengthSquared() < 0.0001D) facing = new Vector(0.0D, 0.0D, 1.0D);
        facing.normalize();
        for (int distance = 2; distance <= 5; distance++) {
            Block target = creator.getLocation().clone().add(facing.clone().multiply(distance)).getBlock();
            int localX = target.getX() & 15;
            int localZ = target.getZ() & 15;
            // The neighborhood safety scan below must remain inside the loaded chunk.
            if (localX < 1 || localX > 14 || localZ < 1 || localZ > 14) continue;
            if (!target.getType().isAir() || !target.getRelative(0, -1, 0).getType().isSolid()) continue;
            if (hasTileStateInNeighborhood(target)) continue;
            if (createLockedCache(target, new Random())) {
                return true;
            }
        }
        return false;
    }

    public ItemStack createKey(int amount) {
        int safeAmount = Math.max(1, Math.min(64, amount));
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, safeAmount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Klíč ke staré schránce"));
            meta.lore(List.of(
                    Component.text("Otevírá uzamčené podzemní Halloween schránky."),
                    Component.text("Pravý klíč poznáš podle jeho magické značky.")
            ));
            meta.getPersistentDataContainer().set(cacheKeyItemKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean giveKey(Player player, int amount) {
        if (player == null || !player.isOnline() || amount < 1 || amount > 64) return false;
        var overflow = player.getInventory().addItem(createKey(amount));
        for (ItemStack leftover : overflow.values()) {
            if (leftover == null || leftover.getType().isAir()) continue;
            var dropped = player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            dropped.setOwner(player.getUniqueId());
            dropped.setPickupDelay(20);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Chest cache = findLockedCache(event.getInventory().getHolder());
        if (cache == null) return;

        // Disabling the cache feature must never leave existing containers
        // permanently inaccessible. When disabled, remove the lock and let the
        // chest behave like an ordinary vanilla chest without consuming a key.
        if (!isEnabled()) {
            if (!unlockCache(cache)) {
                event.setCancelled(true);
                player.sendMessage(plugin.color("&8[&6Halloween&8] &cSchránku se nepodařilo bezpečně odemknout."));
            }
            return;
        }

        if (!consumeKey(player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.color("&8[&6Halloween&8] &cSchránka je zamčená. Potřebuješ Klíč ke staré schránce."));
            return;
        }

        if (!unlockCache(cache)) {
            giveKey(player, 1);
            event.setCancelled(true);
            player.sendMessage(plugin.color("&8[&6Halloween&8] &cSchránku se nepodařilo odemknout; klíč ti byl vrácen."));
            return;
        }

        player.sendMessage(plugin.color("&8[&6Halloween&8] &aKlíč zapadl do zámku. Schránka je odemčená."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCacheBreak(BlockBreakEvent event) {
        if (!isLockedCache(event.getBlock())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.color("&cSchránku nelze rozbít, dokud ji neodemkneš klíčem."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isLockedCache);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isLockedCache);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.CHEST) return;
        Block placed = event.getBlockPlaced();
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (isLockedCache(placed.getRelative(face))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.color("&cK zamčené schránce nelze připojit další truhlu."));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (findLockedCache(event.getSource().getHolder()) != null
                || findLockedCache(event.getDestination().getHolder()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isLockedCache)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isLockedCache)) event.setCancelled(true);
    }

    @EventHandler
    public void onHostileMobDeath(EntityDeathEvent event) {
        if (!isEnabled()
                || !plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("underground-caches.key-drop.enabled", true)
                || !(event.getEntity() instanceof Monster)) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null
                || !plugin.isEligibleGameplayPlayer(killer)
                || !plugin.isEligibleGameplayWorld(killer.getWorld())) return;

        double chance = clamp(plugin.getConfig().getDouble(
                "underground-caches.key-drop.chance-per-hostile-kill", 0.006D), 0.0D, 0.05D);
        if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) return;

        event.getDrops().add(createKey(1));
        killer.sendMessage(plugin.color("&dDýňový klíč! &7Vypadl z nepřítele."));
    }

    private boolean createLockedCache(Block target, Random random) {
        if (target == null || (!target.getType().isAir() && !NATURAL_STONE.contains(target.getType()))) return false;
        Material original = target.getType();
        target.setType(Material.CHEST, false);
        if (!(target.getState() instanceof Chest chest)) {
            target.setType(original, false);
            return false;
        }

        chest.getPersistentDataContainer().set(lockedCacheKey, PersistentDataType.BYTE, (byte) 1);
        fillLoot(chest.getBlockInventory(), random);
        if (!chest.update(true, false)) {
            target.setType(original, false);
            return false;
        }
        return true;
    }

    private void fillLoot(Inventory inventory, Random random) {
        int stacks = 2 + random.nextInt(3);
        for (int i = 0; i < stacks; i++) {
            Material material = LOOT[random.nextInt(LOOT.length)];
            int amount = switch (material) {
                case IRON_INGOT -> 2 + random.nextInt(5);
                case GOLD_INGOT -> 1 + random.nextInt(4);
                case EMERALD -> 1 + random.nextInt(3);
                case AMETHYST_SHARD -> 2 + random.nextInt(7);
                case EXPERIENCE_BOTTLE -> 1 + random.nextInt(4);
                case BONE, STRING -> 2 + random.nextInt(5);
                case PUMPKIN_SEEDS -> 3 + random.nextInt(10);
                case GOLDEN_APPLE -> 1;
                default -> 1;
            };
            inventory.addItem(new ItemStack(material, amount));
        }
    }

    private boolean isNaturalCacheTarget(Block target) {
        if (target == null || !NATURAL_STONE.contains(target.getType())) return false;
        if (!target.getRelative(0, 1, 0).getType().isAir()) return false;
        if (target.getY() <= target.getWorld().getMinHeight() || target.getY() >= target.getWorld().getMaxHeight() - 1) {
            return false;
        }

        boolean exposed = false;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block adjacent = target.getRelative(face);
            if (adjacent.getType().isAir()) exposed = true;
        }
        if (!exposed) return false;

        return !hasTileStateInNeighborhood(target);
    }

    private boolean hasTileStateInNeighborhood(Block target) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (target.getRelative(dx, dy, dz).getState() instanceof TileState) return true;
                }
            }
        }
        return false;
    }

    private boolean unlockCache(Chest chest) {
        chest.getPersistentDataContainer().remove(lockedCacheKey);
        if (chest.update(true, false)) return true;
        chest.getPersistentDataContainer().set(lockedCacheKey, PersistentDataType.BYTE, (byte) 1);
        return false;
    }

    private boolean isKey(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(cacheKeyItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean consumeKey(Player player) {
        var inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!isKey(item)) continue;
            if (item.getAmount() > 1) {
                ItemStack remaining = item.clone();
                remaining.setAmount(item.getAmount() - 1);
                inventory.setItem(slot, remaining);
            } else {
                inventory.setItem(slot, null);
            }
            return true;
        }

        ItemStack offhand = inventory.getItemInOffHand();
        if (isKey(offhand)) {
            if (offhand.getAmount() > 1) {
                ItemStack remaining = offhand.clone();
                remaining.setAmount(offhand.getAmount() - 1);
                inventory.setItemInOffHand(remaining);
            } else {
                inventory.setItemInOffHand(new ItemStack(Material.AIR));
            }
            return true;
        }
        return false;
    }

    private Chest findLockedCache(InventoryHolder holder) {
        if (holder instanceof Chest chest && isLockedCache(chest)) return chest;
        if (holder instanceof DoubleChest doubleChest) {
            Chest left = findChest(doubleChest.getLeftSide());
            if (left != null && isLockedCache(left)) return left;
            Chest right = findChest(doubleChest.getRightSide());
            if (right != null && isLockedCache(right)) return right;
        }
        return null;
    }

    private Chest findChest(InventoryHolder holder) {
        return holder instanceof Chest chest ? chest : null;
    }

    private boolean isLockedCache(Block block) {
        return block != null && block.getType() == Material.CHEST
                && block.getState() instanceof Chest chest && isLockedCache(chest);
    }

    private boolean isLockedCache(Chest chest) {
        return chest != null
                && chest.getPersistentDataContainer().has(lockedCacheKey, PersistentDataType.BYTE);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
