package cz.halloween.core;

import org.bukkit.Chunk;
import org.bukkit.entity.Display;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
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
    private final Queue<Chunk> pending = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> warnedErrors = new HashSet<>();
    private BukkitTask task;

    public HalloweenWorldDecorator(HalloweenCore plugin) {
        this.plugin = plugin;
        this.decoratedKey = new NamespacedKey(plugin, "halloween_decorated_v4");
        this.previousDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v3");
        this.legacyDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v2");
        this.oldestDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v1");
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
                || chunk.getPersistentDataContainer().has(oldestDecoratedKey, PersistentDataType.BYTE);
        int pumpkins = legacyDecorated ? 0 : bounded("pumpkins-per-chunk", 5, 0, 6);
        int webs = legacyDecorated ? 0 : bounded("cobwebs-per-chunk", 2, 0, 3);
        int candles = bounded("red-candles-per-chunk", 1, 0, 3);
        int graves = bounded("gravestones-per-chunk", 2, 0, 4);
        int deadPlants = bounded("dead-plants-per-chunk", 3, 0, 6);
        int soulLights = bounded("soul-lights-per-chunk", 1, 0, 2);
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
            if (placeGravestone(foot, head, random)) placed++;
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

    private boolean placeGravestone(Block foot, Block head, Random random) {
        String itemId = random.nextBoolean()
                ? "warriorland_halloween:grave_marker"
                : "warriorland_halloween:grave_marker_cracked";
        ItemStack marker = plugin.getItemManager() == null
                ? null : plugin.getItemManager().getItemsAdderItem(itemId, 1);

        if (marker == null) {
            // Safe fallback for servers where ItemsAdder is not installed or not ready.
            foot.setType(Material.POLISHED_BLACKSTONE_BRICK_WALL, false);
            head.setType(random.nextBoolean() ? Material.POLISHED_BLACKSTONE_BRICK_WALL : Material.COBBLESTONE_WALL, false);
            return true;
        }

        ItemDisplay display = null;
        try {
            Location location = new Location(foot.getWorld(),
                    foot.getX() + 0.5D, foot.getY() + 0.65D, foot.getZ() + 0.5D);
            display = foot.getWorld().spawn(location, ItemDisplay.class);
            display.setItemStack(marker);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new AxisAngle4f(),
                    new Vector3f(2.4F, 2.4F, 1.0F),
                    new AxisAngle4f()));
            display.setPersistent(true);
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setSilent(true);
            return true;
        } catch (RuntimeException ex) {
            if (display != null && display.isValid()) display.remove();
            // Do not leave a blank grave if the server implementation rejects displays.
            foot.setType(Material.POLISHED_BLACKSTONE_BRICK_WALL, false);
            head.setType(random.nextBoolean() ? Material.POLISHED_BLACKSTONE_BRICK_WALL : Material.COBBLESTONE_WALL, false);
            String warningKey = "gravestone-display|" + ex.getClass().getName();
            if (warnedErrors.add(warningKey)) {
                plugin.getLogger().warning("Custom Halloween gravestone display is unavailable; using vanilla wall markers instead ("
                        + ex.getClass().getSimpleName() + ").");
            }
            return true;
        }
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
