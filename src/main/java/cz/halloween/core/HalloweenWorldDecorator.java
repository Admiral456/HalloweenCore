package cz.halloween.core;

import org.bukkit.Chunk;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/**
 * Adds restrained, natural-looking Halloween details to safe ground as chunks load.
 * Existing blocks and tile entities are never replaced. A per-chunk marker prevents
 * decoration from being repeatedly stacked after relogs or restarts.
 */
public final class HalloweenWorldDecorator implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey decoratedKey;
    private final NamespacedKey previousDecoratedKey;
    private final NamespacedKey legacyDecoratedKey;
    private final NamespacedKey oldestDecoratedKey;
    private final NamespacedKey ancientDecoratedKey;
    private final NamespacedKey graveAccentKey;
    private final Queue<Chunk> pending = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> warnedErrors = new HashSet<>();
    private BukkitTask task;

    public HalloweenWorldDecorator(HalloweenCore plugin) {
        this.plugin = plugin;
        this.decoratedKey = new NamespacedKey(plugin, "halloween_decorated_v5");
        this.previousDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v4");
        this.legacyDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v3");
        this.oldestDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v2");
        this.ancientDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v1");
        this.graveAccentKey = new NamespacedKey(plugin, "halloween_grave_accent");
    }

    public void start() {
        if (task != null || !isEnabled()) return;
        scanLoadedChunks();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        pending.clear();
        queued.clear();
    }

    /**
     * Resume background processing without re-queuing every chunk already loaded.
     * Hot reload must not replay decorations across the entire visible world in one session.
     */
    public void resumeWithoutScanning() {
        if (task != null || !isEnabled()) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 10L);
    }

    public void scanLoadedChunks() {
        if (!isEnabled()) return;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) enqueue(chunk);
        }
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 10L);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (isEnabled()) enqueue(event.getChunk());
    }

    private boolean isEnabled() {
        return plugin.isEventEnabled() && plugin.getConfig().getBoolean("world-decorations.enabled", true);
    }

    private void enqueue(Chunk chunk) {
        if (chunk == null || chunk.getPersistentDataContainer().has(decoratedKey, PersistentDataType.BYTE)) return;
        String id = chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
        if (queued.add(id)) pending.offer(chunk);
    }

    private void processQueue() {
        if (!isEnabled()) {
            pending.clear();
            queued.clear();
            return;
        }
        int budget = Math.max(1, Math.min(3, plugin.getConfig().getInt("world-decorations.chunks-per-tick", 1)));
        for (int i = 0; i < budget; i++) {
            Chunk chunk = pending.poll();
            if (chunk == null) return;
            queued.remove(chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ());
            if (!chunk.isLoaded() || chunk.getPersistentDataContainer().has(decoratedKey, PersistentDataType.BYTE)) continue;
            try {
                decorateChunk(chunk);
                chunk.getPersistentDataContainer().set(decoratedKey, PersistentDataType.BYTE, (byte) 1);
            } catch (RuntimeException ex) {
                String warningKey = ex.getClass().getName();
                if (warnedErrors.add(warningKey)) {
                    plugin.getLogger().warning("Halloween world decoration encountered " + warningKey
                            + " at chunk " + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ()
                            + ". Further errors of this type are suppressed.");
                }
            }
        }
    }

    private void decorateChunk(Chunk chunk) {
        boolean legacyDecorated = chunk.getPersistentDataContainer().has(previousDecoratedKey, PersistentDataType.BYTE)
                || chunk.getPersistentDataContainer().has(legacyDecoratedKey, PersistentDataType.BYTE)
                || chunk.getPersistentDataContainer().has(oldestDecoratedKey, PersistentDataType.BYTE)
                || chunk.getPersistentDataContainer().has(ancientDecoratedKey, PersistentDataType.BYTE);
        // Legacy flat gravestones are removed only after at least one replacement was
        // successfully built, so unsuitable chunks never lose their existing decoration.

        // Legacy chunks are upgraded with 3D graves/mushrooms only; don't duplicate
        // old pumpkins, webs, candles, lights or small mushrooms on their new pass.
        int pumpkins = legacyDecorated ? 0 : bounded("pumpkins-per-chunk", 5, 0, 6);
        int webs = legacyDecorated ? 0 : bounded("cobwebs-per-chunk", 2, 0, 3);
        int candles = legacyDecorated ? 0 : bounded("red-candles-per-chunk", 1, 0, 3);
        int graves = bounded("gravestones-per-chunk", 1, 0, 2);
        int deadPlants = legacyDecorated ? 0 : bounded("dead-plants-per-chunk", 1, 0, 3);
        int largeMushrooms = bounded("large-mushrooms-per-chunk", 1, 0, 1);
        int soulLights = legacyDecorated ? 0 : bounded("soul-lights-per-chunk", 1, 0, 2);
        int attempts = bounded("attempts-per-placement", 32, 8, 80);
        long seed = chunk.getWorld().getSeed() ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L) ^ 0x48414C4C4F574545L;
        Random random = new Random(seed);

        int placed = 0;
        for (int i = 0; i < attempts && placed < pumpkins; i++) {
            Block ground = randomSurface(chunk, random);
            if (!isNaturalGround(ground.getType()) || !ground.getRelative(0, 1, 0).getType().isAir()
                    || ground.getState() instanceof TileState || !naturalNeighborhood(ground)) continue;
            Material pumpkin = switch (random.nextInt(5)) {
                case 0 -> Material.JACK_O_LANTERN;
                case 1 -> Material.CARVED_PUMPKIN;
                default -> Material.PUMPKIN;
            };
            ground.getRelative(0, 1, 0).setType(pumpkin, false);
            placed++;
        }

        placed = 0;
        for (int i = 0; i < attempts && placed < webs; i++) {
            Block ground = randomSurface(chunk, random);
            if (!isNaturalWebAnchor(ground.getType())) continue;
            Block air = ground.getRelative(0, 1, 0);
            if (!air.getType().isAir() || air.getState() instanceof TileState || countNearbyWebAnchors(ground) < 2) continue;
            air.setType(Material.COBWEB, false);
            placed++;
        }

        placed = 0;
        for (int i = 0; i < attempts && placed < candles; i++) {
            Block ground = randomSurface(chunk, random);
            Block air = ground.getRelative(0, 1, 0);
            if (!isNaturalGround(ground.getType()) || !ground.getType().isSolid() || !naturalNeighborhood(ground)
                    || ground.getState() instanceof TileState || !air.getType().isAir()
                    || air.getState() instanceof TileState) continue;
            air.setType(Material.RED_CANDLE, false);
            placed++;
        }

        // ItemDisplay gravestones use a dedicated pixel-art ItemsAdder texture.
        // Older worlds keep their pumpkins/webs but receive the new grave accents once.
        placed = 0;
        for (int i = 0; i < attempts && placed < graves; i++) {
            Block ground = randomSurface(chunk, random);
            Block foot = ground.getRelative(0, 1, 0);
            Block head = ground.getRelative(0, 2, 0);
            if (!isNaturalGround(ground.getType()) || !ground.getType().isSolid() || !naturalNeighborhood(ground)
                    || ground.getState() instanceof TileState || !foot.getType().isAir() || !head.getType().isAir()
                    || foot.getState() instanceof TileState || head.getState() instanceof TileState) continue;
            if (placeGravestone(ground, random)) placed++;
        }
        if (legacyDecorated && placed > 0) removeLegacyFlatGraveDisplays(chunk);

        // Add actual 3D mushrooms formed from mushroom-stem and mushroom-cap blocks.
        // The seasonal pack changes their cap textures, but the geometry is real blocks.
        placed = 0;
        for (int i = 0; i < attempts && placed < largeMushrooms; i++) {
            Block ground = randomSurface(chunk, random);
            if (!isNaturalGround(ground.getType()) || !ground.getType().isSolid()
                    || ground.getState() instanceof TileState || !naturalNeighborhood(ground)) continue;
            if (placeLargeMushroom(ground, random)) placed++;
        }

        // Dead vegetation only appears where the vanilla block can safely sit; no terrain is removed.
        placed = 0;
        for (int i = 0; i < attempts && placed < deadPlants; i++) {
            Block ground = randomSurface(chunk, random);
            Block air = ground.getRelative(0, 1, 0);
            if (!air.getType().isAir() || air.getState() instanceof TileState || ground.getState() instanceof TileState) continue;
            Material plant;
            if (ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND) {
                plant = Material.DEAD_BUSH;
            } else if (isNaturalGround(ground.getType()) && ground.getType().isSolid()) {
                plant = random.nextBoolean() ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
            } else {
                continue;
            }
            air.setType(plant, false);
            placed++;
        }

        placed = 0;
        for (int i = 0; i < attempts && placed < soulLights; i++) {
            Block ground = randomSurface(chunk, random);
            Block air = ground.getRelative(0, 1, 0);
            if (!isNaturalGround(ground.getType()) || !ground.getType().isSolid() || !naturalNeighborhood(ground)
                    || ground.getState() instanceof TileState || !air.getType().isAir()
                    || air.getState() instanceof TileState) continue;
            air.setType(Material.SOUL_LANTERN, false);
            placed++;
        }
    }

    private record BlockPlacement(int dx, int dy, int dz, Material material) {}

    /**
     * Remove only legacy ItemDisplays that used the whole flat gravestone sprite.
     * The current grave is built from real blocks, with a small rune icon used only as an accent.
     */
    private void removeLegacyFlatGraveDisplays(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof ItemDisplay display)) continue;
            ItemStack stack = display.getItemStack();
            if (stack == null || stack.getType() != Material.PAPER || !stack.hasItemMeta()) continue;
            ItemMeta meta = stack.getItemMeta();
            if (meta == null || !meta.hasDisplayName()) continue;
            String name = ChatColor.stripColor(meta.getDisplayName());
            if (name == null) continue;
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.contains("náhrobek prokletého hřbitova")
                    || normalized.contains("rozpraskaný náhrobek")) {
                display.remove();
            }
        }
    }

    private boolean placeGravestone(Block ground, Random random) {
        boolean cracked = random.nextBoolean();
        Material baseSlab = cracked ? Material.DEEPSLATE_BRICK_SLAB : Material.POLISHED_BLACKSTONE_BRICK_SLAB;
        Material stone = cracked ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
        Material face = cracked ? Material.CRACKED_DEEPSLATE_BRICKS : Material.CHISELED_POLISHED_BLACKSTONE;
        Material sideWall = cracked ? Material.COBBLESTONE_WALL : Material.POLISHED_BLACKSTONE_BRICK_WALL;

        List<BlockPlacement> plan = new ArrayList<>();
        // Low six-block plinth makes the grave look grounded rather than like a floating icon.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                plan.add(new BlockPlacement(dx, 1, dz, baseSlab));
            }
        }
        // A real stone cross silhouette, a carved face, side braces and a capstone.
        plan.add(new BlockPlacement(0, 2, 0, stone));
        plan.add(new BlockPlacement(0, 3, 0, face));
        plan.add(new BlockPlacement(-1, 3, 0, stone));
        plan.add(new BlockPlacement(1, 3, 0, stone));
        plan.add(new BlockPlacement(0, 4, 0, stone));
        plan.add(new BlockPlacement(0, 5, 0, Material.POLISHED_BLACKSTONE_BRICK_SLAB));
        plan.add(new BlockPlacement(-1, 2, 1, sideWall));
        plan.add(new BlockPlacement(1, 2, 1, sideWall));

        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        // Require a flat 3x2 patch of untouched natural ground. Never carve or replace it.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                Block support = ground.getRelative(dx, 0, dz);
                if (!isNaturalGround(support.getType()) || support.getState() instanceof TileState
                        || support.getWorld().getHighestBlockYAt(x + dx, z + dz) != y) return false;
            }
        }
        for (BlockPlacement placement : plan) {
            Block target = ground.getRelative(placement.dx(), placement.dy(), placement.dz());
            if (!target.getType().isAir() || target.getState() instanceof TileState) return false;
        }

        for (BlockPlacement placement : plan) {
            ground.getRelative(placement.dx(), placement.dy(), placement.dz()).setType(placement.material(), false);
        }

        // Small custom rune on the front is decorative; the headstone itself is physical geometry.
        String itemId = cracked
                ? "warriorland_halloween:grave_marker_cracked"
                : "warriorland_halloween:grave_marker";
        ItemStack marker = plugin.getItemManager() == null
                ? null : plugin.getItemManager().getItemsAdderItem(itemId, 1);
        if (marker != null) {
            ItemDisplay display = null;
            try {
                Location location = new Location(ground.getWorld(), x + 0.5D, y + 3.5D, z + 1.035D);
                display = ground.getWorld().spawn(location, ItemDisplay.class);
                display.setItemStack(marker);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                display.setBillboard(Display.Billboard.VERTICAL);
                display.setTransformation(new Transformation(
                        new Vector3f(0.0F, 0.0F, 0.0F),
                        new AxisAngle4f(),
                        new Vector3f(0.48F, 0.48F, 0.12F),
                        new AxisAngle4f()));
                display.setPersistent(true);
                display.setGravity(false);
                display.setInvulnerable(true);
                display.setSilent(true);
                display.getPersistentDataContainer().set(graveAccentKey, PersistentDataType.BYTE, (byte) 1);
            } catch (RuntimeException ex) {
                if (display != null && display.isValid()) display.remove();
                String warningKey = "gravestone-accent|" + ex.getClass().getName();
                if (warnedErrors.add(warningKey)) {
                    plugin.getLogger().warning("Halloween 3D gravestones were built without the optional rune accent ("
                            + ex.getClass().getSimpleName() + ").");
                }
            }
        }
        return true;
    }

    private boolean placeLargeMushroom(Block ground, Random random) {
        boolean red = random.nextBoolean();
        Material cap = red ? Material.RED_MUSHROOM_BLOCK : Material.BROWN_MUSHROOM_BLOCK;
        List<BlockPlacement> plan = new ArrayList<>();
        plan.add(new BlockPlacement(0, 1, 0, Material.MUSHROOM_STEM));
        plan.add(new BlockPlacement(0, 2, 0, Material.MUSHROOM_STEM));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                plan.add(new BlockPlacement(dx, 3, dz, cap));
            }
        }
        plan.add(new BlockPlacement(0, 4, 0, cap));

        int x = ground.getX();
        int y = ground.getY();
        int z = ground.getZ();
        // A level natural 3x3 patch keeps the mushroom rooted and avoids building over paths,
        // houses, chest blocks, or sloped terrain.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block support = ground.getRelative(dx, 0, dz);
                if (!isNaturalGround(support.getType()) || support.getState() instanceof TileState
                        || support.getWorld().getHighestBlockYAt(x + dx, z + dz) != y) return false;
            }
        }
        for (BlockPlacement placement : plan) {
            Block target = ground.getRelative(placement.dx(), placement.dy(), placement.dz());
            if (!target.getType().isAir() || target.getState() instanceof TileState) return false;
        }
        for (BlockPlacement placement : plan) {
            ground.getRelative(placement.dx(), placement.dy(), placement.dz()).setType(placement.material(), false);
        }
        return true;
    }

    private int bounded(String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, plugin.getConfig().getInt("world-decorations." + key, fallback)));
    }

    private Block randomSurface(Chunk chunk, Random random) {
        int x = (chunk.getX() << 4) + random.nextInt(16);
        int z = (chunk.getZ() << 4) + random.nextInt(16);
        return chunk.getWorld().getHighestBlockAt(x, z);
    }

    private boolean naturalNeighborhood(Block ground) {
        int natural = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (isNaturalGround(ground.getRelative(dx, 0, dz).getType())) natural++;
            }
        }
        return natural >= 7;
    }

    private int countNearbyWebAnchors(Block center) {
        int anchors = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block neighbor = center.getRelative(dx, 0, dz);
                if (isNaturalWebAnchor(neighbor.getType())
                        || isNaturalWebAnchor(neighbor.getRelative(0, -1, 0).getType())) anchors++;
            }
        }
        return anchors;
    }

    private boolean isNaturalGround(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MYCELIUM,
                    MOSS_BLOCK, MUD, MUDDY_MANGROVE_ROOTS, CLAY, MOSSY_COBBLESTONE,
                    STONE, ANDESITE, DIORITE, GRANITE, DEEPSLATE, TUFF, CALCITE,
                    SAND, RED_SAND, GRAVEL, SNOW_BLOCK, NETHERRACK, SOUL_SAND, SOUL_SOIL,
                    CRIMSON_NYLIUM, WARPED_NYLIUM, BASALT, BLACKSTONE, END_STONE -> true;
            default -> false;
        };
    }

    private boolean isNaturalWebAnchor(Material material) {
        String name = material.name();
        return material.isSolid() && (name.endsWith("_LEAVES") || name.endsWith("_LOG")
                || name.endsWith("_WOOD") || name.endsWith("_STONE") || name.endsWith("_DEEPSLATE")
                || material == Material.MOSS_BLOCK || material == Material.MOSSY_COBBLESTONE);
    }
}
