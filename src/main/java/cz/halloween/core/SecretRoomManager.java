package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SecretRoomManager implements Listener {
    private static final int ROOM_WIDTH = 7;
    private static final int ROOM_HEIGHT = 5;
    private static final int ROOM_DEPTH = 7;
    private static final int MAX_ACCESS_TUNNEL_LENGTH = 14;

    private static final Set<Material> NATURAL_STONE = Set.of(
            Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.CALCITE, Material.DRIPSTONE_BLOCK,
            Material.SMOOTH_BASALT
    );

    // Exact palette used by all three generated room tiers and their entrance arches.
    private static final Set<Material> ROOM_SHELL_MATERIALS = Set.of(
            Material.DEEPSLATE_BRICKS, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE_TILES,
            Material.CRACKED_DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE, Material.CHISELED_DEEPSLATE,
            Material.AMETHYST_BLOCK, Material.GILDED_BLACKSTONE, Material.GOLD_BLOCK,
            Material.POLISHED_BLACKSTONE_BRICKS, Material.CHISELED_POLISHED_BLACKSTONE
    );

    private final HalloweenCore plugin;
    private final KeyItemFactory keyItemFactory;
    private final NamespacedKey lockedKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey managedKey;
    private final NamespacedKey chunkAttemptedKey;
    private final NamespacedKey chunkHasRoomKey;
    private final File dataFile;
    private final YamlConfiguration data;

    public SecretRoomManager(HalloweenCore plugin, KeyItemFactory keyItemFactory) {
        this.plugin = plugin;
        this.keyItemFactory = keyItemFactory;
        this.lockedKey = new NamespacedKey(plugin, "secret_room_locked");
        this.rarityKey = new NamespacedKey(plugin, "secret_room_rarity");
        this.managedKey = new NamespacedKey(plugin, "secret_room_managed");
        this.chunkAttemptedKey = new NamespacedKey(plugin, "secret_room_attempted_v1");
        this.chunkHasRoomKey = new NamespacedKey(plugin, "secret_room_anchor_v1");
        this.dataFile = new File(plugin.getDataFolder(), "secret-rooms.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        migrateLegacyRoomRecords();
    }

    /**
     * Earlier builds stored room coordinates as rooms.<id>.*. Preserve those locations
     * when upgrading to the list-based locate registry, so old generated rooms remain findable.
     */
    private void migrateLegacyRoomRecords() {
        ConfigurationSection legacy = data.getConfigurationSection("rooms");
        if (legacy == null) return;
        List<Map<?, ?>> locations = new ArrayList<>(data.getMapList("locations"));
        int added = 0;
        for (String id : legacy.getKeys(false)) {
            String path = "rooms." + id;
            String worldName = data.getString(path + ".world");
            if (worldName == null || worldName.isBlank()
                    || !data.contains(path + ".x") || !data.contains(path + ".y") || !data.contains(path + ".z")) {
                continue;
            }
            int x = data.getInt(path + ".x");
            int y = data.getInt(path + ".y");
            int z = data.getInt(path + ".z");
            boolean exists = locations.stream().anyMatch(entry ->
                    worldName.equals(String.valueOf(entry.get("world")))
                            && value(entry.get("x")) == x && value(entry.get("y")) == y && value(entry.get("z")) == z);
            if (exists) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", id);
            entry.put("world", worldName);
            entry.put("x", x);
            entry.put("y", y);
            entry.put("z", z);
            locations.add(entry);
            added++;
        }
        if (added > 0) {
            data.set("locations", locations);
            data.set("stats.generated", Math.max(data.getInt("stats.generated", 0), locations.size()));
            save();
            plugin.getLogger().info("Migrated " + added + " existing secret-room location(s) for /locate.");
        }
    }

    /**
     * Re-attach protection to registered chests from previous builds, even if already opened.
     * Only already-loaded chunks are inspected; this never loads terrain as a side effect.
     */
    public void protectRecordedChestsInLoadedChunks() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) markRecordedChestsInChunk(chunk);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onRecordedRoomChunkLoad(ChunkLoadEvent event) {
        markRecordedChestsInChunk(event.getChunk());
    }

    private void markRecordedChestsInChunk(Chunk chunk) {
        String worldName = chunk.getWorld().getName();
        for (Map<?, ?> entry : data.getMapList("locations")) {
            if (!worldName.equals(String.valueOf(entry.get("world")))) continue;
            int x = (int) value(entry.get("x"));
            int y = (int) value(entry.get("y"));
            int z = (int) value(entry.get("z"));
            if ((x >> 4) != chunk.getX() || (z >> 4) != chunk.getZ()) continue;

            Block block = chunk.getWorld().getBlockAt(x, y, z);
            if (block.getType() != Material.CHEST || !(block.getState() instanceof Chest chest)) continue;
            var pdc = chest.getPersistentDataContainer();
            boolean changed = false;
            if (!pdc.has(managedKey, PersistentDataType.BYTE)) {
                pdc.set(managedKey, PersistentDataType.BYTE, (byte) 1);
                changed = true;
            }
            if (!pdc.has(rarityKey, PersistentDataType.STRING)) {
                pdc.set(rarityKey, PersistentDataType.STRING,
                        KeyItemFactory.Rarity.fromId(String.valueOf(entry.get("rarity"))).id());
                changed = true;
            }
            if (changed) chest.update(true, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNewChunk(ChunkLoadEvent event) {
        if (!event.isNewChunk() || !plugin.getConfig().getBoolean("rooms.enabled", true)) return;
        Chunk chunk = event.getChunk();
        if (!isAllowedWorld(chunk.getWorld())) return;

        var chunkData = chunk.getPersistentDataContainer();
        if (chunkData.has(chunkAttemptedKey, PersistentDataType.BYTE)) return;

        // One roll per newly generated chunk. The marker is stored on the chunk,
        // so reloads cannot silently re-roll an unsuccessful chunk forever.
        chunkData.set(chunkAttemptedKey, PersistentDataType.BYTE, (byte) 1);
        incrementStat("stats.chunks-checked", false);

        double chance = clamp(plugin.getConfig().getDouble("rooms.chance-per-new-chunk", 0.025D), 0.0D, 1.0D);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            saveIfCounterCheckpoint();
            return;
        }

        incrementStat("stats.chance-passed", false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!chunk.isLoaded() || !isAllowedWorld(chunk.getWorld())) return;
            if (hasRoomInChunk(chunk)) return;

            int attempts = clamp(plugin.getConfig().getInt("rooms.attempts-per-chunk", 48), 1, 256);
            if (!generateRoomInChunk(chunk, attempts)) {
                incrementStat("stats.generation-failed", true);
                return;
            }
            chunk.getPersistentDataContainer().set(chunkHasRoomKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.CHEST) return;

        BlockState state = clicked.getState();
        if (!(state instanceof Chest chest) || !isLocked(chest)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        KeyItemFactory.Rarity requiredRarity = getChestRarity(chest);
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean inMainHand;
        if (keyItemFactory.isKey(mainHand, requiredRarity)) {
            inMainHand = true;
        } else if (keyItemFactory.isKey(offHand, requiredRarity)) {
            inMainHand = false;
        } else {
            boolean hasWrongKey = keyItemFactory.isKey(mainHand) || keyItemFactory.isKey(offHand);
            if (hasWrongKey) {
                player.sendMessage(plugin.color("&5&lZAMČENÁ TRUHLA &8» &7Tahle truhla vyžaduje klíč vzácnosti "
                        + requiredRarity.color() + requiredRarity.displayName() + "&7."));
            } else {
                player.sendMessage(plugin.color("&5&lZAMČENÁ TRUHLA &8» &7Potřebuješ klíč vzácnosti "
                        + requiredRarity.color() + requiredRarity.displayName() + "&7."));
            }
            return;
        }

        consumeOne(player, inMainHand);
        long fragmentReward = populateLoot(chest, requiredRarity);
        if (fragmentReward > 0L) {
            plugin.getQuestManager().awardFragments(player, fragmentReward);
        }
        chest.getPersistentDataContainer().remove(lockedKey);
        // Keep managed/rarity markers permanently so an unlocked chest remains protected.
        chest.update(true, false);
        player.sendMessage(plugin.color("&5&lTRUHLA ODEMČENA &8» &d" + requiredRarity.displayName()
                + " truhla je odemčená. Klíč byl spotřebován."));
        if (fragmentReward > 0L) {
            player.sendMessage(plugin.color("&d+" + fragmentReward + " fragmentů &7za otevření tajné truhly."));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 0.8f, 0.8f);

        Block chestBlock = clicked;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && chestBlock.getType() == Material.CHEST
                    && chestBlock.getState() instanceof Chest unlockedChest) {
                player.openInventory(unlockedChest.getBlockInventory());
            }
        });
    }

    /**
     * Generates the chest contents once, at the successful unlock. Loot tables contain
     * only vanilla materials, so no extra custom item textures or models are needed.
     */
    private long populateLoot(Chest chest, KeyItemFactory.Rarity rarity) {
        String root = "rooms.loot." + rarity.id();
        Inventory inventory = chest.getBlockInventory();
        inventory.clear();

        List<Map<?, ?>> entries = plugin.getConfig().getMapList(root + ".items");
        for (Map<?, ?> entry : entries) {
            Object rawMaterial = entry.get("material");
            if (!(rawMaterial instanceof String materialName)) continue;
            Material material = Material.matchMaterial(materialName);
            if (material == null || material.isAir() || material.isLegacy()) {
                plugin.getLogger().warning("Ignoring invalid secret-room loot material '" + materialName
                        + "' in " + root + ".items");
                continue;
            }
            double chance = number(entry.get("chance"), 1.0D);
            if (ThreadLocalRandom.current().nextDouble() >= Math.max(0.0D, Math.min(1.0D, chance))) continue;

            int min = Math.max(1, (int) number(entry.get("min"), 1.0D));
            int max = Math.max(min, (int) number(entry.get("max"), min));
            int amount = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
            ItemStack item = new ItemStack(material, Math.min(amount, 2304));
            applyConfiguredEnchant(item, entry.get("enchantments"));
            Map<Integer, ItemStack> leftovers = inventory.addItem(item);
            leftovers.values().forEach(leftover ->
                    chest.getWorld().dropItemNaturally(chest.getLocation().add(0.5, 0.5, 0.5), leftover));
        }
        chest.update(true, false);

        long minFragments = Math.max(0L, plugin.getConfig().getLong(root + ".min-fragments", 0L));
        long maxFragments = Math.max(minFragments, plugin.getConfig().getLong(root + ".max-fragments", minFragments));
        if (maxFragments <= minFragments) return minFragments;
        long width = maxFragments - minFragments;
        if (width == Long.MAX_VALUE) return ThreadLocalRandom.current().nextLong(minFragments, maxFragments);
        return minFragments + ThreadLocalRandom.current().nextLong(width + 1L);
    }

    private void applyConfiguredEnchant(ItemStack item, Object rawOptions) {
        if (!(rawOptions instanceof List<?> options) || options.isEmpty()) return;
        Object selected = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        String[] parts = String.valueOf(selected).split(":", 2);
        if (parts.length != 2) return;
        int level;
        try {
            level = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return;
        }
        if (level < 1 || level > 5) return;
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
        if (enchantment == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta book) {
            book.addStoredEnchant(enchantment, level, true);
            item.setItemMeta(book);
        } else if (meta != null) {
            item.addUnsafeEnchantment(enchantment, level);
        }
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedChestBreak(BlockBreakEvent event) {
        if (!isProtectedChest(event.getBlock())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(plugin.color("&5&lCHRÁNĚNÁ TRUHLA &8» &7Tuhle tajnou truhlu nelze rozbít."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedChestBurn(BlockBurnEvent event) {
        if (isProtectedChest(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        // Removes protected chest from the blast list, including TNT and creepers.
        event.blockList().removeIf(this::isProtectedChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtectedChest);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedChestPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtectedChest)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedChestPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtectedChest)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedChestEntityChange(EntityChangeBlockEvent event) {
        // Also stop destructive entity-driven block changes, e.g. wither block breaking.
        if (isProtectedChest(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedChestDispense(BlockDispenseEvent event) {
        // Dropper/dispenser mechanics must not be an alternate automated chest interface.
        if (!(event.getBlock().getBlockData() instanceof Directional directional)) return;
        Block target = event.getBlock().getRelative(directional.getFacing());
        if (isProtectedChest(target)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedChestTransfer(InventoryMoveItemEvent event) {
        // Blocks automatic insertion/extraction by hoppers and hopper minecarts.
        // Manual player interaction with an unlocked chest remains allowed.
        if (isProtectedInventory(event.getSource()) || isProtectedInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtectedInventory(Inventory inventory) {
        if (inventory == null) return false;
        if (isProtectedHolder(inventory.getHolder())) return true;
        Location location = inventory.getLocation();
        return location != null && isProtectedChest(location.getBlock());
    }

    private boolean isProtectedHolder(InventoryHolder holder) {
        if (holder instanceof Chest chest) return isProtectedChest(chest.getBlock());
        if (holder instanceof DoubleChest doubleChest) {
            return isProtectedHolder(doubleChest.getLeftSide())
                    || isProtectedHolder(doubleChest.getRightSide());
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerEnterSecretRoom(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) return;
        Player player = event.getPlayer();
        if (!isInsideRecordedRoom(event.getTo())) return;

        for (String questId : plugin.getQuestManager().getCatalogIds()) {
            String objectiveType = plugin.getQuestManager().getObjectiveType(questId);
            boolean explorationQuest = objectiveType.equalsIgnoreCase("explore-room")
                    || objectiveType.equalsIgnoreCase("explore-and-deliver");
            if (!explorationQuest
                    || !plugin.getQuestManager().isActive(player.getUniqueId(), questId)
                    || plugin.getQuestManager().getProgress(player.getUniqueId(), questId)
                    >= plugin.getQuestManager().getTarget(questId)) {
                continue;
            }
            if (plugin.getQuestManager().addProgress(player, questId, 1)) {
                player.sendMessage(plugin.color("&5&lTAJNÁ MÍSTNOST &8» &dPrůzkumný postup uložen: "
                        + plugin.getQuestManager().getTitle(questId) + ". Vrať se k zadavateli."));
            }
        }
    }

    public int getGeneratedCount() {
        return Math.max(data.getMapList("locations").size(), Math.max(0, data.getInt("stats.generated", 0)));
    }

    public long getCheckedChunkCount() {
        return Math.max(0L, data.getLong("stats.chunks-checked", 0L));
    }

    public long getChancePassedCount() {
        return Math.max(0L, data.getLong("stats.chance-passed", 0L));
    }

    public long getGenerationFailedCount() {
        return Math.max(0L, data.getLong("stats.generation-failed", 0L));
    }

    public String getLastRoomSummary() {
        if (!data.contains("last.world")) return "zatím žádná";
        return data.getString("last.world", "?") + " " + data.getInt("last.x", 0) + " "
                + data.getInt("last.y", 0) + " " + data.getInt("last.z", 0) + " ("
                + KeyItemFactory.Rarity.fromId(data.getString("last.rarity", "common")).displayName() + ")";
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save secret-room data: " + ex.getMessage());
        }
    }

    /**
     * Admin test generator. It scans a small radius around the admin in the current
     * Overworld and still requires a completely natural-rock outer shell. It never
     * overwrites player-placed blocks, ores in the shell, liquids or containers.
     */
    public boolean generateTestRoom(Player player, int radiusChunks) {
        if (player == null || !player.isOnline() || !isAllowedWorld(player.getWorld())) return false;
        int radius = clamp(radiusChunks, 1, 6);
        int centerX = player.getLocation().getChunk().getX();
        int centerZ = player.getLocation().getChunk().getZ();
        List<ChunkOffset> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                offsets.add(new ChunkOffset(dx, dz, dx * dx + dz * dz));
            }
        }
        offsets.sort(Comparator.comparingInt(ChunkOffset::distanceSquared));

        int attempts = clamp(plugin.getConfig().getInt("rooms.manual-attempts-per-chunk", 96), 1, 512);
        for (ChunkOffset offset : offsets) {
            Chunk chunk = player.getWorld().getChunkAt(centerX + offset.dx(), centerZ + offset.dz());
            if (!isAllowedWorld(chunk.getWorld()) || hasRoomInChunk(chunk)) continue;
            if (generateRoomInChunk(chunk, attempts)) {
                chunk.getPersistentDataContainer().set(chunkHasRoomKey, PersistentDataType.BYTE, (byte) 1);
                incrementStat("stats.manual-generated", true);
                return true;
            }
        }
        incrementStat("stats.manual-failed", true);
        return false;
    }

    public void locateNearest(Player player, double maxDistance) {
        if (player == null) return;
        List<Map<?, ?>> locations = currentWorldLocations(player.getWorld().getName());
        if (locations.isEmpty()) {
            player.sendMessage(plugin.color("&5&lTAJNÉ MÍSTNOSTI &8» &7V tomto světě zatím není zaznamenaná žádná vygenerovaná místnost."));
            player.sendMessage(plugin.color("&7Zkontroluj /halloween rooms status nebo jako správce použij /halloween rooms generate."));
            return;
        }

        Location origin = player.getLocation();
        Map<?, ?> nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Map<?, ?> entry : locations) {
            double dx = origin.getX() - value(entry.get("x"));
            double dy = origin.getY() - value(entry.get("y"));
            double dz = origin.getZ() - value(entry.get("z"));
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = entry;
            }
        }

        double distance = Math.sqrt(nearestDistanceSquared);
        if (maxDistance > 0.0D && distance > maxDistance) {
            player.sendMessage(plugin.color("&5&lTAJNÉ MÍSTNOSTI &8» &7Ve vzdálenosti do &d"
                    + Math.round(maxDistance) + " &7bloků žádná místnost není."));
            return;
        }

        int x = (int) Math.floor(value(nearest.get("x")));
        int y = (int) Math.floor(value(nearest.get("y")));
        int z = (int) Math.floor(value(nearest.get("z")));
        player.sendMessage(plugin.color("&5&lNEJBLIŽŠÍ TAJNÁ MÍSTNOST"));
        player.sendMessage(plugin.color("&7Svět: &f" + player.getWorld().getName()));
        player.sendMessage(plugin.color("&7Souřadnice truhly: &dX " + x + " Y " + y + " Z " + z));
        player.sendMessage(plugin.color("&7Vzdálenost: &d" + Math.round(distance) + " bloků"));
        KeyItemFactory.Rarity roomRarity = KeyItemFactory.Rarity.fromId(String.valueOf(nearest.get("rarity")));
        player.sendMessage(plugin.color("&7Vzácnost: " + roomRarity.color() + roomRarity.displayName()));
        player.sendMessage(plugin.color("&8Místnost je ukrytá v přírodním kameni; hledej bezpečný vstup."));
    }

    private List<BlockState> snapshotCandidateBlocks(World world, int x, int y, int z,
                                                       AccessTunnel accessTunnel) {
        List<BlockState> states = new ArrayList<>();
        for (int dx = 0; dx < ROOM_WIDTH; dx++) {
            for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                for (int dz = 0; dz < ROOM_DEPTH; dz++) {
                    states.add(world.getBlockAt(x + dx, y + dy, z + dz).getState());
                }
            }
        }
        for (TunnelBlock block : accessTunnel.blocks()) {
            states.add(world.getBlockAt(block.x(), y + 1, block.z()).getState());
            states.add(world.getBlockAt(block.x(), y + 2, block.z()).getState());
        }
        return states;
    }

    private void restoreCandidateBlocks(List<BlockState> states) {
        for (int index = states.size() - 1; index >= 0; index--) {
            BlockState state = states.get(index);
            if (!state.update(true, false)) {
                plugin.getLogger().severe("Could not roll back a failed secret-room candidate at "
                        + state.getWorld().getName() + " " + state.getX() + " " + state.getY() + " "
                        + state.getZ() + ".");
            }
        }
    }

    private boolean isRoomStructureIntact(World world, int chestX, int chestY, int chestZ) {
        int x = chestX - ROOM_WIDTH / 2;
        int y = chestY - 1;
        int z = chestZ - ROOM_DEPTH / 2;

        for (int dx = 0; dx < ROOM_WIDTH; dx++) {
            for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                for (int dz = 0; dz < ROOM_DEPTH; dz++) {
                    boolean shell = dx == 0 || dx == ROOM_WIDTH - 1
                            || dz == 0 || dz == ROOM_DEPTH - 1
                            || dy == 0 || dy == ROOM_HEIGHT - 1;
                    if (!shell) continue;

                    boolean possibleDoorCell = (dy == 1 || dy == 2)
                            && (((dx == 0 || dx == ROOM_WIDTH - 1) && dz == ROOM_DEPTH / 2)
                            || ((dz == 0 || dz == ROOM_DEPTH - 1) && dx == ROOM_WIDTH / 2));
                    Material shellType = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    if (possibleDoorCell && shellType.isAir()) continue;
                    // Water, lava, arbitrary blocks, and missing shell blocks must not count as intact.
                    if (!ROOM_SHELL_MATERIALS.contains(shellType)) return false;
                }
            }
        }

        Direction[] directions = {
                new Direction(-1, 0), new Direction(1, 0),
                new Direction(0, -1), new Direction(0, 1)
        };
        int doorways = 0;
        for (Direction direction : directions) {
            int wallX = direction.dx() < 0 ? x
                    : direction.dx() > 0 ? x + ROOM_WIDTH - 1 : x + ROOM_WIDTH / 2;
            int wallZ = direction.dz() < 0 ? z
                    : direction.dz() > 0 ? z + ROOM_DEPTH - 1 : z + ROOM_DEPTH / 2;
            boolean lowerAir = world.getBlockAt(wallX, y + 1, wallZ).getType().isAir();
            boolean upperAir = world.getBlockAt(wallX, y + 2, wallZ).getType().isAir();
            if (!lowerAir && !upperAir) continue;
            if (!(lowerAir && upperAir)) return false;
            doorways++;

            int outsideX = wallX + direction.dx();
            int outsideZ = wallZ + direction.dz();
            if (world.isChunkLoaded(outsideX >> 4, outsideZ >> 4)) {
                if (!world.getBlockAt(outsideX, y + 1, outsideZ).getType().isAir()
                        || !world.getBlockAt(outsideX, y + 2, outsideZ).getType().isAir()) {
                    return false;
                }
            }
        }
        return doorways == 1;
    }

    public void verifyRecordedRooms(CommandSender sender) {
        if (!sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(plugin.color("&cOvěření tajných truhel je pouze pro správce."));
            return;
        }
        int checked = 0, intact = 0, locked = 0, broken = 0, unloaded = 0;
        for (Map<?, ?> entry : data.getMapList("locations")) {
            World world = Bukkit.getWorld(String.valueOf(entry.get("world")));
            if (world == null) { unloaded++; continue; }
            int x = (int) value(entry.get("x"));
            int y = (int) value(entry.get("y"));
            int z = (int) value(entry.get("z"));
            if (!world.isChunkLoaded(x >> 4, z >> 4)) { unloaded++; continue; }
            checked++;
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() != Material.CHEST || !isProtectedChest(block)
                    || !isRoomStructureIntact(world, x, y, z)) { broken++; continue; }
            intact++;
            if (isLockedChest(block)) locked++;
        }
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
        sender.sendMessage(plugin.color("&5&lKONTROLA TAJNÝCH TRUHEL"));
        sender.sendMessage(plugin.color("&7Záznamy zkontrolované v načtených chunkech: &d" + checked));
        sender.sendMessage(plugin.color("&7Truhly nalezené a chráněné: &a" + intact));
        sender.sendMessage(plugin.color("&7Z toho stále zamčené: &d" + locked));
        sender.sendMessage(plugin.color("&7Chybějící nebo bez ochrany: " + (broken == 0 ? "&a" : "&c") + broken));
        sender.sendMessage(plugin.color("&7Přeskočené nenačtené světy/chunky: &e" + unloaded));
        sender.sendMessage(plugin.color("&8Nenačtené chunky se kvůli kontrole nenačítají."));
        sender.sendMessage(plugin.color("&8&m--------------------------------"));
    }

    public void locateAll(CommandSender sender) {
        if (!sender.hasPermission("halloweencore.admin")) {
            sender.sendMessage(plugin.color("&cVýpis všech souřadnic je pouze pro správce."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cTento příkaz vyžaduje hráče a jeho svět."));
            return;
        }
        List<Map<?, ?>> locations = currentWorldLocations(player.getWorld().getName());
        if (locations.isEmpty()) {
            player.sendMessage(plugin.color("&7V tomto světě zatím nejsou zaznamenané žádné tajné místnosti."));
            return;
        }
        player.sendMessage(plugin.color("&5&lTAJNÉ MÍSTNOSTI &8» &7" + locations.size() + " nalezených záznamů:"));
        locations.stream()
                .sorted(Comparator.comparingDouble(entry -> distanceSquared(player.getLocation(), entry)))
                .forEach(entry -> player.sendMessage(plugin.color("&d" + (int) value(entry.get("x")) + " "
                        + (int) value(entry.get("y")) + " " + (int) value(entry.get("z"))
                        + " &8— &7" + KeyItemFactory.Rarity.fromId(String.valueOf(entry.get("rarity"))).displayName()
                        + " &8— &7" + Math.round(Math.sqrt(distanceSquared(player.getLocation(), entry))) + " bloků")));
    }

    private boolean generateRoomInChunk(Chunk chunk, int attempts) {
        World world = chunk.getWorld();
        if (!isAllowedWorld(world) || hasRoomInChunk(chunk)) return false;

        int minY = Math.max(world.getMinHeight() + 8, plugin.getConfig().getInt("rooms.min-y", -36));
        int maxY = Math.min(world.getMaxHeight() - ROOM_HEIGHT - 2,
                plugin.getConfig().getInt("rooms.max-y", -8));
        if (maxY < minY) return false;

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        for (int attempt = 0; attempt < attempts; attempt++) {
            // The entire 7x7 footprint remains inside the current chunk.
            int x = baseX + 2 + ThreadLocalRandom.current().nextInt(8);
            int z = baseZ + 2 + ThreadLocalRandom.current().nextInt(8);
            int y = minY + ThreadLocalRandom.current().nextInt(maxY - minY + 1);
            if (!isSafeRoomVolume(world, x, y, z)) continue;

            // Do not create a sealed stone box. A safe path into a nearby natural cave
            // must be found before this location is accepted or any blocks are changed.
            AccessTunnel accessTunnel = findAccessTunnel(world, x, y, z);
            if (accessTunnel == null) continue;

            KeyItemFactory.Rarity rarity = rollRarity("rooms.rarity-weights");
            List<BlockState> originalBlocks = snapshotCandidateBlocks(world, x, y, z, accessTunnel);
            buildRoom(world, x, y, z, accessTunnel, rarity);
            int chestX = x + ROOM_WIDTH / 2;
            int chestY = y + 1;
            int chestZ = z + ROOM_DEPTH / 2;
            if (!isProtectedChest(world.getBlockAt(chestX, chestY, chestZ))
                    || !isRoomStructureIntact(world, chestX, chestY, chestZ)) {
                restoreCandidateBlocks(originalBlocks);
                plugin.getLogger().warning("Room candidate was rolled back after failing the post-build integrity check at "
                        + world.getName() + " " + chestX + " " + chestY + " " + chestZ + ".");
                return false;
            }
            recordGeneratedRoom(world, chestX, chestY, chestZ, rarity);
            chunk.getPersistentDataContainer().set(chunkHasRoomKey, PersistentDataType.BYTE, (byte) 1);
            plugin.getLogger().info("Generated " + rarity.id() + " secret room at " + world.getName() + " "
                    + (x + 3) + " " + (y + 1) + " " + (z + 3) + ".");
            return true;
        }
        return false;
    }

    private boolean isSafeRoomVolume(World world, int x, int y, int z) {
        for (int dx = 0; dx < ROOM_WIDTH; dx++) {
            for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                for (int dz = 0; dz < ROOM_DEPTH; dz++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    Material type = block.getType();
                    boolean shell = dx == 0 || dx == ROOM_WIDTH - 1
                            || dz == 0 || dz == ROOM_DEPTH - 1
                            || dy == 0 || dy == ROOM_HEIGHT - 1;

                    if (shell) {
                        if (!NATURAL_STONE.contains(type)) return false;
                    } else if (!(NATURAL_STONE.contains(type) || type.isAir() || isOre(type))) {
                        return false;
                    }
                    // Keep containers, tile entities, plants, structures, fluids and
                    // non-natural blocks out of every generated footprint.
                    if (block.getState() instanceof org.bukkit.block.TileState) return false;
                }
            }
        }

        Material chestMaterial = world.getBlockAt(x + ROOM_WIDTH / 2, y + 1, z + ROOM_DEPTH / 2).getType();
        return NATURAL_STONE.contains(chestMaterial) || chestMaterial.isAir();
    }

    private boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    private Material roomShellMaterial(KeyItemFactory.Rarity rarity, int dx, int dy, int dz) {
        if (rarity == KeyItemFactory.Rarity.RARE) {
            if ((dx + dy + dz) % 9 == 0) return Material.AMETHYST_BLOCK;
            if (dy == 0 && (dx + dz) % 2 == 0) return Material.POLISHED_DEEPSLATE;
            if ((dx + dy + dz) % 7 == 0) return Material.CHISELED_DEEPSLATE;
            return Material.POLISHED_DEEPSLATE;
        }
        if (rarity == KeyItemFactory.Rarity.LEGENDARY) {
            if ((dx + dy + dz) % 8 == 0) return Material.GILDED_BLACKSTONE;
            if ((dx == 0 || dx == ROOM_WIDTH - 1) && (dz == 0 || dz == ROOM_DEPTH - 1)
                    && dy > 0 && dy < ROOM_HEIGHT - 1) return Material.GOLD_BLOCK;
            if (dy == 0 && (dx + dz) % 2 == 0) return Material.POLISHED_BLACKSTONE_BRICKS;
            if ((dx + dy + dz) % 7 == 0) return Material.CHISELED_POLISHED_BLACKSTONE;
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }

        boolean corner = (dx == 0 || dx == ROOM_WIDTH - 1)
                && (dz == 0 || dz == ROOM_DEPTH - 1);
        if (corner && dy > 0 && dy < ROOM_HEIGHT - 1) return Material.COBBLED_DEEPSLATE;
        if (dy == 0 && (dx + dz) % 2 == 0) return Material.DEEPSLATE_TILES;
        if (dy == ROOM_HEIGHT - 1 && ThreadLocalRandom.current().nextInt(100) < 12) {
            return Material.CRACKED_DEEPSLATE_BRICKS;
        }
        if (dy > 0 && dy < ROOM_HEIGHT - 1 && (dx + dz + dy) % 7 == 0) {
            return Material.CRACKED_DEEPSLATE_BRICKS;
        }
        return Material.DEEPSLATE_BRICKS;
    }

    private void buildRoom(World world, int x, int y, int z, AccessTunnel accessTunnel,
                           KeyItemFactory.Rarity rarity) {
        for (int dx = 0; dx < ROOM_WIDTH; dx++) {
            for (int dy = 0; dy < ROOM_HEIGHT; dy++) {
                for (int dz = 0; dz < ROOM_DEPTH; dz++) {
                    boolean shell = dx == 0 || dx == ROOM_WIDTH - 1
                            || dz == 0 || dz == ROOM_DEPTH - 1
                            || dy == 0 || dy == ROOM_HEIGHT - 1;
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (shell) {
                        block.setType(roomShellMaterial(rarity, dx, dy, dz), false);
                    } else if (NATURAL_STONE.contains(block.getType())) {
                        block.setType(Material.AIR, false);
                    }
                    // Existing ore inside the room is preserved rather than destroyed.
                }
            }
        }

        // Open only the previously verified natural-stone cells; the far end connects
        // to an already open cave. No ores, containers, fluids, or player blocks are carved.
        for (TunnelBlock tunnelBlock : accessTunnel.blocks()) {
            world.getBlockAt(tunnelBlock.x(), y + 1, tunnelBlock.z()).setType(Material.AIR, false);
            world.getBlockAt(tunnelBlock.x(), y + 2, tunnelBlock.z()).setType(Material.AIR, false);
        }
        decorateEntranceArch(world, x, y, z, accessTunnel.direction(), rarity);

        // Vanilla-only details: no retired Halloween mobs, events, sounds, rewards, or loot.
        setDecorationIfAir(world, x + 1, y + 1, z + 1, Material.SOUL_LANTERN);
        setDecorationIfAir(world, x + ROOM_WIDTH - 2, y + 1, z + 1, Material.SOUL_LANTERN);
        setDecorationIfAir(world, x + 1, y + 1, z + ROOM_DEPTH - 2, Material.LANTERN);
        setDecorationIfAir(world, x + ROOM_WIDTH - 2, y + 1, z + ROOM_DEPTH - 2, Material.LANTERN);
        setDecorationIfAir(world, x + 1, y + ROOM_HEIGHT - 2, z + 1, Material.COBWEB);
        setDecorationIfAir(world, x + ROOM_WIDTH - 2, y + ROOM_HEIGHT - 2,
                z + ROOM_DEPTH - 2, Material.COBWEB);
        setDecorationIfAir(world, x + ROOM_WIDTH / 2, y + ROOM_HEIGHT - 2,
                z + ROOM_DEPTH - 2, Material.COBWEB);

        Block chestBlock = world.getBlockAt(x + ROOM_WIDTH / 2, y + 1, z + ROOM_DEPTH / 2);
        chestBlock.setType(Material.CHEST, false);
        BlockState state = chestBlock.getState();
        if (state instanceof Chest chest) {
            chest.getPersistentDataContainer().set(lockedKey, PersistentDataType.BYTE, (byte) 1);
            chest.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity.id());
            chest.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
            chest.update(true, false);
        }
        // Loot remains empty until its exact contents are agreed; room and lock still have a rarity tier.
    }

    private AccessTunnel findAccessTunnel(World world, int x, int y, int z) {
        Direction[] directions = {
                new Direction(-1, 0), new Direction(1, 0),
                new Direction(0, -1), new Direction(0, 1)
        };
        int first = ThreadLocalRandom.current().nextInt(directions.length);
        for (int rotation = 0; rotation < directions.length; rotation++) {
            Direction direction = directions[(first + rotation) % directions.length];
            int wallX = direction.dx() < 0 ? x
                    : direction.dx() > 0 ? x + ROOM_WIDTH - 1 : x + ROOM_WIDTH / 2;
            int wallZ = direction.dz() < 0 ? z
                    : direction.dz() > 0 ? z + ROOM_DEPTH - 1 : z + ROOM_DEPTH / 2;
            List<TunnelBlock> path = new ArrayList<>();

            for (int step = 0; step <= MAX_ACCESS_TUNNEL_LENGTH; step++) {
                int px = wallX + direction.dx() * step;
                int pz = wallZ + direction.dz() * step;

                // Do not load or generate an adjacent chunk as a side effect of probing.
                // Automatic generation must only inspect chunks already loaded by the server.
                if (!world.isChunkLoaded(px >> 4, pz >> 4)) break;

                Block lower = world.getBlockAt(px, y + 1, pz);
                Block upper = world.getBlockAt(px, y + 2, pz);
                Block ceiling = world.getBlockAt(px, y + 3, pz);

                // World generation uses CAVE_AIR/VOID_AIR inside many natural caves,
                // so treat Bukkit's full air family as a valid opening, not only AIR.
                if (step > 0 && lower.getType().isAir() && upper.getType().isAir()) {
                    if (isNaturalCaveOpening(world, px, y, pz, direction)) {
                        return new AccessTunnel(List.copyOf(path), direction);
                    }
                    break;
                }

                if (!isSafeTunnelCarve(lower) || !isSafeTunnelCarve(upper)
                        || !isSafeTunnelFloor(world.getBlockAt(px, y, pz))
                        || !isSafeTunnelCeiling(ceiling)) {
                    break;
                }
                path.add(new TunnelBlock(px, pz));
            }
        }
        return null;
    }

    private boolean isSafeTunnelCarve(Block block) {
        return NATURAL_STONE.contains(block.getType())
                && !(block.getState() instanceof org.bukkit.block.TileState);
    }

    private boolean isSafeTunnelFloor(Block block) {
        Material type = block.getType();
        return (NATURAL_STONE.contains(type) || isOre(type))
                && !(block.getState() instanceof org.bukkit.block.TileState);
    }

    private boolean isSafeTunnelCeiling(Block block) {
        Material type = block.getType();
        // Avoid digging beneath sand, gravel, fluids, structures, or other falling/
        // non-natural blocks that could collapse into the new tunnel.
        return (NATURAL_STONE.contains(type) || isOre(type))
                && !(block.getState() instanceof org.bukkit.block.TileState);
    }

    private boolean isNaturalCaveOpening(World world, int x, int y, int z, Direction direction) {
        int airBlocks = 0;
        for (int lateral = -1; lateral <= 1; lateral++) {
            int checkX = x + (direction.dx() == 0 ? lateral : 0);
            int checkZ = z + (direction.dz() == 0 ? lateral : 0);
            for (int dy = 1; dy <= 2; dy++) {
                Block block = world.getBlockAt(checkX, y + dy, checkZ);
                if (block.getState() instanceof org.bukkit.block.TileState) return false;
                Material type = block.getType();
                if (type.isAir()) {
                    airBlocks++;
                } else if (!NATURAL_STONE.contains(type) && !isOre(type)) {
                    return false;
                }
            }
        }
        // At least one neighbouring air block avoids a single-block slit.
        return airBlocks >= 3;
    }

    private void decorateEntranceArch(World world, int x, int y, int z, Direction direction,
                                       KeyItemFactory.Rarity rarity) {
        int wallX = direction.dx() < 0 ? x
                : direction.dx() > 0 ? x + ROOM_WIDTH - 1 : x + ROOM_WIDTH / 2;
        int wallZ = direction.dz() < 0 ? z
                : direction.dz() > 0 ? z + ROOM_DEPTH - 1 : z + ROOM_DEPTH / 2;
        for (int side : new int[]{-1, 1}) {
            int pillarX = wallX + (direction.dx() == 0 ? side : 0);
            int pillarZ = wallZ + (direction.dz() == 0 ? side : 0);
            Material pillar = switch (rarity) {
                case COMMON -> Material.POLISHED_BLACKSTONE_BRICKS;
                case RARE -> Material.AMETHYST_BLOCK;
                case LEGENDARY -> Material.GILDED_BLACKSTONE;
            };
            world.getBlockAt(pillarX, y + 1, pillarZ).setType(pillar, false);
            world.getBlockAt(pillarX, y + 2, pillarZ).setType(pillar, false);
        }
        Material keystone = switch (rarity) {
            case COMMON -> Material.CHISELED_DEEPSLATE;
            case RARE -> Material.AMETHYST_BLOCK;
            case LEGENDARY -> Material.GOLD_BLOCK;
        };
        world.getBlockAt(wallX, y + 3, wallZ).setType(keystone, false);
    }

    private void setDecorationIfAir(World world, int x, int y, int z, Material decoration) {
        Block block = world.getBlockAt(x, y, z);
        // Preserve any ore found inside the otherwise valid room volume.
        if (block.getType().isAir()) block.setType(decoration, false);
    }

    private void recordGeneratedRoom(World world, int x, int y, int z, KeyItemFactory.Rarity rarity) {
        List<Map<?, ?>> existing = new ArrayList<>(data.getMapList("locations"));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "room-" + String.format(Locale.ROOT, "%05d", getGeneratedCount() + 1));
        entry.put("world", world.getName());
        entry.put("x", x);
        entry.put("y", y);
        entry.put("z", z);
        entry.put("rarity", rarity.id());
        existing.add(entry);
        data.set("locations", existing);
        data.set("stats.generated", existing.size());
        data.set("last.world", world.getName());
        data.set("last.x", x);
        data.set("last.y", y);
        data.set("last.z", z);
        data.set("last.rarity", rarity.id());
        save();
    }

    private boolean hasRoomInChunk(Chunk chunk) {
        if (chunk.getPersistentDataContainer().has(chunkHasRoomKey, PersistentDataType.BYTE)) return true;
        for (Map<?, ?> entry : data.getMapList("locations")) {
            if (chunk.getWorld().getName().equals(String.valueOf(entry.get("world")))
                    && ((int) value(entry.get("x")) >> 4) == chunk.getX()
                    && ((int) value(entry.get("z")) >> 4) == chunk.getZ()) return true;
        }
        return false;
    }

    private List<Map<?, ?>> currentWorldLocations(String worldName) {
        return data.getMapList("locations").stream()
                .filter(entry -> worldName.equals(String.valueOf(entry.get("world"))))
                .toList();
    }

    private boolean isInsideRecordedRoom(Location location) {
        for (Map<?, ?> entry : currentWorldLocations(location.getWorld().getName())) {
            double dx = Math.abs(location.getBlockX() - value(entry.get("x")));
            double dy = location.getBlockY() - value(entry.get("y"));
            double dz = Math.abs(location.getBlockZ() - value(entry.get("z")));
            if (dx <= 2 && dz <= 2 && dy >= 0 && dy <= 2) return true;
        }
        return false;
    }

    private boolean isLockedChest(Block block) {
        if (block == null || block.getType() != Material.CHEST) return false;
        BlockState state = block.getState();
        return state instanceof Chest chest && isLocked(chest);
    }

    private boolean isProtectedChest(Block block) {
        if (block == null || block.getType() != Material.CHEST) return false;
        BlockState state = block.getState();
        if (!(state instanceof Chest chest)) return false;
        var pdc = chest.getPersistentDataContainer();
        Byte managed = pdc.get(managedKey, PersistentDataType.BYTE);
        Byte locked = pdc.get(lockedKey, PersistentDataType.BYTE);
        return (managed != null && managed == (byte) 1)
                || (locked != null && locked == (byte) 1)
                || pdc.has(rarityKey, PersistentDataType.STRING);
    }

    private boolean isLocked(Chest chest) {
        Byte locked = chest.getPersistentDataContainer().get(lockedKey, PersistentDataType.BYTE);
        return locked != null && locked == (byte) 1;
    }

    private KeyItemFactory.Rarity getChestRarity(Chest chest) {
        String rarity = chest.getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        // Old marked chests have no rarity string and remain openable with the COMMON key.
        return KeyItemFactory.Rarity.fromId(rarity);
    }

    private KeyItemFactory.Rarity rollRarity(String weightPath) {
        int common = Math.max(0, plugin.getConfig().getInt(weightPath + ".common", 70));
        int rare = Math.max(0, plugin.getConfig().getInt(weightPath + ".rare", 25));
        int legendary = Math.max(0, plugin.getConfig().getInt(weightPath + ".legendary", 5));
        long total = (long) common + rare + legendary;
        if (total <= 0L) return KeyItemFactory.Rarity.COMMON;
        long roll = ThreadLocalRandom.current().nextLong(total);
        if (roll < common) return KeyItemFactory.Rarity.COMMON;
        roll -= common;
        if (roll < rare) return KeyItemFactory.Rarity.RARE;
        return KeyItemFactory.Rarity.LEGENDARY;
    }

    private boolean isAllowedWorld(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) return false;
        List<String> blocked = plugin.getConfig().getStringList("rooms.blocked-worlds");
        return blocked.stream().noneMatch(name -> name.equalsIgnoreCase(world.getName()));
    }

    private void incrementStat(String key, boolean saveNow) {
        data.set(key, Math.max(0L, data.getLong(key, 0L)) + 1L);
        if (saveNow || data.getLong("stats.chunks-checked", 0L) % 25L == 0L) save();
    }

    private void saveIfCounterCheckpoint() {
        if (data.getLong("stats.chunks-checked", 0L) % 25L == 0L) save();
    }

    private double distanceSquared(Location playerLocation, Map<?, ?> entry) {
        double dx = playerLocation.getX() - value(entry.get("x"));
        double dy = playerLocation.getY() - value(entry.get("y"));
        double dz = playerLocation.getZ() - value(entry.get("z"));
        return dx * dx + dy * dy + dz * dz;
    }

    private double value(Object object) {
        return object instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() != null && second.getWorld() != null
                && first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private int clamp(int number, int min, int max) {
        return Math.max(min, Math.min(max, number));
    }

    private double clamp(double number, double min, double max) {
        return Math.max(min, Math.min(max, number));
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

    private record ChunkOffset(int dx, int dz, int distanceSquared) {}
    private record Direction(int dx, int dz) {}
    private record TunnelBlock(int x, int z) {}
    private record AccessTunnel(List<TunnelBlock> blocks, Direction direction) {}
}
