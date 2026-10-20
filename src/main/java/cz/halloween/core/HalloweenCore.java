package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HalloweenCore extends JavaPlugin implements Listener {
    private HalloweenQuestManager questManager;
    private SecretRoomManager secretRoomManager;
    private KeyItemFactory keyItemFactory;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateLegacyQuestDefaults();
        removeLegacyConfiguration();
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", 2);
        saveConfig();

        keyItemFactory = new KeyItemFactory(this);
        questManager = new HalloweenQuestManager(this);
        secretRoomManager = new SecretRoomManager(this, keyItemFactory);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(secretRoomManager, this);
        secretRoomManager.protectRecordedChestsInLoadedChunks();

        if (getCommand("halloween") == null) {
            getLogger().severe("Command 'halloween' is missing from plugin.yml; disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        HalloweenCommand command = new HalloweenCommand(this);
        getCommand("halloween").setExecutor(command);
        getCommand("halloween").setTabCompleter(command);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (new HalloweenPlaceholderExpansion(this).register()) {
                getLogger().info("PlaceholderAPI expansion registered.");
            } else {
                getLogger().warning("PlaceholderAPI is installed, but the Halloween expansion could not register.");
            }
        }

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                removed += removeObsoleteHalloweenEntities(chunk);
            }
        }
        if (removed > 0) {
            getLogger().info("Removed " + removed + " old tagged Halloween mob/boss entity(s) from loaded chunks.");
        }

        getLogger().info("HalloweenCore loaded: NPC dialogue quest bridge and locked underground rooms only. "
                + "No legacy mob or event systems are started.");
    }

    @Override
    public void onDisable() {
        if (questManager != null) {
            questManager.save();
        }
        if (secretRoomManager != null) {
            secretRoomManager.save();
        }
    }

    
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSecretRoomLocateAlias(PlayerCommandPreprocessEvent event) {
        if (secretRoomManager == null) return;
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length < 2) return;
        String command = parts[0].toLowerCase(java.util.Locale.ROOT);
        String target = parts[1].toLowerCase(java.util.Locale.ROOT);
        if (command.equals("/locate") && isSecretRoomLocateTarget(target)) {
            double radius = 0.0D;
            if (parts.length > 3) {
                event.getPlayer().sendMessage(color("&cPoužití: /locate halloweenroom [max-vzdálenost]"));
                event.setCancelled(true);
                return;
            }
            if (parts.length == 3) {
                try {
                    radius = Double.parseDouble(parts[2]);
                    if (!Double.isFinite(radius) || radius < 0.0D) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    event.getPlayer().sendMessage(color("&cVzdálenost musí být nezáporné číslo v blocích."));
                    event.setCancelled(true);
                    return;
                }
            }
            event.setCancelled(true);
            secretRoomManager.locateNearest(event.getPlayer(), radius);
            return;
        }

        if (command.equals("/locateall") && isSecretRoomLocateTarget(target)) {
            event.setCancelled(true);
            secretRoomManager.locateAll(event.getPlayer());
        }
    }

    private boolean isSecretRoomLocateTarget(String target) {
        return java.util.Set.of(
                "halloweenroom", "halloweenrooms", "halloween_room", "halloween_rooms",
                "secretroom", "secret_room", "room", "rooms", "skrytamistnost", "skryta_mistnost"
        ).contains(target);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Old tagged encounters can remain in the world after the old managers are removed.
        // Clean them only when the relevant chunk is loaded; never scan or edit unloaded chunks.
        removeObsoleteHalloweenEntities(event.getChunk());
    }

    private int removeObsoleteHalloweenEntities(Chunk chunk) {
        NamespacedKey cursedMob = new NamespacedKey(this, "cursed_mob");
        NamespacedKey eventMob = new NamespacedKey(this, "event_mob");
        NamespacedKey graveEncounterMob = new NamespacedKey(this, "grave_encounter_mob");
        NamespacedKey bloodMoonCaptain = new NamespacedKey(this, "blood_moon_captain");
        NamespacedKey vampireBoss = new NamespacedKey(this, "vampire_boss");
        NamespacedKey headlessRider = new NamespacedKey(this, "headless_nick_rider");
        NamespacedKey headlessHorse = new NamespacedKey(this, "headless_nick_horse");
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            var pdc = entity.getPersistentDataContainer();
            boolean obsolete = pdc.has(cursedMob, PersistentDataType.STRING)
                    || pdc.has(eventMob, PersistentDataType.BYTE)
                    || pdc.has(graveEncounterMob, PersistentDataType.BYTE)
                    || pdc.has(bloodMoonCaptain, PersistentDataType.BYTE)
                    || pdc.has(vampireBoss, PersistentDataType.BYTE)
                    || pdc.has(headlessRider, PersistentDataType.BYTE)
                    || pdc.has(headlessHorse, PersistentDataType.BYTE);
            if (obsolete) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }


    /**
     * Upgrade stock tasks from pre-v2 configs without overwriting custom amounts.
     * A task is migrated only when its requirements still exactly match the known
     * old defaults. Custom quest IDs and customized requirements are preserved.
     */
    private void migrateLegacyQuestDefaults() {
        if (getConfig().getValues(false).containsKey("config-version")) return;

        try (var stream = getResource("config.yml")) {
            if (stream == null) {
                getLogger().warning("Cannot migrate quest defaults: bundled config.yml is missing.");
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            int migrated = 0;
            for (Map.Entry<String, Map<String, Integer>> entry : legacyDefaultRequirements().entrySet()) {
                String id = entry.getKey();
                String root = "quests.definitions." + id;
                if (!matchesRequiredItems(getConfig().getMapList(root + ".required-items"), entry.getValue())) {
                    continue;
                }
                getConfig().set(root + ".required-items", defaults.getMapList(root + ".required-items"));
                String newDescription = defaults.getString(root + ".description");
                if (newDescription != null) getConfig().set(root + ".description", newDescription);
                migrated++;
            }

            updateOldRewardDefault(defaults, "light", 3);
            updateOldRewardDefault(defaults, "medium", 5);
            updateOldRewardDefault(defaults, "hard", 8);
            updateOldRewardDefault(defaults, "very-hard", 12);
            getConfig().set("config-version", 2);
            if (migrated > 0) {
                getLogger().info("Updated " + migrated
                        + " unchanged built-in quest definition(s) to schema v2. Custom quest definitions were preserved.");
            } else {
                getLogger().info("Migrated existing HalloweenCore configuration to schema v2; custom quest definitions were preserved.");
            }
            saveConfig();
        } catch (Exception ex) {
            getLogger().severe("Could not migrate HalloweenCore config to schema v2: " + ex.getMessage());
        }
    }

    private void updateOldRewardDefault(YamlConfiguration defaults, String tier, long oldValue) {
        String path = "quests.default-reward-fragments." + tier;
        if (getConfig().getLong(path, Long.MIN_VALUE) == oldValue) {
            getConfig().set(path, defaults.getLong(path));
        }
    }

    private boolean matchesRequiredItems(List<Map<?, ?>> actual, Map<String, Integer> expected) {
        if (actual.size() != expected.size()) return false;
        Map<String, Integer> parsed = new HashMap<>();
        for (Map<?, ?> requirement : actual) {
            Object id = requirement.get("id");
            Object amount = requirement.get("amount");
            if (!(id instanceof String itemId) || !(amount instanceof Number number)
                    || number.doubleValue() != Math.rint(number.doubleValue())) return false;
            if (parsed.put(itemId.toLowerCase(java.util.Locale.ROOT), number.intValue()) != null) return false;
        }
        return parsed.equals(expected);
    }

    private Map<String, Map<String, Integer>> legacyDefaultRequirements() {
        return Map.ofEntries(
                Map.entry("fresh_harvest", Map.of("minecraft:wheat", 16)),
                Map.entry("stone_for_repairs", Map.of("minecraft:cobblestone", 24)),
                Map.entry("paper_trail", Map.of("minecraft:paper", 12)),
                Map.entry("food_for_watch", Map.of("minecraft:bread", 8)),
                Map.entry("village_library", Map.of("minecraft:book", 4)),
                Map.entry("copper_samples", Map.of("minecraft:copper_ingot", 16)),
                Map.entry("iron_requisition", Map.of("minecraft:iron_ingot", 12)),
                Map.entry("amethyst_study", Map.of("minecraft:amethyst_shard", 6)),
                Map.entry("alchemist_components", Map.of("minecraft:spider_eye", 8, "minecraft:glass_bottle", 8, "minecraft:blaze_powder", 4)),
                Map.entry("archivist_records", Map.of("minecraft:book", 8, "minecraft:paper", 12)),
                Map.entry("blacksmith_order", Map.of("minecraft:iron_ingot", 24, "minecraft:gold_ingot", 12)),
                Map.entry("deep_expedition", Map.of("minecraft:ender_pearl", 3, "minecraft:blaze_powder", 8)),
                Map.entry("sealed_archive", Map.of("minecraft:book", 8, "minecraft:paper", 16, "minecraft:amethyst_shard", 6)),
                Map.entry("night_research", Map.of("minecraft:spider_eye", 8, "minecraft:iron_ingot", 16, "minecraft:ender_pearl", 4)),
                Map.entry("mushroom_samples", Map.of("minecraft:red_mushroom", 12, "minecraft:brown_mushroom", 12)),
                Map.entry("wool_for_bedding", Map.of("minecraft:white_wool", 16)),
                Map.entry("river_catch", Map.of("minecraft:cod", 8, "minecraft:salmon", 4)),
                Map.entry("clay_for_potter", Map.of("minecraft:clay_ball", 24)),
                Map.entry("lantern_supplies", Map.of("minecraft:torch", 16)),
                Map.entry("honey_for_healer", Map.of("minecraft:honey_bottle", 4)),
                Map.entry("forest_materials", Map.of("minecraft:oak_log", 16)),
                Map.entry("leather_repair", Map.of("minecraft:leather", 12)),
                Map.entry("map_and_compass", Map.of("minecraft:compass", 1, "minecraft:paper", 8)),
                Map.entry("miner_samples", Map.of("minecraft:coal", 12, "minecraft:raw_iron", 8))
        );
    }

    private void removeLegacyConfiguration() {
        // Existing installations keep their old config.yml when saveDefaultConfig() runs.
        // Strip settings for systems that are intentionally no longer part of this version.
        List<String> legacySections = List.of(
                "season", "witching-hour", "grave-encounters", "global-goal", "global-milestones",
                "currency", "itemsadder", "gameplay", "automatic-world-generation", "rewards",
                "haunted-village", "secret-discoveries", "story-quests", "special-mobs", "notifications",
                "daily-challenge", "return-rewards", "curse", "headless-nick", "random-events",
                "atmosphere", "world-decorations", "finale", "boss", "vampire", "migrations",
                "structure-generation", "structures", "bosses", "messages", "enabled");
        boolean changed = false;
        for (String section : legacySections) {
            if (getConfig().contains(section)) {
                getConfig().set(section, null);
                changed = true;
            }
        }
        if (changed) {
            getLogger().info("Removed obsolete configuration sections for retired Halloween systems.");
        }
    }

    public String color(String message) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    public HalloweenQuestManager getQuestManager() {
        return questManager;
    }

    public SecretRoomManager getSecretRoomManager() {
        return secretRoomManager;
    }

    public KeyItemFactory getKeyItemFactory() {
        return keyItemFactory;
    }
}
