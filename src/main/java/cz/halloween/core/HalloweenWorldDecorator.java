package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
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
import java.util.UUID;

/**
 * Adds small, natural-looking Halloween details to safe ground as chunks load.
 * It never replaces a non-air block, never touches block entities, and stores a
 * per-chunk marker to avoid repeated work on every login/restart.
 */
public final class HalloweenWorldDecorator implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey decoratedKey;
    private final NamespacedKey legacyDecoratedKey;
    private final Queue<Chunk> pending = new ArrayDeque<>();
    private final Set<String> queued = new HashSet<>();
    private final Set<String> warnedErrors = new HashSet<>();
    private BukkitTask task;

    public HalloweenWorldDecorator(HalloweenCore plugin) {
        this.plugin = plugin;
        this.decoratedKey = new NamespacedKey(plugin, "halloween_decorated_v2");
        this.legacyDecoratedKey = new NamespacedKey(plugin, "halloween_decorated_v1");
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
        return plugin.isEventEnabled()
                && plugin.getConfig().getBoolean("world-decorations.enabled", true);
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
        int budget = Math.max(1, Math.min(4, plugin.getConfig().getInt("world-decorations.chunks-per-tick", 2)));
        for (int i = 0; i < budget; i++) {
            Chunk chunk = pending.poll();
            if (chunk == null) return;
            queued.remove(chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ());
            if (!chunk.isLoaded()) continue;
            if (chunk.getPersistentDataContainer().has(decoratedKey, PersistentDataType.BYTE)) continue;
            try {
                decorateChunk(chunk);
                chunk.getPersistentDataContainer().set(decoratedKey, PersistentDataType.BYTE, (byte) 1);
            } catch (RuntimeException ex) {
                String warningKey = ex.getClass().getName();
                if (warnedErrors.add(warningKey)) {
                    plugin.getLogger().warning("Halloween world decoration encountered " + warningKey
                            + " at chunk " + chunk.getWorld().getName() + " "
                            + chunk.getX() + "," + chunk.getZ()
                            + ". Further errors of this type are suppressed to keep the console readable.");
                }
            }
        }
    }

    private void decorateChunk(Chunk chunk) {
        // Existing decorated chunks get only the new accent layer. Avoid stacking
        // a second batch of pumpkins/cobwebs when upgrading an already-running world.
        boolean legacyDecorated = chunk.getPersistentDataContainer().has(legacyDecoratedKey, PersistentDataType.BYTE);
        int pumpkins = legacyDecorated ? 0 : Math.max(0, Math.min(6,
                plugin.getConfig().getInt("world-decorations.pumpkins-per-chunk", 5)));
        int webs = legacyDecorated ? 0 : Math.max(0, Math.min(3,
                plugin.getConfig().getInt("world-decorations.cobwebs-per-chunk", 2)));
        int candles = Math.max(0, Math.min(3, plugin.getConfig().getInt("world-decorations.red-candles-per-chunk", 1)));
        int attempts = Math.max(8, Math.min(80, plugin.getConfig().getInt("world-decorations.attempts-per-placement", 32)));
        long seed = chunk.getWorld().getSeed() ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L) ^ 0x48414C4C4F574545L;
        Random random = new Random(seed);
        int pumpkinCount = 0;
        for (int i = 0; i < attempts && pumpkinCount < pumpkins; i++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block ground = chunk.getWorld().getHighestBlockAt(x, z);
            if (!isNaturalGround(ground.getType())) continue;
            if (!(ground.getRelative(0, 1, 0).getType().isAir())) continue;
            if (ground.getState() instanceof TileState) continue;
            if (!naturalNeighborhood(ground)) continue;

            Material pumpkin = switch (random.nextInt(5)) {
                case 0 -> Material.JACK_O_LANTERN;
                case 1 -> Material.CARVED_PUMPKIN;
                default -> Material.PUMPKIN;
            };
            ground.getRelative(0, 1, 0).setType(pumpkin, false);
            pumpkinCount++;
        }

        int webCount = 0;
        for (int i = 0; i < attempts && webCount < webs; i++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block surface = chunk.getWorld().getHighestBlockAt(x, z);
            if (!isNaturalWebAnchor(surface.getType())) continue;
            Block air = surface.getRelative(0, 1, 0);
            if (!air.getType().isAir() || air.getY() <= chunk.getWorld().getMinHeight()) continue;
            if (air.getState() instanceof TileState) continue;
            if (countNearbyWebAnchors(surface) < 2) continue;
            air.setType(Material.COBWEB, false);
            webCount++;
        }

        int candleCount = 0;
        for (int i = 0; i < attempts && candleCount < candles; i++) {
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            Block ground = chunk.getWorld().getHighestBlockAt(x, z);
            if (!isNaturalGround(ground.getType()) || !ground.getType().isSolid()) continue;
            if (!naturalNeighborhood(ground) || ground.getState() instanceof TileState) continue;
            Block air = ground.getRelative(0, 1, 0);
            if (!air.getType().isAir() || air.getState() instanceof TileState) continue;
            air.setType(Material.RED_CANDLE, false);
            candleCount++;
        }
    }

    private boolean naturalNeighborhood(Block ground) {
        int natural = 0;
        int checked = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = ground.getRelative(dx, 0, dz);
                checked++;
                if (isNaturalGround(block.getType())) natural++;
            }
        }
        return natural >= Math.min(7, checked);
    }

    private int countNearbyWebAnchors(Block center) {
        int anchors = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block neighbor = center.getRelative(dx, 0, dz);
                if (isNaturalWebAnchor(neighbor.getType())
                        || isNaturalWebAnchor(neighbor.getRelative(0, -1, 0).getType())) {
                    anchors++;
                }
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
