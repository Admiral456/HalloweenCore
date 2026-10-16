package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistent custom achievements for structure discovery, boss kills and the full armor set. */
public final class HalloweenAchievementManager implements Listener {
    private static final Set<String> BOSS_ACHIEVEMENT_IDS = Set.of(
            "boss_vampire_king", "boss_grave_guardian", "boss_headless_nick", "boss_blood_moon_captain");
    private static final Set<String> ARMOR_SET_IDS = Set.of(
            "gear_crimson_warden_helmet", "gear_crimson_warden_chestplate",
            "gear_crimson_warden_leggings", "gear_crimson_warden_boots");
    private static final Map<String, String> LEGACY_ARMOR_IDENTITIES = Map.of(
            "crimson-warden-helmet", "gear_crimson_warden_helmet",
            "crimson-warden-chestplate", "gear_crimson_warden_chestplate",
            "crimson-warden-leggings", "gear_crimson_warden_leggings",
            "crimson-warden-boots", "gear_crimson_warden_boots");
    private static final List<Achievement> CATALOG = List.of(
            new Achievement("find_village", "První výprava", "Objevil jsi Halloween vesnici."),
            new Achievement("find_graveyard", "Město mrtvých", "Objevil jsi Halloween hřbitov."),
            new Achievement("boss_vampire_king", "Konec noci", "Porazil jsi Krále upírů."),
            new Achievement("boss_grave_guardian", "Strážce padl", "Porazil jsi Strážce hrobu při skutečném rituálu."),
            new Achievement("boss_headless_nick", "Bezhlavý konec", "Porazil jsi Bezhlavého Nicka."),
            new Achievement("boss_blood_moon_captain", "Zhasni krvavý měsíc", "Porazil jsi Kapitána krvavé invaze."),
            new Achievement("boss_hunter", "Lovec bossů", "Porazil jsi všechny čtyři hlavní bosse."),
            new Achievement("full_crimson_warden_set", "Krvavý strážce", "Získal jsi všechny čtyři kusy zbroje Krvavého strážce.")
    );
    private final HalloweenCore plugin;
    private final NamespacedKey contentIdKey;
    private final NamespacedKey legacyIdentityKey;
    private final NamespacedKey bloodMoonCaptainKey;
    private BukkitTask scanTask;

    public HalloweenAchievementManager(HalloweenCore plugin) {
        this.plugin = plugin;
        contentIdKey = new NamespacedKey(plugin, "halloween_content_id");
        legacyIdentityKey = new NamespacedKey(plugin, "halloween_relic");
        bloodMoonCaptainKey = new NamespacedKey(plugin, "blood_moon_captain");
    }

    public void start() {
        if (scanTask != null) return;
        // Only recorded centers in already-loaded chunks are scanned.
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::scanPlayers, 20L, 40L);
    }

    public void stop() {
        if (scanTask == null) return;
        scanTask.cancel();
        scanTask = null;
    }

    public List<Achievement> getAchievementCatalog() { return CATALOG; }

    public boolean award(Player player, String achievementId) {
        if (player == null || !player.isOnline() || achievementId == null) return false;
        Achievement achievement = CATALOG.stream()
                .filter(candidate -> candidate.id().equalsIgnoreCase(achievementId))
                .findFirst().orElse(null);
        if (achievement == null || !plugin.getDataManager().unlockAchievement(player.getUniqueId(), achievement.id())) return false;
        plugin.getDataManager().save();
        player.sendTitle(plugin.color("&6&lÚSPĚCH ODEMČEN"), plugin.color("&f" + achievement.title()), 5, 45, 12);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.25f);
        player.sendMessage(plugin.color("&6✦ &eOdemčen úspěch: &f" + achievement.title()));
        player.sendMessage(plugin.color("&7" + achievement.description()));
        if (BOSS_ACHIEVEMENT_IDS.contains(achievement.id())
                && BOSS_ACHIEVEMENT_IDS.stream().allMatch(id -> plugin.getDataManager().hasAchievement(player.getUniqueId(), id))) {
            award(player, "boss_hunter");
        }
        return true;
    }

    private void scanPlayers() {
        if (!plugin.isEventEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isEligibleGameplayWorld(player.getWorld())) continue;
            scanStructureDiscoveries(player);
            scanArmorSet(player);
        }
    }

    private void scanStructureDiscoveries(Player player) {
        HalloweenStructureManager structures = plugin.getStructureManager();
        if (structures == null) return;
        World world = player.getWorld();
        Location at = player.getLocation();
        double radius = Math.max(4.0D, Math.min(64.0D,
                plugin.getConfig().getDouble("achievements.structure-discovery-radius-blocks", 12.0D)));
        double radiusSquared = radius * radius;
        if (!plugin.getDataManager().hasAchievement(player.getUniqueId(), "find_village")) {
            boolean found = nearAnyRecordedStructure(structures, world, at, "VILLAGE", radiusSquared)
                    || nearConfiguredHauntedVillage(at, radiusSquared);
            if (found) award(player, "find_village");
        }
        if (!plugin.getDataManager().hasAchievement(player.getUniqueId(), "find_graveyard")
                && nearAnyRecordedStructure(structures, world, at, "GRAVEYARD", radiusSquared)) {
            award(player, "find_graveyard");
        }
    }

    private boolean nearAnyRecordedStructure(HalloweenStructureManager structures, World world,
                                             Location at, String kind, double radiusSquared) {
        for (Location center : structures.getRecordedStructureLocations(world, kind)) {
            double dx = at.getX() - center.getX(), dz = at.getZ() - center.getZ();
            if (dx * dx + dz * dz <= radiusSquared) return true;
        }
        return false;
    }

    private boolean nearConfiguredHauntedVillage(Location at, double radiusSquared) {
        if (!plugin.getConfig().getBoolean("haunted-village.enabled", false)) return false;
        World world = Bukkit.getWorld(plugin.getConfig().getString("haunted-village.world", ""));
        if (world == null || !world.getUID().equals(at.getWorld().getUID())) return false;
        double dx = at.getX() - plugin.getConfig().getDouble("haunted-village.x", 0.0D);
        double dz = at.getZ() - plugin.getConfig().getDouble("haunted-village.z", 0.0D);
        return dx * dx + dz * dz <= radiusSquared;
    }

    private void scanArmorSet(Player player) {
        if (plugin.getDataManager().hasAchievement(player.getUniqueId(), "full_crimson_warden_set")) return;
        Set<String> found = new HashSet<>();
        var inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) collectArmorIdentity(item, found);
        for (ItemStack item : inventory.getArmorContents()) collectArmorIdentity(item, found);
        collectArmorIdentity(inventory.getItemInOffHand(), found);
        if (found.containsAll(ARMOR_SET_IDS)) award(player, "full_crimson_warden_set");
    }

    private void collectArmorIdentity(ItemStack item, Set<String> found) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var data = meta.getPersistentDataContainer();
        String contentId = data.get(contentIdKey, PersistentDataType.STRING);
        if (ARMOR_SET_IDS.contains(contentId)) found.add(contentId);
        String legacy = data.get(legacyIdentityKey, PersistentDataType.STRING);
        String canonical = LEGACY_ARMOR_IDENTITIES.get(legacy);
        if (canonical != null) found.add(canonical);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBloodMoonCaptainDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!dead.getPersistentDataContainer().has(bloodMoonCaptainKey, PersistentDataType.BYTE)) return;
        Player killer = dead.getKiller();
        if (killer != null) award(killer, "boss_blood_moon_captain");
    }

    public record Achievement(String id, String title, String description) {}
}
