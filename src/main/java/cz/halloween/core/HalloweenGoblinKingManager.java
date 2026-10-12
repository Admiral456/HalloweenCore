package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dedicated Goblin King encounter, completely separate from the Vampire King.
 *
 * The arena builder only places blocks after an air-only preflight and an explicit
 * confirm command. If a build fails during the same server tick, changed blocks are
 * rolled back to air. A world backup is still recommended before building.
 */
public final class HalloweenGoblinKingManager implements Listener {
    public static final int ARENA_RADIUS = 31;
    private static final int ARENA_HEIGHT = 16;
    private static final int PREFLIGHT_RADIUS = ARENA_RADIUS + 1;
    private static final double TARGET_RANGE = 44.0D;

    public record ArenaInspection(boolean clear, String issue, Location center, int radius,
                                  int minX, int maxX, int minZ, int maxZ, int minY, int maxY,
                                  int obstructions, String firstObstruction) {
        public String boundsText() {
            if (center == null || center.getWorld() == null) return "neznámé hranice";
            return center.getWorld().getName() + " X[" + minX + ".." + maxX + "] Z[" + minZ + ".." + maxZ
                    + "] Y[" + minY + ".." + maxY + "]";
        }
    }

    private final HalloweenCore plugin;
    private final NamespacedKey bossKey;
    private final NamespacedKey minionKey;
    private final Set<UUID> participants = new HashSet<>();
    private final List<UUID> minionIds = new ArrayList<>();
    private Location pendingCenter;
    private UUID bossId;
    private Location arenaCenter;
    private BukkitTask bossTask;
    private BossBar bossBar;
    private long nextAbilityAt;
    private int phase = 1;
    private boolean rewardEnabled;
    private boolean stopping;
    private final Set<String> modelWarnings = new HashSet<>();

    public HalloweenGoblinKingManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "goblin_king_boss");
        this.minionKey = new NamespacedKey(plugin, "goblin_king_minion");
    }

    public boolean isArenaConfigured() {
        return plugin.getConfig().getBoolean("bosses.goblin.arena.configured", false);
    }

    public boolean isArenaGenerated() {
        return plugin.getConfig().getBoolean("bosses.goblin.arena.generated", false);
    }

    public boolean isBossActive() {
        Entity entity = bossId == null ? null : Bukkit.getEntity(bossId);
        return entity instanceof LivingEntity living && living.isValid() && !living.isDead();
    }

    public String statusText() {
        String world = plugin.getConfig().getString("bosses.goblin.arena.world", "");
        boolean enabled = plugin.getConfig().getBoolean("bosses.goblin.enabled", true);
        return "Král skřítků " + (isBossActive() ? "AKTIVNÍ" : "neaktivní")
                + " | povolení=" + enabled
                + " | aréna=" + (isArenaConfigured() ? world : "nenastavena")
                + " | vygenerována=" + isArenaGenerated()
                + " | fáze=" + (isBossActive() ? phase : 0)
                + " | test bez odměn=" + (!rewardEnabled && isBossActive());
    }

    public ArenaInspection previewArena(Location requestedCenter, CommandSender actor) {
        ArenaInspection inspection = inspectArena(requestedCenter, actor);
        if (inspection.clear()) {
            pendingCenter = normalizedCenter(requestedCenter);
        } else {
            pendingCenter = null;
        }
        return inspection;
    }

    public void cancelArenaPlan() {
        pendingCenter = null;
    }

    public String confirmArena(CommandSender actor) {
        if (pendingCenter == null) {
            return "Nemáš připravený náhled. Zadej nejdřív /halloween goblin arena <world> <x> <y> <z>.";
        }
        ArenaInspection inspection = inspectArena(pendingCenter, actor);
        if (!inspection.clear()) {
            pendingCenter = null;
            return "Stavba zrušena při opakované kontrole: " + inspection.issue()
                    + (inspection.firstObstruction() == null ? "" : " — " + inspection.firstObstruction());
        }

        Map<Block, Material> rollback = new HashMap<>();
        try {
            buildFortress(inspection.center(), rollback);
            plugin.getConfig().set("bosses.goblin.arena.configured", true);
            plugin.getConfig().set("bosses.goblin.arena.generated", true);
            plugin.getConfig().set("bosses.goblin.arena.world", inspection.center().getWorld().getName());
            plugin.getConfig().set("bosses.goblin.arena.x", inspection.center().getX());
            plugin.getConfig().set("bosses.goblin.arena.y", inspection.center().getY());
            plugin.getConfig().set("bosses.goblin.arena.z", inspection.center().getZ());
            plugin.getConfig().set("bosses.goblin.arena.radius", ARENA_RADIUS);
            plugin.saveConfig();
            pendingCenter = null;
            return "Gobliní pevnost byla postavena. Hranice: " + inspection.boundsText()
                    + ". Počet změněných pozic: " + rollback.size() + ".";
        } catch (RuntimeException exception) {
            for (Map.Entry<Block, Material> entry : rollback.entrySet()) {
                try {
                    entry.getKey().setType(entry.getValue(), false);
                } catch (RuntimeException rollbackError) {
                    plugin.getLogger().severe("[GoblinArena] rollback selhal u " + entry.getKey().getLocation()
                            + ": " + rollbackError.getClass().getSimpleName());
                }
            }
            pendingCenter = null;
            plugin.getLogger().severe("[GoblinArena] stavba selhala; rollback na vzduch proveden. Důvod: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return "Stavba selhala a byly vráceny všechny bloky změněné tímto pokusem. Zkontroluj konzoli.";
        }
    }

    public ArenaInspection inspectArena(Location requestedCenter, CommandSender actor) {
        Location center = normalizedCenter(requestedCenter);
        if (center == null || center.getWorld() == null) {
            return rejected("Svět nebo souřadnice arény nejsou platné.", null, 0, null);
        }
        World world = center.getWorld();
        int bx = center.getBlockX();
        int by = center.getBlockY();
        int bz = center.getBlockZ();
        if (by < world.getMinHeight() + 1 || by + ARENA_HEIGHT >= world.getMaxHeight() - 1) {
            return rejected("Aréna se nevejde do povolených výškových hranic tohoto světa.", center, 0, null);
        }
        int minChunkX = (bx - PREFLIGHT_RADIUS) >> 4;
        int maxChunkX = (bx + PREFLIGHT_RADIUS) >> 4;
        int minChunkZ = (bz - PREFLIGHT_RADIUS) >> 4;
        int maxChunkZ = (bz + PREFLIGHT_RADIUS) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return rejected("Všechny chunky v oblasti musí být již načtené; stavba je nenačítá násilně.",
                            center, 0, "chunk " + cx + "," + cz + " není načten");
                }
            }
        }

        int obstructions = 0;
        String first = null;
        for (int dx = -PREFLIGHT_RADIUS; dx <= PREFLIGHT_RADIUS; dx++) {
            for (int dz = -PREFLIGHT_RADIUS; dz <= PREFLIGHT_RADIUS; dz++) {
                if ((dx * dx) + (dz * dz) > PREFLIGHT_RADIUS * PREFLIGHT_RADIUS) continue;
                for (int y = by; y <= by + ARENA_HEIGHT; y++) {
                    Material type = world.getBlockAt(bx + dx, y, bz + dz).getType();
                    if (!type.isAir()) {
                        obstructions++;
                        if (first == null) {
                            first = type + " na " + (bx + dx) + "," + y + "," + (bz + dz);
                        }
                    }
                }
                // If WorldGuard is installed, fail closed unless build is explicitly permitted.
                // Sampling every other floor cell and every perimeter cell catches region boundaries
                // without spending thousands of reflection calls on vertical air blocks.
                if (((dx & 1) == 0 && (dz & 1) == 0)
                        || Math.abs((dx * dx) + (dz * dz) - (ARENA_RADIUS * ARENA_RADIUS)) <= ARENA_RADIUS) {
                    if (!worldGuardAllows(world.getBlockAt(bx + dx, by, bz + dz).getLocation(), actor)) {
                        return rejected("WorldGuard nepovoluje stavbu v celé plánované oblasti. Použij oprávnění halloweencore.admin.worldguard-bypass jen v oblasti, kde smíš stavět.",
                                center, obstructions, first);
                    }
                }
            }
        }
        if (obstructions > 0) {
            return new ArenaInspection(false, "Oblast není prázdná; stavba nepřepisuje existující bloky.",
                    center, ARENA_RADIUS, bx - ARENA_RADIUS, bx + ARENA_RADIUS,
                    bz - ARENA_RADIUS, bz + ARENA_RADIUS, by, by + ARENA_HEIGHT,
                    obstructions, first);
        }
        return new ArenaInspection(true, "OK", center, ARENA_RADIUS,
                bx - ARENA_RADIUS, bx + ARENA_RADIUS, bz - ARENA_RADIUS, bz + ARENA_RADIUS,
                by, by + ARENA_HEIGHT, 0, null);
    }

    private ArenaInspection rejected(String issue, Location center, int obstructions, String first) {
        int x = center == null ? 0 : center.getBlockX();
        int y = center == null ? 0 : center.getBlockY();
        int z = center == null ? 0 : center.getBlockZ();
        return new ArenaInspection(false, issue, center, ARENA_RADIUS,
                x - ARENA_RADIUS, x + ARENA_RADIUS, z - ARENA_RADIUS, z + ARENA_RADIUS,
                y, y + ARENA_HEIGHT, obstructions, first);
    }

    private Location normalizedCenter(Location location) {
        if (location == null || location.getWorld() == null
                || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())) {
            return null;
        }
        return new Location(location.getWorld(), location.getBlockX() + 0.5D,
                location.getBlockY(), location.getBlockZ() + 0.5D);
    }

    private boolean worldGuardAllows(Location location, CommandSender actor) {
        Plugin worldGuard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (worldGuard == null || !worldGuard.isEnabled()) return true;
        if (actor != null && actor.hasPermission("halloweencore.admin.worldguard-bypass")) return true;
        if (!(actor instanceof Player player)) return false;
        try {
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object worldEditLocation = adapterClass.getMethod("adapt", Location.class).invoke(null, location);
            Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
            Object wgPlugin = wgPluginClass.getMethod("inst").invoke(null);
            Object localPlayer = wgPluginClass.getMethod("wrapPlayer", Player.class).invoke(wgPlugin, player);
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Object buildFlag = flagsClass.getField("BUILD").get(null);
            for (Method method : query.getClass().getMethods()) {
                if (!method.getName().equals("testState") || method.getParameterCount() != 3
                        || !method.getParameterTypes()[0].isInstance(worldEditLocation)
                        || !method.getParameterTypes()[1].isInstance(localPlayer)
                        || !method.getParameterTypes()[2].isArray()) continue;
                Object flags = Array.newInstance(method.getParameterTypes()[2].getComponentType(), 1);
                Array.set(flags, 0, buildFlag);
                Object answer = method.invoke(query, worldEditLocation, localPlayer, flags);
                return Boolean.TRUE.equals(answer);
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().warning("[GoblinArena] WorldGuard API nešlo ověřit. Stavba se odmítá bezpečně: "
                    + exception.getClass().getSimpleName());
            return false;
        }
    }

    private void buildFortress(Location center, Map<Block, Material> rollback) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int floorY = center.getBlockY();
        int cz = center.getBlockZ();

        // Combat floor: concentric rings with radial accents keep the center easy to read.
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                double radius = Math.sqrt(dx * dx + dz * dz);
                if (radius > ARENA_RADIUS) continue;
                setGenerated(world.getBlockAt(cx + dx, floorY, cz + dz),
                        floorMaterial(dx, dz, radius), rollback);
            }
        }

        // Broken blackstone perimeter with four intentional entrance gaps and gate arches.
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                double radius = Math.sqrt(dx * dx + dz * dz);
                if (radius < ARENA_RADIUS - 0.65D || radius > ARENA_RADIUS + 0.35D) continue;
                if (isGateGap(dx, dz)) continue;
                for (int dy = 1; dy <= 4; dy++) {
                    Material wall = dy == 4 ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS
                            : Material.POLISHED_BLACKSTONE_BRICK_WALL;
                    setGenerated(world.getBlockAt(cx + dx, floorY + dy, cz + dz), wall, rollback);
                }
                if (((dx + dz) & 3) == 0) {
                    setGenerated(world.getBlockAt(cx + dx, floorY + 5, cz + dz),
                            Material.POLISHED_BLACKSTONE_BRICKS, rollback);
                }
            }
        }

        // Eight low towers and warm lighting frame the fight without obstructing the combat floor.
        for (int ix = -1; ix <= 1; ix++) {
            for (int iz = -1; iz <= 1; iz++) {
                if (ix == 0 && iz == 0) continue;
                int tx = ix * (ix == 0 ? 0 : 21);
                int tz = iz * (iz == 0 ? 0 : 21);
                if (Math.abs(tx) == 21 && Math.abs(tz) == 21) {
                    tx = (int) Math.round(tx * 0.78D);
                    tz = (int) Math.round(tz * 0.78D);
                }
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dy = 1; dy <= 6; dy++) {
                            Material pillar = (dx == 0 && dz == 0) ? Material.POLISHED_BLACKSTONE_BRICKS
                                    : Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
                            setGenerated(world.getBlockAt(cx + tx + dx, floorY + dy, cz + tz + dz), pillar, rollback);
                        }
                    }
                }
                setGenerated(world.getBlockAt(cx + tx, floorY + 7, cz + tz), Material.SOUL_LANTERN, rollback);
                setGenerated(world.getBlockAt(cx + tx, floorY + 6, cz + tz), Material.GILDED_BLACKSTONE, rollback);
            }
        }

        // The throne and altar are placed north of the clear 7-block boss spawn circle.
        buildThrone(world, cx, floorY, cz - 14, rollback);
        buildGate(world, cx, floorY, cz, 0, -1, rollback);
        buildGate(world, cx, floorY, cz, 0, 1, rollback);
        buildGate(world, cx, floorY, cz, -1, 0, rollback);
        buildGate(world, cx, floorY, cz, 1, 0, rollback);

        // Rune pylons mark the boss's outer attack boundary.
        for (int xOffset : new int[]{-8, 8}) {
            for (int zOffset : new int[]{-8, 8}) {
                setGenerated(world.getBlockAt(cx + xOffset, floorY + 1, cz + zOffset), Material.GILDED_BLACKSTONE, rollback);
                setGenerated(world.getBlockAt(cx + xOffset, floorY + 2, cz + zOffset), Material.CRYING_OBSIDIAN, rollback);
                setGenerated(world.getBlockAt(cx + xOffset, floorY + 3, cz + zOffset), Material.SOUL_LANTERN, rollback);
            }
        }
    }

    private Material floorMaterial(int dx, int dz, double radius) {
        if (Math.abs(radius - 7.0D) < 0.48D || Math.abs(radius - 15.0D) < 0.48D
                || Math.abs(radius - 23.0D) < 0.48D || Math.abs(radius - 30.0D) < 0.48D) {
            return Material.GILDED_BLACKSTONE;
        }
        if (radius < 5.0D) return Material.MOSSY_STONE_BRICKS;
        if ((dx == 0 || dz == 0) && radius < 27.0D) return Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
        if ((Math.abs(dx) == Math.abs(dz)) && radius > 8.0D && radius < 27.0D) return Material.MOSS_BLOCK;
        if (Math.floorMod(dx * 31 + dz * 17, 47) == 0) return Material.CRACKED_POLISHED_BLACKSTONE_BRICKS;
        if (radius < 15.0D) return Material.POLISHED_BLACKSTONE_BRICKS;
        return Material.DEEPSLATE_BRICKS;
    }

    private boolean isGateGap(int dx, int dz) {
        return (Math.abs(dx) <= 2 && Math.abs(Math.abs(dz) - ARENA_RADIUS) <= 0.75D)
                || (Math.abs(dz) <= 2 && Math.abs(Math.abs(dx) - ARENA_RADIUS) <= 0.75D);
    }

    private void buildGate(World world, int cx, int floorY, int cz, int ax, int az, Map<Block, Material> rollback) {
        int px = cx + ax * ARENA_RADIUS;
        int pz = cz + az * ARENA_RADIUS;
        for (int side : new int[]{-3, 3}) {
            for (int dy = 1; dy <= 6; dy++) {
                setGenerated(world.getBlockAt(px + (az != 0 ? side : 0), floorY + dy,
                        pz + (ax != 0 ? side : 0)), dy == 6 ? Material.GILDED_BLACKSTONE
                        : Material.POLISHED_BLACKSTONE_BRICKS, rollback);
            }
        }
        for (int across = -3; across <= 3; across++) {
            setGenerated(world.getBlockAt(px + (az != 0 ? across : 0), floorY + 7,
                    pz + (ax != 0 ? across : 0)), Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, rollback);
        }
        setGenerated(world.getBlockAt(px, floorY + 5, pz), Material.SOUL_LANTERN, rollback);
    }

    private void buildThrone(World world, int cx, int floorY, int cz, Map<Block, Material> rollback) {
        for (int step = 0; step < 4; step++) {
            int half = 4 - step;
            for (int x = -half; x <= half; x++) {
                for (int z = -1; z <= 1; z++) {
                    setGenerated(world.getBlockAt(cx + x, floorY + 1 + step, cz + z),
                            step == 3 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS, rollback);
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            setGenerated(world.getBlockAt(cx + x, floorY + 5, cz), Material.RED_NETHER_BRICKS, rollback);
            setGenerated(world.getBlockAt(cx + x, floorY + 6, cz), Material.POLISHED_BLACKSTONE_BRICKS, rollback);
        }
        for (int z = -1; z <= 1; z++) {
            setGenerated(world.getBlockAt(cx - 3, floorY + 5, cz + z), Material.POLISHED_BLACKSTONE_BRICKS, rollback);
            setGenerated(world.getBlockAt(cx + 3, floorY + 5, cz + z), Material.POLISHED_BLACKSTONE_BRICKS, rollback);
        }
        setGenerated(world.getBlockAt(cx, floorY + 7, cz), Material.SOUL_LANTERN, rollback);
        setGenerated(world.getBlockAt(cx, floorY + 5, cz - 1), Material.CRYING_OBSIDIAN, rollback);
    }

    private void setGenerated(Block block, Material material, Map<Block, Material> rollback) {
        if (!rollback.containsKey(block)) {
            if (!block.getType().isAir()) {
                throw new IllegalStateException("Block changed after preflight: " + block.getLocation());
            }
            rollback.put(block, block.getType());
        }
        block.setType(material, false);
    }

    public boolean spawnEncounter(boolean testOnly) {
        if (isBossActive() || !plugin.getConfig().getBoolean("bosses.goblin.enabled", true)
                || !isArenaConfigured() || !isArenaGenerated()) return false;
        double requiredProgress = Math.max(0.0D, Math.min(100.0D,
                plugin.getConfig().getDouble("bosses.goblin.unlock-global-progress-percent", 50.0D)));
        if (!testOnly && plugin.getService().getGlobalProgressPercent() < requiredProgress) return false;
        Location center = configuredCenter();
        if (center == null || center.getWorld() == null) return false;
        if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return false;

        Location spawn = center.clone().add(0.0D, 1.0D, 0.0D);
        // Stage A fails closed: never present a vanilla piglin as the custom Goblin King.
        LivingEntity spawned = plugin.getMobManager().spawnCustomEncounterEntity(spawn, "goblin-king");
        if (spawned == null) return false;
        bossId = spawned.getUniqueId();
        arenaCenter = center.clone();
        phase = 1;
        nextAbilityAt = System.currentTimeMillis() + 4_000L;
        rewardEnabled = !testOnly;
        participants.clear();
        minionIds.clear();
        spawned.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        spawned.setCustomName(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "KRÁL SKŘÍTKŮ");
        spawned.setCustomNameVisible(true);
        spawned.setPersistent(true);
        spawned.setRemoveWhenFarAway(false);
        spawned.setCanPickupItems(false);
        spawned.setGlowing(false);
        setAttribute(spawned, Attribute.MAX_HEALTH, plugin.getConfig().getDouble("bosses.goblin.health", 500.0D));
        setAttribute(spawned, Attribute.ATTACK_DAMAGE, plugin.getConfig().getDouble("bosses.goblin.damage", 14.0D));
        setAttribute(spawned, Attribute.MOVEMENT_SPEED, plugin.getConfig().getDouble("bosses.goblin.movement-speed", 0.28D));
        setAttribute(spawned, Attribute.KNOCKBACK_RESISTANCE, plugin.getConfig().getDouble("bosses.goblin.knockback-resistance", 0.85D));
        AttributeInstance health = spawned.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) spawned.setHealth(Math.min(health.getValue(), health.getBaseValue()));
        bossBar = Bukkit.createBossBar("KRÁL SKŘÍTKŮ", BarColor.GREEN, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        UUID entranceBossId = spawned.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity current = Bukkit.getEntity(entranceBossId);
            if (current instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                playGoblinAnimation(living, "wing_spread");
            }
        }, 2L);
        Location entrance = spawned.getLocation().clone().add(0.0D, 1.8D, 0.0D);
        entrance.getWorld().playSound(entrance, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7F, 0.65F);
        entrance.getWorld().spawnParticle(Particle.DUST, entrance, 18, 0.35D, 0.35D, 0.35D, 0.0D,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 255, 45), 1.25F));
        stopping = false;
        bossTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBoss, 10L, 10L);
        broadcastArena(testOnly
                ? ChatColor.YELLOW + "Testovací souboj s Králem goblinů začíná. Odměny jsou vypnuté."
                : ChatColor.DARK_GREEN + "KRÁL SKŘÍTKŮ PROCITL! Jeho pevnost se uzavřela.");
        return true;
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(Math.max(0.0D, value));
    }

    private Location configuredCenter() {
        String worldName = plugin.getConfig().getString("bosses.goblin.arena.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                plugin.getConfig().getDouble("bosses.goblin.arena.x"),
                plugin.getConfig().getDouble("bosses.goblin.arena.y"),
                plugin.getConfig().getDouble("bosses.goblin.arena.z"));
    }

    private void tickBoss() {
        if (stopping || bossId == null) return;
        Entity entity = Bukkit.getEntity(bossId);
        if (!(entity instanceof LivingEntity boss) || boss.isDead() || !boss.isValid()) {
            endEncounter(false);
            return;
        }
        if (arenaCenter == null || arenaCenter.getWorld() == null || !boss.getWorld().getUID().equals(arenaCenter.getWorld().getUID())) {
            endEncounter(false);
            return;
        }

        double ratio = boss.getHealth() / Math.max(1.0D, boss.getAttribute(Attribute.MAX_HEALTH) == null
                ? boss.getHealth() : boss.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (ratio <= 0.70D && phase < 2) {
            phase = 2;
            playGoblinAnimation(boss, "phase_transition");
            broadcastArena(ChatColor.GOLD + "Král skřítků rozevřel křídla a povolal svou stráž!");
            spawnMinions(boss, 2);
        }
        if (ratio <= 0.35D && phase < 3) {
            phase = 3;
            playGoblinAnimation(boss, "phase_transition");
            broadcastArena(ChatColor.RED + "Král skřítků uvolňuje sílu smaragdové koruny — pozor na runové výbuchy!");
            boss.getWorld().spawnParticle(Particle.DUST, boss.getLocation().add(0, 2.0D, 0),
                    42, 0.8D, 0.7D, 0.8D, 0.0D,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(107, 255, 40), 1.4F));
        }

        updateBossBar(boss);
        emitOrbMagic(boss);
        Player target = chooseTarget(boss);
        if (target == null) {
            if (boss instanceof Mob mob) mob.setTarget(null);
            return;
        }
        if (boss instanceof Mob mob) mob.setTarget(target);

        double distanceFromCenter = boss.getLocation().distance(arenaCenter);
        double leash = Math.max(12.0D, ARENA_RADIUS - 2.0D);
        if (distanceFromCenter > leash) {
            Vector toward = arenaCenter.toVector().subtract(boss.getLocation().toVector());
            if (toward.lengthSquared() > 0.001D) boss.setVelocity(toward.normalize().multiply(0.45D));
        }
        if (distanceFromCenter > ARENA_RADIUS + 7.0D) {
            boss.teleport(arenaCenter.clone().add(0.0D, 1.0D, 0.0D));
        }

        long now = System.currentTimeMillis();
        if (now < nextAbilityAt) return;
        if (phase == 1) {
            telegraphedCleave(boss, target, 12.0D, 3.7D, 20L);
            nextAbilityAt = now + plugin.getConfig().getLong("bosses.goblin.phase-one-cooldown-ms", 8_000L);
        } else if (phase == 2) {
            if (countMinions() < Math.max(1, Math.min(4, plugin.getConfig().getInt("bosses.goblin.max-minions", 3)))) {
                spawnMinions(boss, 1);
            }
            telegraphedCleave(boss, target, 14.0D, 4.2D, 18L);
            nextAbilityAt = now + plugin.getConfig().getLong("bosses.goblin.phase-two-cooldown-ms", 7_000L);
        } else {
            arenaRupture(boss, target);
            nextAbilityAt = now + plugin.getConfig().getLong("bosses.goblin.phase-three-cooldown-ms", 9_000L);
        }
    }

    private Player chooseTarget(LivingEntity boss) {
        Player best = null;
        double bestDistance = TARGET_RANGE * TARGET_RANGE;
        for (Player player : boss.getWorld().getPlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player) || player.isDead()
                    || !player.getWorld().getUID().equals(arenaCenter.getWorld().getUID())) continue;
            double distance = player.getLocation().distanceSquared(arenaCenter);
            if (distance > (ARENA_RADIUS + 4.0D) * (ARENA_RADIUS + 4.0D)) continue;
            double mobDistance = player.getLocation().distanceSquared(boss.getLocation());
            if (mobDistance > bestDistance) continue;
            if (boss instanceof Mob mob && !mob.hasLineOfSight(player)) continue;
            best = player;
            bestDistance = mobDistance;
        }
        return best;
    }

    private void emitOrbMagic(LivingEntity boss) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;
        org.bukkit.util.Vector forward = boss.getLocation().getDirection().setY(0.0D);
        if (forward.lengthSquared() < 0.001D) forward = new org.bukkit.util.Vector(0, 0, 1);
        forward.normalize();
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-forward.getZ(), 0.0D, forward.getX());
        Location orb = boss.getLocation().clone().add(0.0D, 2.05D, 0.0D)
                .add(right.multiply(0.62D)).add(forward.multiply(0.15D));
        boss.getWorld().spawnParticle(Particle.DUST, orb, 2, 0.08D, 0.09D, 0.08D, 0.0D,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(100, 255, 45), 0.95F));
    }

    /** Plays a named animation on the custom ModelEngine blueprint without a hard dependency. */
    private boolean playGoblinAnimation(LivingEntity target, String animation) {
        if (target == null || !target.isValid() || target.isDead() || animation == null || animation.isBlank()) {
            return false;
        }
        Plugin modelEngine = plugin.getServer().getPluginManager().getPlugin("ModelEngine");
        if (modelEngine == null || !modelEngine.isEnabled()) return false;
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object modeledEntity = apiClass.getMethod("getModeledEntity", Entity.class).invoke(null, target);
            if (modeledEntity == null) return false;
            Class<?> modeledEntityApi = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Object modelResult = modeledEntityApi.getMethod("getModel", String.class)
                    .invoke(modeledEntity, "halloween_goblin_king");
            if (!(modelResult instanceof java.util.Optional<?> optional) || optional.isEmpty()) return false;
            Object activeModel = optional.get();
            Class<?> activeModelApi = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Object handler = activeModelApi.getMethod("getAnimationHandler").invoke(activeModel);
            Class<?> handlerApi = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler");
            Method play;
            try {
                play = handlerApi.getMethod("playAnimation",
                        String.class, double.class, double.class, double.class, boolean.class);
            } catch (NoSuchMethodException ex) {
                warnModelOnce("animation-api", "ModelEngine animation API is incompatible with Goblin King clip playback.");
                return false;
            }
            Object result = play.invoke(handler, animation, 0.08D, 0.12D, 1.0D, true);
            return result != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            warnModelOnce("animation-" + animation, "Goblin King animation '" + animation + "' could not start: "
                    + ex.getClass().getSimpleName());
            return false;
        }
    }

    private void warnModelOnce(String key, String message) {
        if (modelWarnings.add(key)) plugin.getLogger().warning(message);
    }

    private void updateBossBar(LivingEntity boss) {
        if (bossBar == null) return;
        AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? boss.getHealth() : maxHealth.getValue();
        bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, boss.getHealth() / Math.max(1.0D, max))));
        String phaseName = phase == 1 ? "I" : phase == 2 ? "II" : "III";
        bossBar.setTitle(ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "KRÁL SKŘÍTKŮ "
                + ChatColor.YELLOW + "• FÁZE " + phaseName);
        for (Player viewer : new ArrayList<>(bossBar.getPlayers())) {
            if (!viewer.isOnline() || !viewer.getWorld().getUID().equals(arenaCenter.getWorld().getUID())
                    || viewer.getLocation().distanceSquared(arenaCenter) > 55.0D * 55.0D) {
                bossBar.removePlayer(viewer);
            }
        }
        for (Player viewer : arenaCenter.getWorld().getPlayers()) {
            if (plugin.isEligibleGameplayPlayer(viewer)
                    && viewer.getLocation().distanceSquared(arenaCenter) <= 55.0D * 55.0D
                    && !bossBar.getPlayers().contains(viewer)) bossBar.addPlayer(viewer);
        }
    }

    private void telegraphedCleave(LivingEntity boss, Player target, double damage, double radius, long delay) {
        playGoblinAnimation(boss, "staff_strike");
        Location mark = target.getLocation().clone();
        World world = mark.getWorld();
        if (world == null) return;
        warningRing(world, mark, radius, Particle.CRIT);
        world.playSound(mark, org.bukkit.Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0F, 0.55F);
        target.sendMessage(ChatColor.GOLD + "Král skřítků zvedá svou sekeru — uskoč z označeného místa!");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!encounterStillValid(boss, target, world, mark)) return;
            world.spawnParticle(Particle.SWEEP_ATTACK, mark.clone().add(0.0D, 1.0D, 0.0D), 16, 0.8D, 0.6D, 0.8D, 0.0D);
            world.playSound(mark, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
            if (target.getLocation().distanceSquared(mark) <= radius * radius) {
                target.damage(Math.max(0.0D, damage), boss);
                Vector away = target.getLocation().toVector().subtract(boss.getLocation().toVector());
                away.setY(0.0D);
                if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.75D).setY(0.28D));
            }
        }, Math.max(10L, Math.min(40L, delay)));
    }

    private void arenaRupture(LivingEntity boss, Player target) {
        playGoblinAnimation(boss, "spell_cast");
        Location origin = target.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) return;
        List<Location> marks = List.of(
                origin.clone(),
                origin.clone().add(3.5D, 0.0D, 0.0D),
                origin.clone().add(-3.5D, 0.0D, 0.0D),
                origin.clone().add(0.0D, 0.0D, 3.5D),
                origin.clone().add(0.0D, 0.0D, -3.5D));
        for (Location mark : marks) warningRing(world, mark, 2.3D, Particle.SOUL_FIRE_FLAME);
        world.playSound(origin, org.bukkit.Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0F, 0.65F);
        target.sendMessage(ChatColor.RED + "RUNOVÁ KATASTROFA! Vyhni se žhnoucím kruhům!");
        long delay = Math.max(15L, Math.min(45L, plugin.getConfig().getLong("bosses.goblin.rupture-delay-ticks", 28L)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!encounterStillValid(boss, target, world, origin)) return;
            for (Location mark : marks) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, mark.clone().add(0.0D, 0.25D, 0.0D),
                        26, 0.7D, 0.1D, 0.7D, 0.025D);
            }
            if (target.getLocation().distanceSquared(origin) <= 2.3D * 2.3D) {
                target.damage(plugin.getConfig().getDouble("bosses.goblin.rupture-damage", 16.0D), boss);
            } else {
                for (Location mark : marks.subList(1, marks.size())) {
                    if (target.getLocation().distanceSquared(mark) <= 2.3D * 2.3D) {
                        target.damage(plugin.getConfig().getDouble("bosses.goblin.rupture-damage", 16.0D), boss);
                        break;
                    }
                }
            }
        }, delay);
    }

    private void warningRing(World world, Location center, double radius, Particle particle) {
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D * i) / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15D, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 2, 0.05D, 0.05D, 0.05D, 0.01D);
        }
        world.spawnParticle(Particle.SMOKE, center.clone().add(0, 0.2D, 0), 8, radius * 0.35D, 0.1D, radius * 0.35D, 0.01D);
    }

    private boolean encounterStillValid(LivingEntity boss, Player target, World originalWorld, Location mark) {
        return !stopping && bossId != null && boss.isValid() && !boss.isDead()
                && target.isOnline() && !target.isDead() && plugin.isEligibleGameplayPlayer(target)
                && target.getWorld().getUID().equals(originalWorld.getUID())
                && originalWorld.isChunkLoaded(mark.getBlockX() >> 4, mark.getBlockZ() >> 4);
    }

    private int countMinions() {
        int count = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE)) {
                    count++;
                    if (!minionIds.contains(entity.getUniqueId())) minionIds.add(entity.getUniqueId());
                }
            }
        }
        return count;
    }

    private void spawnMinions(LivingEntity boss, int amount) {
        int cap = Math.max(1, Math.min(4, plugin.getConfig().getInt("bosses.goblin.max-minions", 3)));
        for (int i = 0; i < amount && countMinions() < cap; i++) {
            double angle = (Math.PI * 2.0D * (i + 1)) / Math.max(2, amount + 1);
            Location location = boss.getLocation().clone().add(Math.cos(angle) * 4.0D, 0.0D, Math.sin(angle) * 4.0D);
            World world = location.getWorld();
            if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            if (!location.getBlock().isPassable() || !location.clone().add(0, 1, 0).getBlock().isPassable()
                    || !location.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            Entity entity = world.spawnEntity(location, EntityType.PIGLIN);
            if (!(entity instanceof LivingEntity minion)) {
                entity.remove();
                continue;
            }
            minion.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
            minion.setCustomName(ChatColor.DARK_GREEN + "Strážce gobliního krále");
            minion.setCustomNameVisible(true);
            minion.setRemoveWhenFarAway(false);
            minion.setCanPickupItems(false);
            minion.setPersistent(true);
            setAttribute(minion, Attribute.MAX_HEALTH, plugin.getConfig().getDouble("bosses.goblin.minion-health", 24.0D));
            setAttribute(minion, Attribute.ATTACK_DAMAGE, plugin.getConfig().getDouble("bosses.goblin.minion-damage", 5.0D));
            AttributeInstance hp = minion.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) minion.setHealth(hp.getValue());
            if (minion instanceof Mob mob) mob.setTarget(chooseTarget(boss));
            minionIds.add(minion.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!isGoblinBoss(event.getEntity())) return;
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker != null && plugin.isEligibleGameplayPlayer(attacker)) participants.add(attacker.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            broadcastArena(ChatColor.GREEN + "KRÁL SKŘÍTKŮ PADL! Cesta ke koruně je volná.");
            if (rewardEnabled) {
                long reward = Math.max(0L, plugin.getConfig().getLong("bosses.goblin.reward-fragments", 1500L));
                for (UUID participantId : new HashSet<>(participants)) {
                    Player player = Bukkit.getPlayer(participantId);
                    if (reward > 0L && player != null && plugin.isEligibleGameplayPlayer(player)) {
                        plugin.getService().addFragments(player.getUniqueId(), reward, "goblin-king");
                        player.sendMessage(ChatColor.GOLD + "Za vítězství nad Králem goblinů získáváš " + reward + " fragmentů.");
                    }
                }
            } else {
                broadcastArena(ChatColor.GRAY + "Testovací souboj skončil bez odměn.");
            }
            endEncounter(false);
        } else if (entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE)) {
            minionIds.remove(entity.getUniqueId());
        }
    }

    public boolean isManagedCombatEntity(Entity entity) {
        return entity != null && (entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)
                || entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE));
    }

    private boolean isGoblinBoss(Entity entity) {
        return entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) return player;
        return null;
    }

    private void broadcastArena(String message) {
        if (arenaCenter == null || arenaCenter.getWorld() == null) return;
        for (Player player : arenaCenter.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(arenaCenter) <= 80.0D * 80.0D) {
                player.sendMessage(message);
            }
        }
    }

    public void stopEncounter() {
        endEncounter(true);
    }

    private void endEncounter(boolean removeBoss) {
        stopping = true;
        if (bossTask != null) {
            bossTask.cancel();
            bossTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }
        if (removeBoss && bossId != null) {
            Entity entity = Bukkit.getEntity(bossId);
            if (entity != null && entity.isValid()) entity.remove();
        }
        bossId = null;
        participants.clear();
        phase = 1;
        rewardEnabled = false;
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE)) entity.remove();
            }
        }
        minionIds.clear();
        arenaCenter = null;
        stopping = false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (bossBar != null) bossBar.removePlayer(event.getPlayer());
        participants.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        stopEncounter();
        cancelArenaPlan();
    }

}
