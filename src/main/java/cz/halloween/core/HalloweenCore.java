package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class HalloweenCore extends JavaPlugin implements Listener {
    private HalloweenQuestManager questManager;
    private SecretRoomManager secretRoomManager;
    private KeyItemFactory keyItemFactory;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        removeLegacyConfiguration();
        getConfig().options().copyDefaults(true);
        saveConfig();

        keyItemFactory = new KeyItemFactory(this);
        questManager = new HalloweenQuestManager(this);
        secretRoomManager = new SecretRoomManager(this, keyItemFactory);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(secretRoomManager, this);

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
