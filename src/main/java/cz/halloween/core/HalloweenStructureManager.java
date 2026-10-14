package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded, tick-batched procedural structures for manual administrator commands.
 *
 * Manual structures intentionally use FORCE inside a computed, finite footprint.
 * Automatic world decoration never calls this class and has separate world rules.
 */
public final class HalloweenStructureManager {
    private enum Kind {
        GRAVEYARD("hřbitov", 15, 12, false),
        VILLAGE("vesnice", 20, 14, false),
        ARENA("aréna", 48, 20, true),
        VAMPIRE_ARENA("vampirearena", 48, 20, true),
        GOBLIN_ARENA("goblinarena", 48, 20, true);

        private final String displayName;
        private final int radius;
        private final int clearHeight;
        private final boolean circular;

        Kind(String displayName, int radius, int clearHeight, boolean circular) {
            this.displayName = displayName;
            this.radius = radius;
            this.clearHeight = clearHeight;
            this.circular = circular;
        }
    }

    private enum Phase { CLEAR, SUPPORT, PLACE, ROLLBACK }

    private record Pos(int x, int y, int z) {}
    private record ChunkPos(int x, int z) {}
    private record Cell(Pos position, Material material) {}

    private final HalloweenCore plugin;
    private BuildJob activeJob;

    public HalloweenStructureManager(HalloweenCore plugin) {
        this.plugin = plugin;
    }

    public boolean handleCommand(org.bukkit.command.CommandSender sender, String[] args) {
        if (!hasStructurePermission(sender)) {
            sender.sendMessage(plugin.color("&cPotřebuješ oprávnění halloweencore.structure.spawn a halloweencore.structure.force."));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cStruktury musíš spustit přímo ve hře jako hráč."));
            return true;
        }
        if (args.length < 2 || args[1].isBlank()) {
            sender.sendMessage(plugin.color("&ePoužití: /halloween spawn <hřbitov|vesnice|aréna|vampirearena|goblinarena>"));
            sender.sendMessage(plugin.color("&7Stav: " + (activeJob == null ? "&aPřipraveno" : "&eProbíhá generování " + activeJob.kind.displayName)));
            return true;
        }
        if (activeJob != null) {
            player.sendMessage(plugin.color("&cJiná stavba právě probíhá. Počkej, až se dokončí nebo bezpečně vrátí změny."));
            return true;
        }

        Kind kind = parseKind(args[1]);
        if (kind == null) {
            player.sendMessage(plugin.color("&cNeznámá struktura. Použij: hřbitov, vesnice, aréna, vampirearena nebo goblinarena."));
            return true;
        }
        start(player, kind);
        return true;
    }

    public List<String> tabComplete(String prefix) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return List.of("hřbitov", "vesnice", "aréna", "vampirearena", "goblinarena").stream()
                .filter(value -> value.startsWith(normalized))
                .toList();
    }

    public String statusLine() {
        if (activeJob == null) return "Structures: idle";
        return "Structures: " + activeJob.kind.displayName + ", phase=" + activeJob.phase
                + ", processed=" + activeJob.processed + ", changed=" + activeJob.snapshots.size();
    }

    public void stop() {
        if (activeJob == null) return;
        BuildJob job = activeJob;
        if (job.task != null) job.task.cancel();
        rollbackImmediately(job);
        cleanupChunks(job);
        activeJob = null;
        plugin.getLogger().warning("An unfinished Halloween structure build was rolled back during plugin shutdown.");
    }

    private boolean hasStructurePermission(org.bukkit.command.CommandSender sender) {
        return sender.hasPermission("halloweencore.admin")
                || (sender.hasPermission("halloweencore.structure.spawn")
                && sender.hasPermission("halloweencore.structure.force"));
    }

    private Kind parseKind(String input) {
        String value = input.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("č", "c").replace("ď", "d")
                .replace("é", "e").replace("ě", "e").replace("í", "i")
                .replace("ň", "n").replace("ó", "o").replace("ř", "r")
                .replace("š", "s").replace("ť", "t").replace("ú", "u")
                .replace("ů", "u").replace("ý", "y").replace("ž", "z")
                .replace("_", "").replace("-", "");
        return switch (value) {
            case "hrbitov", "graveyard", "cemetery" -> Kind.GRAVEYARD;
            case "vesnice", "village", "hauntedvillage" -> Kind.VILLAGE;
            case "arena", "aren" -> Kind.ARENA;
            case "vampirearena", "upiriarena", "upiriare na" -> Kind.VAMPIRE_ARENA;
            case "goblinarena" -> Kind.GOBLIN_ARENA;
            default -> null;
        };
    }

    private void start(Player player, Kind kind) {
        World world = player.getWorld();
        Vector forward = player.getLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 0.001) forward = new Vector(0, 0, 1);
        forward.normalize();

        int distance = Math.max(plugin.getConfig().getInt("structures.placement-distance-blocks", 12), kind.radius + 4);
        int centerX = (int) Math.floor(player.getLocation().getX() + forward.getX() * distance);
        int centerZ = (int) Math.floor(player.getLocation().getZ() + forward.getZ() * distance);
        int centerY = findGroundY(world, centerX, centerZ);
        if (centerY < world.getMinHeight() + 2 || centerY + kind.clearHeight + 2 >= world.getMaxHeight()) {
            player.sendMessage(plugin.color("&cTady není dostatečná výška pro stavbu. Zkus jiné místo."));
            return;
        }

        if (!insideWorldBorder(world, centerX, centerZ, kind.radius + 1)) {
            player.sendMessage(plugin.color("&cStruktura by zasáhla mimo hranice světa. Otoč se nebo zvol jiné místo."));
            return;
        }

        int maxChanges = Math.max(10000, Math.min(300000,
                plugin.getConfig().getInt("structures.max-block-changes", 220000)));
        int blocksPerTick = Math.max(100, Math.min(5000,
                plugin.getConfig().getInt("structures.blocks-per-tick", 1200)));
        int supportDepth = Math.max(0, Math.min(96,
                plugin.getConfig().getInt("structures.max-support-depth", 32)));

        BuildJob job;
        try {
            double yaw = Math.toRadians(player.getLocation().getYaw());
            job = new BuildJob(kind, world, centerX, centerY, centerZ, yaw,
                    maxChanges, blocksPerTick, supportDepth, player.getUniqueId());
            job.placement.addAll(createPlan(job));
            if (job.placement.size() > maxChanges) {
                player.sendMessage(plugin.color("&cStruktura překračuje nastavený limit bloků. Nic nebylo změněno."));
                return;
            }
        } catch (RuntimeException ex) {
            player.sendMessage(plugin.color("&cStrukturu se nepodařilo připravit. Nic nebylo změněno; zkontroluj konzoli."));
            plugin.getLogger().warning("Could not prepare " + kind + " structure: " + ex.getClass().getSimpleName());
            return;
        }

        activeJob = job;
        player.sendMessage(plugin.color("&6&lHALLOWEEN &8» &eZačínám stavět: &f" + kind.displayName
                + "&e. Režim FORCE přepisuje bloky pouze uvnitř půdorysu a výšky stavby."));
        player.sendMessage(plugin.color("&7Cíl: &f" + world.getName() + " " + centerX + " " + centerY + " " + centerZ
                + "&7 • změny se aplikují postupně; potvrzení ani nastavování středu není potřeba."));
        plugin.getLogger().warning("Manual FORCE structure build started: type=" + kind + ", world=" + world.getName()
                + ", center=" + centerX + "," + centerY + "," + centerZ
                + ", radius=" + kind.radius + ", placementCells=" + job.placement.size()
                + ", operator=" + player.getName() + ". Blocks inside the declared footprint may be replaced.");

        job.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(job), 1L, 1L);
    }

    private void tick(BuildJob job) {
        if (activeJob != job) {
            if (job.task != null) job.task.cancel();
            return;
        }
        try {
            int budget = job.blocksPerTick;
            while (budget-- > 0) {
                switch (job.phase) {
                    case CLEAR -> {
                        if (!processClearCell(job)) {
                            job.phase = Phase.SUPPORT;
                            prepareSupport(job);
                        }
                    }
                    case SUPPORT -> {
                        if (job.supportIndex >= job.support.size()) {
                            job.phase = Phase.PLACE;
                            continue;
                        }
                        apply(job, job.support.get(job.supportIndex++));
                    }
                    case PLACE -> {
                        if (job.placementIndex >= job.placement.size()) {
                            finish(job);
                            return;
                        }
                        apply(job, job.placement.get(job.placementIndex++));
                    }
                    case ROLLBACK -> {
                        if (!processRollbackCell(job)) {
                            failed(job);
                            return;
                        }
                    }
                }
                job.processed++;
            }
        } catch (RuntimeException ex) {
            if (job.phase != Phase.ROLLBACK) {
                job.failure = ex.getClass().getSimpleName();
                job.phase = Phase.ROLLBACK;
                job.rollbackStates = new ArrayList<>(job.snapshots.values());
                Collections.reverse(job.rollbackStates);
                job.rollbackIndex = 0;
                plugin.getLogger().warning("Halloween structure failed during " + job.phase + " in "
                        + job.kind + "; starting rollback (" + job.failure + ").");
            }
        }
    }

    private boolean processClearCell(BuildJob job) {
        while (job.clearX <= job.kind.radius) {
            if (job.clearZ > job.kind.radius) {
                job.clearZ = -job.kind.radius;
                job.clearX++;
                job.clearY = job.centerY + 1;
                continue;
            }
            int lx = job.clearX;
            int lz = job.clearZ;
            if (!insideFootprint(job.kind, lx, lz)) {
                job.clearZ++;
                job.clearY = job.centerY + 1;
                continue;
            }
            Pos pos = transform(job, lx, 0, lz);
            ensureChunk(job, pos.x, pos.z);
            if (job.clearY > job.centerY + job.kind.clearHeight) {
                job.clearZ++;
                job.clearY = job.centerY + 1;
                continue;
            }
            Block block = job.world.getBlockAt(pos.x, job.clearY++, pos.z);
            if (!block.getType().isAir()) {
                remember(job, block);
                block.setType(Material.AIR, false);
            }
            return true;
        }
        return false;
    }

    private void prepareSupport(BuildJob job) {
        if (job.supportDepth <= 0) return;
        for (int lx = -job.kind.radius; lx <= job.kind.radius; lx++) {
            for (int lz = -job.kind.radius; lz <= job.kind.radius; lz++) {
                if (!insideFootprint(job.kind, lx, lz)) continue;
                Pos p = transform(job, lx, 0, lz);
                ensureChunk(job, p.x, p.z);
                int y = job.centerY - 1;
                int floor = Math.max(job.world.getMinHeight(), job.centerY - job.supportDepth);
                while (y >= floor && !job.world.getBlockAt(p.x, y, p.z).getType().isSolid()) y--;
                int solidY = y;
                for (int fillY = solidY + 1; fillY < job.centerY; fillY++) {
                    Material support = job.kind == Kind.VILLAGE ? Material.COBBLESTONE
                            : job.kind == Kind.GOBLIN_ARENA ? Material.MOSSY_STONE_BRICKS : Material.DEEPSLATE_BRICKS;
                    job.support.add(new Cell(new Pos(p.x, fillY, p.z), support));
                }
            }
        }
    }

    private boolean processRollbackCell(BuildJob job) {
        if (job.rollbackStates == null || job.rollbackIndex >= job.rollbackStates.size()) return false;
        BlockState state = job.rollbackStates.get(job.rollbackIndex++);
        ensureChunk(job, state.getX(), state.getZ());
        state.update(true, false);
        return job.rollbackIndex < job.rollbackStates.size();
    }

    private void apply(BuildJob job, Cell cell) {
        Pos pos = cell.position();
        ensureChunk(job, pos.x, pos.z);
        Block block = job.world.getBlockAt(pos.x, pos.y, pos.z);
        if (block.getType() == cell.material()) return;
        if (job.snapshots.size() >= job.maxChanges) {
            throw new IllegalStateException("structure.max-block-changes exceeded");
        }
        remember(job, block);
        block.setType(cell.material(), false);
    }

    private void remember(BuildJob job, Block block) {
        Pos pos = new Pos(block.getX(), block.getY(), block.getZ());
        if (job.snapshots.containsKey(pos)) return;
        if (job.snapshots.size() >= job.maxChanges) throw new IllegalStateException("structure.max-block-changes exceeded");
        job.snapshots.put(pos, block.getState());
    }

    private void finish(BuildJob job) {
        if (job.task != null) job.task.cancel();
        if (job.kind == Kind.VAMPIRE_ARENA) saveArenaCenter("bosses.vampire.arena", job);
        if (job.kind == Kind.GOBLIN_ARENA) saveArenaCenter("bosses.goblin.arena", job);
        cleanupChunks(job);
        Player operator = Bukkit.getPlayer(job.operator);
        if (operator != null) {
            operator.sendMessage(plugin.color("&aHotovo: struktura &f" + job.kind.displayName + " &abyla skutečně postavena."));
            operator.sendMessage(plugin.color("&7Upraveno blokových pozic: &e" + job.snapshots.size() + "&7. Střed: "
                    + job.world.getName() + " " + job.centerX + " " + job.centerY + " " + job.centerZ));
        }
        plugin.getLogger().info("Halloween structure build completed: " + job.kind + ", world=" + job.world.getName()
                + ", center=" + job.centerX + "," + job.centerY + "," + job.centerZ
                + ", changedBlocks=" + job.snapshots.size() + ".");
        activeJob = null;
    }

    private void saveArenaCenter(String path, BuildJob job) {
        plugin.getConfig().set(path + ".configured", true);
        plugin.getConfig().set(path + ".world", job.world.getName());
        plugin.getConfig().set(path + ".x", job.centerX + 0.5D);
        plugin.getConfig().set(path + ".y", job.centerY + 1.0D);
        plugin.getConfig().set(path + ".z", job.centerZ + 0.5D);
        plugin.saveConfig();
    }

    private void failed(BuildJob job) {
        if (job.task != null) job.task.cancel();
        cleanupChunks(job);
        Player operator = Bukkit.getPlayer(job.operator);
        if (operator != null) operator.sendMessage(plugin.color("&cGenerování selhalo (" + job.failure
                + "). Provedl jsem návrat zaznamenaných změn."));
        plugin.getLogger().severe("Halloween structure build failed and rollback finished: " + job.kind
                + ", restored=" + job.snapshots.size() + ", reason=" + job.failure);
        activeJob = null;
    }

    private void rollbackImmediately(BuildJob job) {
        List<BlockState> states = new ArrayList<>(job.snapshots.values());
        Collections.reverse(states);
        for (BlockState state : states) {
            try {
                ensureChunk(job, state.getX(), state.getZ());
                state.update(true, false);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Could not restore a block while rolling back " + job.kind + ": "
                        + ex.getClass().getSimpleName());
            }
        }
    }

    private void cleanupChunks(BuildJob job) {
        for (ChunkPos pos : job.loadedByJob) {
            if (!job.unloadedAtStart.contains(pos) || !job.world.isChunkLoaded(pos.x, pos.z)) continue;
            var chunk = job.world.getChunkAt(pos.x, pos.z);
            boolean hasPlayer = false;
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Player) {
                    hasPlayer = true;
                    break;
                }
            }
            if (!hasPlayer && chunk.getEntities().length == 0) job.world.unloadChunk(pos.x, pos.z, false);
        }
    }

    private void ensureChunk(BuildJob job, int x, int z) {
        int cx = Math.floorDiv(x, 16);
        int cz = Math.floorDiv(z, 16);
        ChunkPos key = new ChunkPos(cx, cz);
        if (job.loadedByJob.add(key)) {
            if (!job.world.isChunkLoaded(cx, cz)) {
                job.unloadedAtStart.add(key);
                job.world.getChunkAt(cx, cz);
            }
        }
    }

    private boolean insideWorldBorder(World world, int x, int z, int radius) {
        return world.getWorldBorder().isInside(new Location(world, x - radius, world.getMinHeight(), z - radius))
                && world.getWorldBorder().isInside(new Location(world, x + radius, world.getMinHeight(), z - radius))
                && world.getWorldBorder().isInside(new Location(world, x - radius, world.getMinHeight(), z + radius))
                && world.getWorldBorder().isInside(new Location(world, x + radius, world.getMinHeight(), z + radius));
    }

    private int findGroundY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        while (y > world.getMinHeight() + 2) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (material.isSolid() && !isTreeOrFoliage(material)) return y;
            y--;
        }
        return world.getHighestBlockYAt(x, z);
    }

    private boolean isTreeOrFoliage(Material material) {
        String name = material.name();
        return name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_SAPLING") || name.endsWith("_FLOWERS") || name.endsWith("_TULIP")
                || name.endsWith("_BUSH") || material == Material.SHORT_GRASS || material == Material.TALL_GRASS
                || material == Material.FERN || material == Material.LARGE_FERN || material == Material.DEAD_BUSH
                || material == Material.VINE || material == Material.SNOW;
    }

    private boolean insideFootprint(Kind kind, int x, int z) {
        if (!kind.circular) return true;
        return x * x + z * z <= kind.radius * kind.radius;
    }

    private Pos transform(BuildJob job, int localX, int relativeY, int localZ) {
        // Local +Z is the structure front. Rotate the whole footprint to face the player.
        double sin = Math.sin(job.yaw);
        double cos = Math.cos(job.yaw);
        int x = job.centerX + (int) Math.round(localX * cos - localZ * sin);
        int z = job.centerZ + (int) Math.round(localX * sin + localZ * cos);
        return new Pos(x, job.centerY + relativeY, z);
    }

    private List<Cell> createPlan(BuildJob job) {
        Map<Pos, Material> cells = new LinkedHashMap<>();
        Kind kind = job.kind;
        int r = kind.radius;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (!insideFootprint(kind, x, z)) continue;
                Material floor;
                if (kind == Kind.GRAVEYARD) {
                    floor = (Math.abs(x) <= 1 || (z >= -r && z <= -r + 2)) ? Material.MOSSY_COBBLESTONE : Material.PODZOL;
                } else if (kind == Kind.VILLAGE) {
                    floor = (Math.abs(x) <= 2 || Math.abs(z) <= 2 || (Math.abs(x) <= 5 && Math.abs(z) <= 5))
                            ? Material.COBBLESTONE : ((Math.floorMod(x + z, 5) == 0) ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
                } else {
                    int distance2 = x * x + z * z;
                    if (distance2 > (r - 2) * (r - 2)) floor = Material.POLISHED_BLACKSTONE_BRICKS;
                    else if (distance2 > 37 * 37) floor = Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
                    else if (distance2 > 33 * 33) floor = Material.RED_NETHER_BRICKS;
                    else if (distance2 <= 8 * 8) floor = Material.CHISELED_POLISHED_BLACKSTONE;
                    else if (((x + z) & 1) == 0) floor = Material.POLISHED_DEEPSLATE;
                    else floor = Material.DEEPSLATE_TILES;
                    if (kind == Kind.GOBLIN_ARENA && distance2 < 30 * 30 && Math.floorMod(x - z, 7) == 0) {
                        floor = Material.MOSSY_STONE_BRICKS;
                    }
                }
                put(job, cells, x, 0, z, floor);
            }
        }

        if (kind == Kind.GRAVEYARD) graveyard(job, cells);
        else if (kind == Kind.VILLAGE) village(job, cells);
        else arena(job, cells);

        List<Cell> result = new ArrayList<>(cells.size());
        for (Map.Entry<Pos, Material> entry : cells.entrySet()) result.add(new Cell(entry.getKey(), entry.getValue()));
        return result;
    }

    private void graveyard(BuildJob job, Map<Pos, Material> cells) {
        int r = job.kind.radius;
        for (int y = 1; y <= 2; y++) {
            for (int p = -r; p <= r; p++) {
                if (p < -2 || p > 2) {
                    put(job, cells, p, y, -r, Material.MOSSY_STONE_BRICKS);
                    put(job, cells, p, y, r, Material.MOSSY_STONE_BRICKS);
                    put(job, cells, -r, y, p, Material.MOSSY_STONE_BRICKS);
                    put(job, cells, r, y, p, Material.MOSSY_STONE_BRICKS);
                }
            }
        }
        // Entrance arch toward the player (the local -Z side).
        for (int x = -2; x <= 2; x++) {
            put(job, cells, x, 1, -r, Material.AIR);
            put(job, cells, x, 2, -r, Material.AIR);
        }
        for (int x = -3; x <= 3; x++) {
            put(job, cells, x, 3, -r, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int side : new int[]{-1, 1}) {
            for (int y = 1; y <= 4; y++) put(job, cells, side * 3, y, -r, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        // Old crypt at the far end, with a stepped roof and empty doorway.
        for (int x = -5; x <= 5; x++) {
            for (int z = 7; z <= 13; z++) {
                boolean wall = x == -5 || x == 5 || z == 7 || z == 13;
                if (wall) for (int y = 1; y <= 4; y++) put(job, cells, x, y, z, Material.DEEPSLATE_BRICKS);
                else if (x == 0 && z == 7) put(job, cells, x, 1, z, Material.AIR);
            }
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = 7; z <= 13; z++) put(job, cells, x, 5, z, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = 8; z <= 12; z++) put(job, cells, x, 6, z, Material.DEEPSLATE_TILES);
        }
        // Twenty distinctive grave plots, each with a tombstone and a low grave border.
        int index = 0;
        for (int z = -8; z <= 4; z += 4) {
            for (int x = -10; x <= 10; x += 5) {
                if (Math.abs(x) <= 2 && z <= 0) continue;
                Material stone = switch (index++ % 4) {
                    case 0 -> Material.MOSSY_COBBLESTONE;
                    case 1 -> Material.STONE_BRICKS;
                    case 2 -> Material.COBBLED_DEEPSLATE;
                    default -> Material.POLISHED_BLACKSTONE_BRICKS;
                };
                for (int dx = -1; dx <= 1; dx++) {
                    put(job, cells, x + dx, 1, z, stone);
                    put(job, cells, x + dx, 1, z + 1, stone);
                }
                put(job, cells, x, 2, z, stone);
                if (index % 3 == 0) put(job, cells, x, 3, z, Material.STONE_BRICK_WALL);
                else put(job, cells, x, 3, z, Material.POLISHED_BLACKSTONE_BRICKS);
                put(job, cells, x, 4, z, Material.STONE_BRICK_WALL);
            }
        }
        // Soul lanterns at corners.
        for (int x : new int[]{-12, 12}) for (int z : new int[]{-12, 12}) {
            put(job, cells, x, 1, z, Material.POLISHED_BLACKSTONE_BRICKS);
            put(job, cells, x, 2, z, Material.CHAIN);
            put(job, cells, x, 3, z, Material.SOUL_LANTERN);
        }
    }

    private void village(BuildJob job, Map<Pos, Material> cells) {
        // Six houses, intentionally different wall/roof palettes and heights.
        house(job, cells, -12, -12, Material.SPRUCE_PLANKS, Material.DARK_OAK_PLANKS, 5, 6);
        house(job, cells, 12, -12, Material.DARK_OAK_PLANKS, Material.DEEPSLATE_BRICKS, 6, 7);
        house(job, cells, -12, 12, Material.OAK_PLANKS, Material.COBBLED_DEEPSLATE, 5, 6);
        house(job, cells, 12, 12, Material.SPRUCE_PLANKS, Material.POLISHED_BLACKSTONE_BRICKS, 6, 7);
        house(job, cells, -15, 1, Material.MANGROVE_PLANKS, Material.MUD_BRICKS, 5, 6);
        house(job, cells, 15, 1, Material.DARK_OAK_PLANKS, Material.MOSSY_STONE_BRICKS, 5, 6);
        // Central square, well, and surrounding market stands.
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            put(job, cells, x, 1, z, Material.STONE_BRICKS);
            if (Math.abs(x) >= 3 || Math.abs(z) >= 3) put(job, cells, x, 1, z, Material.MOSSY_STONE_BRICKS);
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (x * x + z * z <= 4) put(job, cells, x, 1, z, Material.WATER);
        }
        for (int[] corner : new int[][]{{-3,-3},{-3,3},{3,-3},{3,3}}) {
            put(job, cells, corner[0], 1, corner[1], Material.MOSSY_COBBLESTONE);
            put(job, cells, corner[0], 2, corner[1], Material.COBBLESTONE_WALL);
            put(job, cells, corner[0], 3, corner[1], Material.CHAIN);
            put(job, cells, corner[0], 4, corner[1], Material.SOUL_LANTERN);
        }
        for (int[] pos : new int[][]{{-6,-6},{6,-6},{-6,6},{6,6}}) {
            put(job, cells, pos[0], 1, pos[1], Material.SPRUCE_FENCE);
            put(job, cells, pos[0], 2, pos[1], Material.SOUL_LANTERN);
        }
        // A small common meeting hall and vegetable beds provide a varied silhouette.
        house(job, cells, 0, 14, Material.BRICKS, Material.DARK_OAK_PLANKS, 6, 7);
        for (int x = -18; x <= -15; x++) {
            for (int z = -3; z <= 3; z++) put(job, cells, x, 1, z, (z % 2 == 0) ? Material.FARMLAND : Material.DIRT);
        }
    }

    private void house(BuildJob job, Map<Pos, Material> cells, int cx, int cz,
                       Material wall, Material roof, int wallHeight, int roofHeight) {
        int half = 3;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                for (int y = 1; y <= wallHeight; y++) {
                    if (!edge) continue;
                    boolean doorGap = z == -half && x == 0 && y <= 2;
                    boolean windowGap = y == 3 && ((Math.abs(x) == half && z == 0) || (Math.abs(z) == half && x == 2));
                    if (doorGap || windowGap) continue;
                    put(job, cells, cx + x, y, cz + z, wall);
                }
                if (!edge && x == 0 && z == 0 && roofHeight > wallHeight) put(job, cells, x + cx, wallHeight, z + cz, wall);
            }
        }
        // Crossed beam lintels and a compact stepped roof; no non-vanilla asset is required.
        for (int x = -half - 1; x <= half + 1; x++) {
            for (int z = -half - 1; z <= half + 1; z++) {
                int layer = Math.max(Math.abs(x), Math.abs(z)) - half;
                if (layer < 0) layer = 0;
                if (layer <= 2) put(job, cells, cx + x, wallHeight + layer, cz + z, roof);
            }
        }
        // Timber trim and a small chimney.
        for (int y = 1; y <= wallHeight; y++) {
            put(job, cells, cx - half, y, cz - half, Material.STRIPPED_SPRUCE_LOG);
            put(job, cells, cx + half, y, cz + half, Material.STRIPPED_SPRUCE_LOG);
        }
        for (int y = wallHeight + 1; y <= wallHeight + 3; y++) put(job, cells, cx + 2, y, cz - 2, Material.BRICKS);
    }

    private void arena(BuildJob job, Map<Pos, Material> cells) {
        int r = job.kind.radius;
        Material wall = job.kind == Kind.GOBLIN_ARENA ? Material.MOSSY_STONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
        Material trim = job.kind == Kind.GOBLIN_ARENA ? Material.EMERALD_BLOCK : Material.RED_NETHER_BRICKS;
        Material floor = job.kind == Kind.GOBLIN_ARENA ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE_BRICKS;

        // Monumental circular boundary with four gates.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int d2 = x * x + z * z;
                if (d2 < (r - 1) * (r - 1) || d2 > r * r) continue;
                boolean gate = (Math.abs(x) <= 3 && Math.abs(z) >= r - 1)
                        || (Math.abs(z) <= 3 && Math.abs(x) >= r - 1);
                if (gate) continue;
                for (int y = 1; y <= 4; y++) put(job, cells, x, y, z, wall);
                if (d2 > (r - 1) * (r - 1)) put(job, cells, x, 5, z, trim);
            }
        }
        // Eight tall towers with stepped silhouettes and soul lighting.
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D * i) / 8.0D;
            int cx = (int) Math.round(Math.cos(angle) * 43);
            int cz = (int) Math.round(Math.sin(angle) * 43);
            for (int y = 1; y <= 10; y++) {
                int size = (y <= 7) ? 2 : (y <= 9 ? 1 : 0);
                for (int dx = -size; dx <= size; dx++) for (int dz = -size; dz <= size; dz++) {
                    put(job, cells, cx + dx, y, cz + dz, wall);
                }
            }
            put(job, cells, cx, 11, cz, Material.CHAIN);
            put(job, cells, cx, 12, cz, Material.SOUL_LANTERN);
        }
        // Four broad stair-like approaches. They stay inside the defined footprint.
        for (int sign : new int[]{-1, 1}) {
            for (int step = 0; step < 8; step++) {
                for (int width = -3; width <= 3; width++) {
                    int z = sign * (r - 7 + step);
                    put(job, cells, width, 1, z, (step % 2 == 0) ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                    int x = sign * (r - 7 + step);
                    put(job, cells, x, 1, width, (step % 2 == 0) ? Material.POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        // Central raised dais and four approach pillars.
        for (int y = 1; y <= 3; y++) {
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                if (x * x + z * z <= (9 - y) * (9 - y)) {
                    put(job, cells, x, y, z, (y == 3) ? trim : wall);
                }
            }
        }
        for (int signX : new int[]{-1, 1}) for (int signZ : new int[]{-1, 1}) {
            int x = signX * 13;
            int z = signZ * 13;
            for (int y = 1; y <= 6; y++) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    put(job, cells, x + dx, y, z + dz, wall);
                }
            }
            put(job, cells, x, 7, z, trim);
        }
        // Runes in eight radial spokes; material palette changes by arena identity.
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0D * i) / 8.0D;
            for (int radius = 16; radius <= 31; radius++) {
                int x = (int) Math.round(Math.cos(angle) * radius);
                int z = (int) Math.round(Math.sin(angle) * radius);
                put(job, cells, x, 1, z, trim);
            }
        }
        // Keep the arena center clear for the encounter spawn point.
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            put(job, cells, x, 1, z, floor);
        }
    }

    private void put(BuildJob job, Map<Pos, Material> cells, int localX, int relativeY, int localZ, Material material) {
        Pos p = transform(job, localX, relativeY, localZ);
        // Generated writes are kept within their transformed footprint and height.
        if (p.y < job.world.getMinHeight() || p.y >= job.world.getMaxHeight()) {
            throw new IllegalArgumentException("Generated block exceeds world height");
        }
        cells.put(p, material);
    }

    private final class BuildJob {
        private final Kind kind;
        private final World world;
        private final int centerX;
        private final int centerY;
        private final int centerZ;
        private final double yaw;
        private final int maxChanges;
        private final int blocksPerTick;
        private final int supportDepth;
        private final UUID operator;
        private final Map<Pos, BlockState> snapshots = new LinkedHashMap<>();
        private final Set<ChunkPos> loadedByJob = new LinkedHashSet<>();
        private final Set<ChunkPos> unloadedAtStart = new LinkedHashSet<>();
        private final List<Cell> placement = new ArrayList<>();
        private final List<Cell> support = new ArrayList<>();
        private Phase phase = Phase.CLEAR;
        private int clearX = -kindRadiusPlaceholder();
        private int clearZ = -kindRadiusPlaceholder();
        private int clearY;
        private int supportIndex;
        private int placementIndex;
        private int processed;
        private String failure = "unknown";
        private List<BlockState> rollbackStates;
        private int rollbackIndex;
        private BukkitTask task;

        private BuildJob(Kind kind, World world, int centerX, int centerY, int centerZ, double yaw,
                         int maxChanges, int blocksPerTick, int supportDepth, UUID operator) {
            this.kind = kind;
            this.world = world;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.yaw = yaw;
            this.maxChanges = maxChanges;
            this.blocksPerTick = blocksPerTick;
            this.supportDepth = supportDepth;
            this.operator = operator;
            this.clearX = -kind.radius;
            this.clearZ = -kind.radius;
            this.clearY = centerY + 1;
        }

        private int kindRadiusPlaceholder() { return 0; }
    }
}
