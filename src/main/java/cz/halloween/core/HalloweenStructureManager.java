package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;

/**
 * Procedural, deterministic structures for explicit administrator commands.
 * Automatic world decoration is handled separately with a strict world allow-list.
 */
public final class HalloweenStructureManager implements Listener {
    private record Pos(int x, int y, int z) {}
    private record Change(Pos pos, BlockData before, BlockData after) {}

    private static final int MAX_TERRAIN_DELTA = 32;

    private final HalloweenCore plugin;
    private final NamespacedKey naturalChunkKey;
    private final NamespacedKey naturalStructurePlacedKey;
    private final Set<BuildJob> activeJobs = new LinkedHashSet<>();
    private boolean suppressAutomaticGeneration;

    private static final class BuildJob {
        final Player player;
        final World world;
        final String type;
        final int centerX;
        final int centerY;
        final int centerZ;
        final List<Change> changes;
        int cursor;
        int applied;
        BukkitTask task;

        BuildJob(Player player, World world, String type, int centerX, int centerY, int centerZ,
                 List<Change> changes) {
            this.player = player;
            this.world = world;
            this.type = type;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.changes = changes;
        }
    }

    public HalloweenStructureManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.naturalChunkKey = new NamespacedKey(plugin, "halloween_natural_structure_checked_v1");
        this.naturalStructurePlacedKey = new NamespacedKey(plugin, "halloween_natural_structure_placed_v1");
    }

    /** Only brand-new chunks in explicitly allowed worlds are candidates for automatic structures. */
    @EventHandler
    public void onNewChunk(ChunkLoadEvent event) {
        if (suppressAutomaticGeneration || !event.isNewChunk() || !plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("world-generation.enabled", true)) return;
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        if (world == null || "Spawn".equalsIgnoreCase(world.getName())
                || !plugin.isEligibleGameplayWorld(world)
                || !plugin.getConfig().getStringList("world-generation.allowed-worlds").stream()
                    .anyMatch(name -> name.equalsIgnoreCase(world.getName()))) return;
        List<String> allowedBiomes = plugin.getConfig().getStringList("world-generation.allowed-biomes");
        if (allowedBiomes.isEmpty()) {
            allowedBiomes = List.of("PLAINS", "FOREST", "DARK_FOREST", "TAIGA",
                    "OLD_GROWTH_PINE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA", "MEADOW");
        }
        if (chunk.getPersistentDataContainer().has(naturalChunkKey, PersistentDataType.BYTE)) return;

        long seed = world.getSeed() ^ ((long) chunk.getX() * 341873128712L)
                ^ ((long) chunk.getZ() * 132897987541L) ^ 0x48414C4C53545255L;
        Random random = new Random(seed);
        int cemeteryDenominator = Math.max(1, Math.min(100000, plugin.getConfig().getInt("world-generation.cemetery-chance-denominator", 256)));
        int villageDenominator = Math.max(1, Math.min(100000, plugin.getConfig().getInt("world-generation.village-chance-denominator", 512)));
        int cx = (chunk.getX() << 4) + 8;
        int cz = (chunk.getZ() << 4) + 8;
        try {
            String biome = world.getBiome(cx, baseSurfaceY(world, cx, cz), cz).name();
            if (allowedBiomes.stream().noneMatch(name -> name.equalsIgnoreCase(biome))) return;
            if (random.nextInt(cemeteryDenominator) == 0) {
                buildNaturalCemetery(chunk, random);
            } else if (random.nextInt(villageDenominator) == 0) {
                buildNaturalVillage(chunk, random);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Natural Halloween structure skipped in " + world.getName() + " chunk "
                    + chunk.getX() + "," + chunk.getZ() + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            chunk.getPersistentDataContainer().set(naturalChunkKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private void buildNaturalCemetery(Chunk chunk, Random random) {
        int cx = (chunk.getX() << 4) + 8;
        int cz = (chunk.getZ() << 4) + 8;
        int baseY = findGroundY(chunk.getWorld(), cx, cz);
        if (!isNaturalFlatFootprint(chunk, cx, cz, 5, baseY)) return;
        World world = chunk.getWorld();
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            Block ground = world.getBlockAt(cx + dx, baseY, cz + dz);
            ground.setType((Math.max(Math.abs(dx), Math.abs(dz)) == 5) ? Material.MOSSY_COBBLESTONE : Material.POLISHED_DEEPSLATE, false);
            if (Math.max(Math.abs(dx), Math.abs(dz)) == 5 && !(Math.abs(dx) <= 1 && dz == -5)) {
                world.getBlockAt(cx + dx, baseY + 1, cz + dz).setType(Material.MOSSY_COBBLESTONE_WALL, false);
            }
        }
        for (int gx : new int[]{-3, 0, 3}) for (int gz : new int[]{-2, 2}) {
            world.getBlockAt(cx + gx, baseY + 1, cz + gz).setType(Material.COBBLESTONE_WALL, false);
            if (random.nextBoolean()) world.getBlockAt(cx + gx, baseY + 2, cz + gz).setType(Material.POLISHED_BLACKSTONE_BRICK_WALL, false);
        }
        world.getBlockAt(cx, baseY + 1, cz - 5).setType(Material.SOUL_LANTERN, false);
        chunk.getPersistentDataContainer().set(naturalStructurePlacedKey, PersistentDataType.BYTE, (byte) 1);
        plugin.getLogger().info("Generated natural Halloween cemetery in " + world.getName() + " at " + cx + "," + baseY + "," + cz);
    }

    private void buildNaturalVillage(Chunk chunk, Random random) {
        int cx = (chunk.getX() << 4) + 8;
        int cz = (chunk.getZ() << 4) + 8;
        int baseY = findGroundY(chunk.getWorld(), cx, cz);
        if (!isNaturalFlatFootprint(chunk, cx, cz, 7, baseY)) return;
        World world = chunk.getWorld();
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            Material floor = (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) ? Material.DIRT_PATH : Material.MUD_BRICKS;
            world.getBlockAt(cx + dx, baseY, cz + dz).setType(floor, false);
        }
        // Four tiny cabins around a central lantern well; the manual command creates the full-size version.
        for (int hx : new int[]{-4, 4}) for (int hz : new int[]{-4, 4}) {
            Material plank = random.nextBoolean() ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + hx + dx, baseY, cz + hz + dz).setType(plank, false);
                world.getBlockAt(cx + hx + dx, baseY + 1, cz + hz + dz).setType(
                        (Math.abs(dx) == 1 || Math.abs(dz) == 1) ? Material.MOSSY_COBBLESTONE : Material.AIR, false);
                world.getBlockAt(cx + hx + dx, baseY + 2, cz + hz + dz).setType(
                        (Math.abs(dx) == 1 || Math.abs(dz) == 1) ? Material.MOSSY_COBBLESTONE : Material.AIR, false);
                world.getBlockAt(cx + hx + dx, baseY + 3, cz + hz + dz).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }
        world.getBlockAt(cx, baseY + 1, cz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        world.getBlockAt(cx, baseY + 2, cz).setType(Material.SOUL_LANTERN, false);
        chunk.getPersistentDataContainer().set(naturalStructurePlacedKey, PersistentDataType.BYTE, (byte) 1);
        plugin.getLogger().info("Generated natural Halloween village in " + world.getName() + " at " + cx + "," + baseY + "," + cz);
    }

    private int baseSurfaceY(World world, int x, int z) {
        return Math.max(world.getMinHeight(), world.getHighestBlockYAt(x, z));
    }

    private boolean isNaturalFlatFootprint(Chunk chunk, int cx, int cz, int radius, int expectedY) {
        World world = chunk.getWorld();
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            if ((x >> 4) != chunk.getX() || (z >> 4) != chunk.getZ()) return false;
            int groundY = findGroundY(world, x, z);
            if (groundY != expectedY || world.getHighestBlockYAt(x, z) != groundY) return false;
            Material ground = world.getBlockAt(x, groundY, z).getType();
            if (!(ground == Material.GRASS_BLOCK || ground == Material.DIRT || ground == Material.COARSE_DIRT
                    || ground == Material.PODZOL || ground == Material.MYCELIUM || ground == Material.MOSS_BLOCK)) return false;
            if (world.getBlockAt(x, groundY + 1, z).getType() != Material.AIR
                    || world.getBlockAt(x, groundY + 2, z).getType() != Material.AIR) return false;
        }
        return expectedY + 5 < world.getMaxHeight();
    }

    /**
     * Starts a manual FORCE build at a point in front of the invoking player.
     * This intentionally does not apply automatic-generation world restrictions.
     */
    public boolean buildBeforePlayer(Player player, String requestedType) {
        if (player == null || !player.isOnline()) return false;
        String type = normalizeType(requestedType);
        if (type == null) {
            player.sendMessage(plugin.color("&cNeznámá struktura. Použij hřbitov, vesnice, aréna, vampirearena nebo goblinarena."));
            return false;
        }
        if (activeJobs.stream().anyMatch(job -> job.world.equals(player.getWorld()))) {
            player.sendMessage(plugin.color("&eV tomto světě už probíhá stavba jiné Halloween struktury. Počkej na její dokončení."));
            return false;
        }

        int radius = radius(type);
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().setY(0);
        if (direction.lengthSquared() < 0.0001D) {
            direction = new Vector(0, 0, 1);
        } else {
            direction.normalize();
        }

        int centerX = (int) Math.floor(origin.getX() + direction.getX() * (radius + 4) + 0.5D);
        int centerZ = (int) Math.floor(origin.getZ() + direction.getZ() * (radius + 4) + 0.5D);
        int rotation = HalloweenStructureGeometry.rotationFromYaw(origin.getYaw());

        World world = player.getWorld();
        int baseY;
        Map<Pos, Material> plan;
        try {
            // Load only chunks covered by this manually requested structure.
            suppressAutomaticGeneration = true;
            try {
                int minChunkX = (centerX - radius) >> 4;
                int maxChunkX = (centerX + radius) >> 4;
                int minChunkZ = (centerZ - radius) >> 4;
                int maxChunkZ = (centerZ + radius) >> 4;
                for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                    for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                        world.getChunkAt(chunkX, chunkZ);
                    }
                }
            } finally {
                suppressAutomaticGeneration = false;
            }
            int centerSurfaceY = world.getHighestBlockYAt(centerX, centerZ);
            Block centerSurface = world.getBlockAt(centerX, centerSurfaceY, centerZ);
            // If the command points into shallow water/lava, build at the surface and add
            // foundations inside the footprint rather than accidentally placing the arena underground.
            baseY = centerSurface.isLiquid() ? centerSurfaceY : findGroundY(world, centerX, centerZ);
            if (baseY <= world.getMinHeight() + 1 || baseY + clearance(type) >= world.getMaxHeight()) {
                player.sendMessage(plugin.color("&cTady není dost místa ve výšce světa pro tuto stavbu. Zkus se postavit na běžný terén a znovu zadat příkaz."));
                return false;
            }
            plan = createPlan(world, type, centerX, centerZ, baseY, rotation);
        } catch (IllegalArgumentException ex) {
            player.sendMessage(plugin.color("&cStavbu nelze bezpečně připravit: &7" + ex.getMessage()));
            return false;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Halloween structure preflight failed for " + type + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            player.sendMessage(plugin.color("&cKontrola prostoru selhala ještě před změnou bloků. Nic nebylo postaveno."));
            return false;
        }

        int hardLimit = Math.max(1000, Math.min(180000,
                plugin.getConfig().getInt("structures.max-block-changes", 150000)));
        if (plan.size() > hardLimit) {
            player.sendMessage(plugin.color("&cStavba by změnila " + plan.size() + " pozic, což překračuje bezpečný limit "
                    + hardLimit + ". Nebyly změněny žádné bloky."));
            return false;
        }

        List<Change> changes = new ArrayList<>(plan.size());
        try {
            for (Map.Entry<Pos, Material> entry : plan.entrySet()) {
                Pos pos = entry.getKey();
                Material wanted = entry.getValue();
                Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
                if (block.getType() == wanted) continue;
                BlockState state = block.getState();
                if (state instanceof TileState) {
                    String where = pos.x() + " " + pos.y() + " " + pos.z();
                    player.sendMessage(plugin.color("&cV půdorysu je chráněný blok s uloženými daty (" + block.getType()
                            + ") na &f" + where + "&c. Stavba byla zastavena před první změnou, aby nezničila obsah truhly nebo jiná uložená data."));
                    plugin.getLogger().warning("Blocked Halloween FORCE structure " + type + " in " + world.getName()
                            + " at " + where + " because it would overwrite block entity " + block.getType());
                    return false;
                }
                changes.add(new Change(pos, block.getBlockData().clone(), Bukkit.createBlockData(wanted)));
            }
        } catch (RuntimeException ex) {
            player.sendMessage(plugin.color("&cPředběžný snapshot bloků selhal. Svět zatím nebyl změněn."));
            plugin.getLogger().warning("Halloween structure snapshot failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }

        if (changes.isEmpty()) {
            player.sendMessage(plugin.color("&eNa tomto místě už odpovídající bloky jsou; nebylo co měnit."));
            return true;
        }

        BuildJob job = new BuildJob(player, world, type, centerX, baseY, centerZ, changes);
        activeJobs.add(job);
        player.sendMessage(plugin.color("&6Halloween &8» &ePřipravuji FORCE stavbu &f" + displayName(type)
                + "&e před tebou na &f" + world.getName() + " " + centerX + " " + baseY + " " + centerZ + "&e."));
        player.sendMessage(plugin.color("&cBloky uvnitř definovaného půdorysu budou přepsány. "
                + "Změny jsou omezené na půdorys a střežené předběžnou kontrolou; stavba se nyní provádí po dávkách."));
        plugin.getLogger().info("Starting manual FORCE structure " + type + " world=" + world.getName()
                + " center=" + centerX + "," + baseY + "," + centerZ + " plannedChanges=" + changes.size()
                + " actor=" + player.getName());

        int perTick = Math.max(100, Math.min(1500,
                plugin.getConfig().getInt("structures.blocks-per-tick", 800)));
        job.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(job, perTick), 1L, 1L);
        return true;
    }

    public void stopAndRollback() {
        for (BuildJob job : new ArrayList<>(activeJobs)) {
            if (job.task != null) job.task.cancel();
            rollback(job);
            if (job.player.isOnline()) {
                job.player.sendMessage(plugin.color("&cHalloween stavba byla při vypnutí pluginu zastavena a provedené změny byly vráceny."));
            }
        }
        activeJobs.clear();
    }

    private void tick(BuildJob job, int perTick) {
        if (!activeJobs.contains(job)) return;
        try {
            int end = Math.min(job.changes.size(), job.cursor + perTick);
            while (job.cursor < end) {
                Change change = job.changes.get(job.cursor);
                Block block = job.world.getBlockAt(change.pos().x(), change.pos().y(), change.pos().z());
                // Re-check immediately before each write: players/plugins may have changed the area
                // while the build was being applied over several ticks.
                if (block.getState() instanceof TileState) {
                    throw new IllegalStateException("A block entity appeared during generation at "
                            + change.pos().x() + " " + change.pos().y() + " " + change.pos().z());
                }
                block.setBlockData(change.after(), false);
                job.cursor++;
                job.applied++;
            }
            if (job.cursor >= job.changes.size()) finish(job);
            else if (job.player.isOnline() && job.cursor % 5000 < perTick) {
                int percent = (int) (100L * job.cursor / job.changes.size());
                job.player.sendMessage(plugin.color("&7" + displayName(job.type) + ": &e" + percent + "%"));
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Halloween structure build failed for " + job.type + " in " + job.world.getName()
                    + " after " + job.applied + " writes. Rolling back. Error=" + ex.getClass().getSimpleName()
                    + ": " + ex.getMessage());
            rollback(job);
            activeJobs.remove(job);
            if (job.task != null) job.task.cancel();
            if (job.player.isOnline()) job.player.sendMessage(plugin.color("&cStavba selhala; plugin vrátil provedené změny. Zkontroluj konzoli."));
        }
    }

    private void finish(BuildJob job) {
        if (job.task != null) job.task.cancel();
        activeJobs.remove(job);

        if ("vampirearena".equals(job.type) && plugin.isEligibleGameplayWorld(job.world)) {
            plugin.getConfig().set("bosses.vampire.arena.configured", true);
            plugin.getConfig().set("bosses.vampire.arena.generated", true);
            plugin.getConfig().set("bosses.vampire.arena.world", job.world.getName());
            plugin.getConfig().set("bosses.vampire.arena.x", job.centerX + 0.5D);
            plugin.getConfig().set("bosses.vampire.arena.y", job.centerY + 1.0D);
            plugin.getConfig().set("bosses.vampire.arena.z", job.centerZ + 0.5D);
            plugin.saveConfig();
            if (plugin.getBossManager() != null) plugin.getBossManager().validateConfiguration();
        } else if ("village".equals(job.type) && plugin.isEligibleGameplayWorld(job.world)) {
            plugin.getConfig().set("haunted-village.enabled", true);
            plugin.getConfig().set("haunted-village.world", job.world.getName());
            plugin.getConfig().set("haunted-village.x", job.centerX + 0.5D);
            plugin.getConfig().set("haunted-village.y", job.centerY + 1.0D);
            plugin.getConfig().set("haunted-village.z", job.centerZ + 0.5D);
            plugin.saveConfig();
        }

        plugin.getLogger().info("Halloween structure completed: " + job.type + " world=" + job.world.getName()
                + " center=" + job.centerX + "," + job.centerY + "," + job.centerZ
                + " changedBlocks=" + job.applied);
        if (job.player.isOnline()) {
            job.player.sendMessage(plugin.color("&aHotovo! &f" + displayName(job.type) + " &abyla skutečně postavena."));
            job.player.sendMessage(plugin.color("&7Svět: &e" + job.world.getName() + " &8• &7střed: &e"
                    + job.centerX + " " + (job.centerY + 1) + " " + job.centerZ
                    + " &8• &7změněných bloků: &e" + job.applied));
            if ("vampirearena".equals(job.type) && !plugin.isEligibleGameplayWorld(job.world)) {
                job.player.sendMessage(plugin.color("&7Aréna ve světě Spawn je pouze ruční stavba a nebude použita pro automatický boss encounter."));
            }
        }
    }

    private void rollback(BuildJob job) {
        for (int i = Math.min(job.cursor, job.changes.size()) - 1; i >= 0; i--) {
            Change change = job.changes.get(i);
            try {
                Block block = job.world.getBlockAt(change.pos().x(), change.pos().y(), change.pos().z());
                // Do not erase a newer player/plugin edit while restoring the old snapshot.
                if (block.getState() instanceof TileState || !block.getBlockData().equals(change.after())) {
                    plugin.getLogger().warning("Rollback skipped a changed block at " + job.world.getName()
                            + " " + change.pos().x() + " " + change.pos().y() + " " + change.pos().z()
                            + " because its current state no longer matches this build.");
                    continue;
                }
                block.setBlockData(change.before(), false);
            } catch (RuntimeException rollbackError) {
                plugin.getLogger().severe("Rollback failed at " + job.world.getName() + " "
                        + change.pos().x() + " " + change.pos().y() + " " + change.pos().z()
                        + ": " + rollbackError.getClass().getSimpleName());
            }
        }
        job.applied = 0;
    }

    private Map<Pos, Material> createPlan(World world, String type, int cx, int cz, int baseY, int rotation) {
        int radius = radius(type);
        boolean square = "village".equals(type);
        int clearance = clearance(type);
        Map<Pos, Material> plan = new LinkedHashMap<>();

        // Flatten and clear only the exact footprint, with per-column foundations for low spots.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!square && !HalloweenStructureGeometry.withinCircle(dx, dz, radius)) continue;
                int x = cx + dx;
                int z = cz + dz;
                int groundY = findGroundY(world, x, z);
                int surfaceY = world.getHighestBlockYAt(x, z);
                if (Math.abs(groundY - baseY) > MAX_TERRAIN_DELTA) {
                    throw new IllegalArgumentException("terén se v půdorysu liší od základu o více než "
                            + MAX_TERRAIN_DELTA + " bloků; limit překročen u " + x + " " + groundY + " " + z);
                }
                int clearTop = Math.max(baseY + clearance, surfaceY);
                if (clearTop >= world.getMaxHeight()) {
                    throw new IllegalArgumentException("půdorys je příliš blízko horní hranici světa");
                }
                for (int y = baseY + 1; y <= clearTop; y++) {
                    // Air is the no-op in most of this volume; skip it to bound memory for 97-block arenas.
                    if (!world.getBlockAt(x, y, z).getType().isAir()) put(plan, x, y, z, Material.AIR);
                }
                if (groundY < baseY - 1) {
                    for (int y = groundY + 1; y < baseY; y++) {
                        put(plan, x, y, z, ((x + z + y) & 1) == 0
                                ? Material.POLISHED_BLACKSTONE : Material.DEEPSLATE_BRICKS);
                    }
                }
                put(plan, x, baseY, z, floorMaterial(type, dx, dz, radius));
                if (plan.size() > Math.max(1000, Math.min(180000,
                        plugin.getConfig().getInt("structures.max-block-changes", 150000)))) {
                    throw new IllegalArgumentException("příprava překročila nastavený limit změn bloků");
                }
            }
        }

        int variant = HalloweenStructureGeometry.variant(world.getSeed(), cx, cz);
        switch (type) {
            case "cemetery" -> addCemetery(plan, cx, baseY, cz, radius, rotation, variant);
            case "village" -> addVillage(plan, cx, baseY, cz, radius, rotation, variant);
            case "arena", "goblinarena" -> addArena(plan, cx, baseY, cz, radius, rotation, false);
            case "vampirearena" -> addArena(plan, cx, baseY, cz, radius, rotation, true);
            default -> throw new IllegalArgumentException("neznámý typ");
        }
        return plan;
    }

    private void addCemetery(Map<Pos, Material> plan, int cx, int y, int cz, int r, int rotation, int variant) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                if (Math.abs(Math.sqrt(dx * dx + dz * dz) - (r - 1)) <= 0.55D) {
                    if (!(Math.abs(dx) <= 1 && dz == -r + 1)) {
                        local(plan, cx, y + 1, cz, dx, dz, rotation, Material.MOSSY_COBBLESTONE_WALL);
                        local(plan, cx, y + 2, cz, dx, dz, rotation, Material.COBBLESTONE_WALL);
                    }
                }
            }
        }
        for (int z = -r; z <= -r + 4; z++) local(plan, cx, y, cz, 0, z, rotation, Material.COARSE_DIRT);
        for (int side : new int[]{-1, 1}) {
            local(plan, cx, y + 1, cz, side, -r + 1, rotation, Material.POLISHED_BLACKSTONE_BRICKS);
            local(plan, cx, y + 2, cz, side, -r + 1, rotation, Material.SOUL_LANTERN);
        }
        // Grave rows with alternating stone markers and candles.
        int[] graveXs = variant == 1 ? new int[]{-6, -3, 0, 3, 6}
                : variant == 2 ? new int[]{-4, 0, 4} : new int[]{-5, -2, 2, 5};
        int[] graveZs = variant == 1 ? new int[]{-3, 1, 5} : new int[]{-4, 0, 4};
        for (int gx : graveXs) {
            for (int gz : graveZs) {
                local(plan, cx, y, cz, gx, gz, rotation, Material.MOSSY_COBBLESTONE);
                local(plan, cx, y + 1, cz, gx, gz, rotation, Material.COBBLESTONE_WALL);
                local(plan, cx, y + 2, cz, gx, gz, rotation,
                        ((gx + gz) & 1) == 0 ? Material.POLISHED_BLACKSTONE_BRICK_WALL : Material.MOSSY_COBBLESTONE_WALL);
                if (((gx + gz) & 1) == 0) local(plan, cx, y + 1, cz, gx + 1, gz, rotation, Material.RED_CANDLE);
            }
        }
        if (variant == 2) {
            // Ritual variant: a central stepped dais and four rune stones instead of a crypt.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= 2)
                        local(plan, cx, y, cz, dx, dz, rotation, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            local(plan, cx, y + 1, cz, 0, 0, rotation, Material.POLISHED_BLACKSTONE_BRICK_WALL);
            local(plan, cx, y + 2, cz, 0, 0, rotation, Material.SOUL_LANTERN);
            for (int[] rune : new int[][]{{-4,-4},{4,-4},{-4,4},{4,4}}) {
                local(plan, cx, y + 1, cz, rune[0], rune[1], rotation, Material.CRYING_OBSIDIAN);
                local(plan, cx, y + 2, cz, rune[0], rune[1], rotation, Material.RED_CANDLE);
            }
        } else {
            // Two crypt variants: keep the familiar crypt, but make one less regular.
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = 4; dz <= 8; dz++) {
                    boolean wall = Math.abs(dx) == 3 || dz == 4 || dz == 8
                            || (variant == 1 && Math.abs(dx) == 2 && dz == 6);
                    local(plan, cx, y, cz, dx, dz, rotation, Material.POLISHED_DEEPSLATE);
                    for (int dy = 1; dy <= (variant == 1 ? 4 : 3); dy++) {
                        if (wall) local(plan, cx, y + dy, cz, dx, dz, rotation,
                                variant == 1 ? Material.DEEPSLATE_BRICKS : Material.MOSSY_STONE_BRICKS);
                    }
                    if (wall && dz == 8 && Math.abs(dx) <= 1)
                        local(plan, cx, y + 1, cz, dx, dz, rotation, Material.AIR);
                }
            }
            local(plan, cx, y + (variant == 1 ? 5 : 4), cz, 0, 6, rotation, Material.DEEPSLATE_BRICK_SLAB);
            local(plan, cx, y + (variant == 1 ? 6 : 5), cz, 0, 6, rotation, Material.SOUL_LANTERN);
        }
    }

    private void addVillage(Map<Pos, Material> plan, int cx, int y, int cz, int r, int rotation, int variant) {
        for (int i = -r + 2; i <= r - 2; i++) {
            local(plan, cx, y, cz, i, 0, rotation, Material.DIRT_PATH);
            local(plan, cx, y, cz, 0, i, rotation, Material.DIRT_PATH);
            if (Math.abs(i) % 5 == 0 && Math.abs(i) > 2) {
                local(plan, cx, y + 1, cz, i, 1, rotation, Material.JACK_O_LANTERN);
            }
        }
        // Central ritual fountain with bounded water placement.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2)
                    local(plan, cx, y + 1, cz, dx, dz, rotation, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        local(plan, cx, y + 1, cz, 0, 0, rotation, Material.WATER);
        local(plan, cx, y + 1, cz, 1, 0, rotation, Material.WATER);
        local(plan, cx, y + 1, cz, -1, 0, rotation, Material.WATER);
        local(plan, cx, y + 1, cz, 0, 1, rotation, Material.WATER);
        local(plan, cx, y + 1, cz, 0, -1, rotation, Material.WATER);
        local(plan, cx, y + 2, cz, 0, 0, rotation, Material.SOUL_LANTERN);

        if (variant == 1) {
            // Three-house hamlet with a cemetery garden on the open side.
            addHouse(plan, cx, y, cz, rotation, -10, -8, Material.DARK_OAK_PLANKS);
            addHouse(plan, cx, y, cz, rotation, 10, -8, Material.SPRUCE_PLANKS);
            addHouse(plan, cx, y, cz, rotation, 12, 5, Material.DARK_OAK_PLANKS);
        } else if (variant == 2) {
            // Four offset houses around the square, leaving the main cross-path clear.
            addHouse(plan, cx, y, cz, rotation, -10, -9, Material.SPRUCE_PLANKS);
            addHouse(plan, cx, y, cz, rotation, 10, -9, Material.DARK_OAK_PLANKS);
            addHouse(plan, cx, y, cz, rotation, -10, 9, Material.DARK_OAK_PLANKS);
            addHouse(plan, cx, y, cz, rotation, 10, 9, Material.SPRUCE_PLANKS);
        } else {
            addHouse(plan, cx, y, cz, rotation, -9, -9, Material.DARK_OAK_PLANKS);
            addHouse(plan, cx, y, cz, rotation, 9, -9, Material.SPRUCE_PLANKS);
            addHouse(plan, cx, y, cz, rotation, -9, 9, Material.SPRUCE_PLANKS);
            addHouse(plan, cx, y, cz, rotation, 9, 9, Material.DARK_OAK_PLANKS);
        }

        if (variant != 1) {
            for (int x : new int[]{-13, -11, 11, 13}) {
                local(plan, cx, y + 1, cz, x, 4, rotation, Material.MOSSY_COBBLESTONE_WALL);
                local(plan, cx, y + 2, cz, x, 4, rotation, Material.COBBLESTONE_WALL);
            }
        } else {
            for (int x : new int[]{-13, -11, -9}) {
                local(plan, cx, y + 1, cz, x, 4, rotation, Material.MOSSY_COBBLESTONE_WALL);
                local(plan, cx, y + 2, cz, x, 4, rotation, Material.COBBLESTONE_WALL);
            }
        }
        for (int dx : new int[]{-14, 14}) {
            local(plan, cx, y + 1, cz, dx, 0, rotation, Material.OAK_FENCE);
            local(plan, cx, y + 1, cz, dx, 1, rotation, Material.SOUL_LANTERN);
        }
    }

    private void addHouse(Map<Pos, Material> plan, int cx, int y, int cz, int rotation,
                          int hx, int hz, Material wallMaterial) {
        int half = 3;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                local(plan, cx, y, cz, hx + dx, hz + dz, rotation, Material.DARK_OAK_PLANKS);
                boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
                for (int dy = 1; dy <= 3; dy++) {
                    if (edge) {
                        Material block = (dy == 2 && (Math.abs(dx) == half && dz == 0 || Math.abs(dz) == half && dx == 0))
                                ? Material.GLASS_PANE : wallMaterial;
                        local(plan, cx, y + dy, cz, hx + dx, hz + dz, rotation, block);
                    } else {
                        local(plan, cx, y + dy, cz, hx + dx, hz + dz, rotation, Material.AIR);
                    }
                }
                if (edge) local(plan, cx, y + 4, cz, hx + dx, hz + dz, rotation, Material.COBBLED_DEEPSLATE_STAIRS);
            }
        }
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            for (int dz = -half - 1; dz <= half + 1; dz++) {
                local(plan, cx, y + 5, cz, hx + dx, hz + dz, rotation, Material.DEEPSLATE_BRICKS);
            }
        }
        local(plan, cx, y + 1, cz, hx, hz - half, rotation, Material.AIR);
        local(plan, cx, y + 2, cz, hx, hz - half, rotation, Material.AIR);
        local(plan, cx, y + 1, cz, hx, hz - half - 1, rotation, Material.DARK_OAK_FENCE);
        local(plan, cx, y + 2, cz, hx, hz - half - 1, rotation, Material.DARK_OAK_FENCE);
    }

    private void addArena(Map<Pos, Material> plan, int cx, int y, int cz, int r, int rotation, boolean vampire) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r) continue;
                double distance = Math.sqrt(d2);
                if (Math.abs(distance - (r - 1)) <= 0.6D) {
                    boolean gate = Math.abs(dx) <= 2 && dz <= -r + 1;
                    if (!gate) {
                        local(plan, cx, y + 1, cz, dx, dz, rotation, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                        if (vampire) local(plan, cx, y + 2, cz, dx, dz, rotation, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                    }
                }
                if (Math.abs(distance - r * 0.68D) <= 0.48D) {
                    local(plan, cx, y, cz, dx, dz, rotation, Material.CRYING_OBSIDIAN);
                } else if (Math.abs(distance - r * 0.36D) <= 0.48D) {
                    local(plan, cx, y, cz, dx, dz, rotation, Material.RED_NETHER_BRICKS);
                }
            }
        }
        for (int gateZ : new int[]{-r + 1, r - 1}) {
            for (int x = -2; x <= 2; x++) local(plan, cx, y + 1, cz, x, gateZ, rotation, Material.AIR);
        }
        for (int gateX : new int[]{-r + 1, r - 1}) {
            for (int z = -2; z <= 2; z++) local(plan, cx, y + 1, cz, gateX, z, rotation, Material.AIR);
        }
        int towerRadius = Math.max(5, r - (vampire ? 9 : 5));
        int[][] towers = {{0,-towerRadius},{towerRadius,0},{0,towerRadius},{-towerRadius,0}};
        for (int[] tower : towers) {
            int tx = tower[0], tz = tower[1];
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 1; dy <= (vampire ? 10 : 6); dy++) {
                        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        Material block = (dy == 3 || dy == 6)
                                ? Material.GILDED_BLACKSTONE
                                : (edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                        local(plan, cx, y + dy, cz, tx + dx, tz + dz, rotation, block);
                    }
                }
            }
            local(plan, cx, y + (vampire ? 11 : 7), cz, tx, tz, rotation, Material.SOUL_LANTERN);
        }
        for (int dx : new int[]{-5, 5}) {
            for (int dz : new int[]{-5, 5}) {
                local(plan, cx, y + 1, cz, dx, dz, rotation, Material.RED_NETHER_BRICKS);
                local(plan, cx, y + 2, cz, dx, dz, rotation, Material.SOUL_LANTERN);
            }
        }
    }

    private Material floorMaterial(String type, int dx, int dz, int radius) {
        if ("village".equals(type)) {
            if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) return Material.COARSE_DIRT;
            return Material.MUD_BRICKS;
        }
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(distance - (radius - 1)) < 0.6D || Math.abs(distance - radius * 0.68D) < 0.48D)
            return Material.CRYING_OBSIDIAN;
        if (Math.abs(distance - radius * 0.36D) < 0.48D) return Material.RED_NETHER_BRICKS;
        if ("cemetery".equals(type)) return Material.POLISHED_DEEPSLATE;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private int findGroundY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        while (y > world.getMinHeight()) {
            Material material = world.getBlockAt(x, y, z).getType();
            String name = material.name();
            if (!material.isSolid() || material == Material.WATER || material == Material.LAVA
                    || name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                    || name.endsWith("_SAPLING") || name.endsWith("_FLOWERS") || name.endsWith("_TULIP")
                    || material == Material.SHORT_GRASS || material == Material.TALL_GRASS
                    || material == Material.FERN || material == Material.LARGE_FERN
                    || material == Material.DEAD_BUSH || material == Material.VINE
                    || material == Material.SWEET_BERRY_BUSH) {
                y--;
                continue;
            }
            return y;
        }
        return world.getMinHeight() + 1;
    }

    private void local(Map<Pos, Material> plan, int cx, int y, int cz,
                       int dx, int dz, int rotation, Material material) {
        int rx = HalloweenStructureGeometry.rotateX(dx, dz, rotation);
        int rz = HalloweenStructureGeometry.rotateZ(dx, dz, rotation);
        put(plan, cx + rx, y, cz + rz, material);
    }

    private void put(Map<Pos, Material> plan, int x, int y, int z, Material material) {
        plan.put(new Pos(x, y, z), material);
    }

    private int radius(String type) {
        return HalloweenStructureGeometry.radius(type);
    }

    private int clearance(String type) {
        return HalloweenStructureGeometry.clearance(type);
    }

    private String normalizeType(String requested) {
        return HalloweenStructureGeometry.normalizeType(requested);
    }

    private String displayName(String type) {
        return switch (type) {
            case "cemetery" -> "Hřbitov";
            case "village" -> "Halloween vesnice";
            case "arena" -> "Halloween aréna";
            case "vampirearena" -> "Aréna Krále upírů";
            case "goblinarena" -> "Aréna Goblin Kinga";
            default -> type;
        };
    }
}
