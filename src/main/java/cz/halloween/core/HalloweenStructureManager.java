package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Procedural, deterministic structures for explicit administrator commands.
 * Automatic world decoration is handled separately with a strict world allow-list.
 */
public final class HalloweenStructureManager {
    private record Pos(int x, int y, int z) {}
    private record Change(Pos pos, BlockData before, BlockData after) {}

    private static final int MAX_TERRAIN_DELTA = 32;

    private final HalloweenCore plugin;
    private final Set<BuildJob> activeJobs = new LinkedHashSet<>();

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
        int rotation = Math.floorMod(Math.round(origin.getYaw() / 90.0f), 4);

        World world = player.getWorld();
        int baseY;
        Map<Pos, Material> plan;
        try {
            // Load only chunks covered by this manually requested structure.
            for (int x = centerX - radius; x <= centerX + radius; x += 16) {
                for (int z = centerZ - radius; z <= centerZ + radius; z += 16) {
                    world.getChunkAt(x >> 4, z >> 4);
                }
            }
            baseY = findGroundY(world, centerX, centerZ);
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
                Change change = job.changes.get(job.cursor++);
                Block block = job.world.getBlockAt(change.pos().x(), change.pos().y(), change.pos().z());
                block.setBlockData(change.after(), false);
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
                job.world.getBlockAt(change.pos().x(), change.pos().y(), change.pos().z())
                        .setBlockData(change.before(), false);
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
                if (!square && dx * dx + dz * dz > radius * radius) continue;
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
                for (int y = baseY + 1; y <= clearTop; y++) put(plan, x, y, z, Material.AIR);
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

        switch (type) {
            case "cemetery" -> addCemetery(plan, cx, baseY, cz, radius, rotation);
            case "village" -> addVillage(plan, cx, baseY, cz, radius, rotation);
            case "arena", "goblinarena" -> addArena(plan, cx, baseY, cz, radius, rotation, false);
            case "vampirearena" -> addArena(plan, cx, baseY, cz, radius, rotation, true);
            default -> throw new IllegalArgumentException("neznámý typ");
        }
        return plan;
    }

    private void addCemetery(Map<Pos, Material> plan, int cx, int y, int cz, int r, int rotation) {
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
        for (int gx : new int[]{-5, -2, 2, 5}) {
            for (int gz : new int[]{-4, 0, 4}) {
                local(plan, cx, y, cz, gx, gz, rotation, Material.MOSSY_COBBLESTONE);
                local(plan, cx, y + 1, cz, gx, gz, rotation, Material.COBBLESTONE_WALL);
                local(plan, cx, y + 2, cz, gx, gz, rotation,
                        ((gx + gz) & 1) == 0 ? Material.POLISHED_BLACKSTONE_BRICK_WALL : Material.MOSSY_COBBLESTONE_WALL);
                if (((gx + gz) & 1) == 0) local(plan, cx, y + 1, cz, gx + 1, gz, rotation, Material.RED_CANDLE);
            }
        }
        // Compact crypt at the rear of the graveyard.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = 4; dz <= 8; dz++) {
                boolean wall = Math.abs(dx) == 3 || dz == 4 || dz == 8;
                local(plan, cx, y, cz, dx, dz, rotation, Material.POLISHED_DEEPSLATE);
                for (int dy = 1; dy <= 3; dy++) {
                    if (wall) local(plan, cx, y + dy, cz, dx, dz, rotation, Material.MOSSY_STONE_BRICKS);
                }
                if (wall && dz == 8 && Math.abs(dx) <= 1) local(plan, cx, y + 1, cz, dx, dz, rotation, Material.AIR);
            }
        }
        local(plan, cx, y + 4, cz, 0, 6, rotation, Material.DEEPSLATE_BRICK_SLAB);
        local(plan, cx, y + 5, cz, 0, 6, rotation, Material.SOUL_LANTERN);
    }

    private void addVillage(Map<Pos, Material> plan, int cx, int y, int cz, int r, int rotation) {
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

        addHouse(plan, cx, y, cz, rotation, -9, -9, Material.DARK_OAK_PLANKS);
        addHouse(plan, cx, y, cz, rotation, 9, -9, Material.SPRUCE_PLANKS);
        addHouse(plan, cx, y, cz, rotation, -9, 9, Material.SPRUCE_PLANKS);
        addHouse(plan, cx, y, cz, rotation, 9, 9, Material.DARK_OAK_PLANKS);

        for (int x : new int[]{-13, -11, 11, 13}) {
            local(plan, cx, y + 1, cz, x, 4, rotation, Material.MOSSY_COBBLESTONE_WALL);
            local(plan, cx, y + 2, cz, x, 4, rotation, Material.COBBLESTONE_WALL);
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
        int rx;
        int rz;
        switch (rotation) {
            case 1 -> { rx = -dz; rz = dx; }
            case 2 -> { rx = -dx; rz = -dz; }
            case 3 -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }
        put(plan, cx + rx, y, cz + rz, material);
    }

    private void put(Map<Pos, Material> plan, int x, int y, int z, Material material) {
        plan.put(new Pos(x, y, z), material);
    }

    private int radius(String type) {
        return switch (type) {
            case "cemetery" -> 10;
            case "village" -> 17;
            case "arena" -> 20;
            case "goblinarena" -> 22;
            case "vampirearena" -> 48;
            default -> throw new IllegalArgumentException("neznámý typ");
        };
    }

    private int clearance(String type) {
        return switch (type) {
            case "cemetery" -> 14;
            case "village" -> 20;
            case "arena" -> 12;
            case "goblinarena" -> 13;
            case "vampirearena" -> 15;
            default -> 12;
        };
    }

    private String normalizeType(String requested) {
        if (requested == null) return null;
        return switch (requested.trim().toLowerCase(Locale.ROOT)) {
            case "hřbitov", "hrbitov", "cemetery", "graveyard" -> "cemetery";
            case "vesnice", "village", "haunted-village" -> "village";
            case "aréna", "arena" -> "arena";
            case "vampirearena", "vampire-arena", "vampire" -> "vampirearena";
            case "goblinarena", "goblin-arena", "goblin" -> "goblinarena";
            default -> null;
        };
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
