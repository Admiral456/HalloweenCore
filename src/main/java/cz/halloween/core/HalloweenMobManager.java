package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

public final class HalloweenMobManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey cursedKey;
    private final NamespacedKey relicKey;
    private final NamespacedKey eventMobKey;

    public HalloweenMobManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.cursedKey = new NamespacedKey(plugin, "cursed_mob");
        this.relicKey = new NamespacedKey(plugin, "halloween_relic");
        this.eventMobKey = new NamespacedKey(plugin, "event_mob");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!plugin.isEventEnabled()) return;
        if (!plugin.getConfig().getBoolean("special-mobs.enabled", true)) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.PATROL) {
            return;
        }

        LivingEntity entity = event.getEntity();
        String mobId = switch (entity.getType()) {
            case ZOMBIE -> "cursed-zombie";
            case SKELETON -> "gravekeeper";
            case SPIDER -> "blood-spider";
            case CREEPER -> "pumpkin-wraith";
            case WITCH -> "hex-witch";
            default -> null;
        };

        if (mobId == null) return;

        double chance = Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble("special-mobs.chance", 0.04D)));
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        configureSpecialMob(entity, mobId);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!isSpecial(event.getEntity())) return;

        String mobId = event.getEntity().getPersistentDataContainer().get(cursedKey, PersistentDataType.STRING);
        if (mobId == null) return;

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            long bonus = Math.max(0L, plugin.getConfig().getLong("special-mobs.bonus-fragments", 12L));
            if (bonus > 0L) {
                plugin.getService().addFragments(killer.getUniqueId(), bonus, "special-mob");
            }
        }

        double dropChance = Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble("special-mobs.relic-drop-chance", 0.12D)));
        if (ThreadLocalRandom.current().nextDouble() <= dropChance) {
            String configuredItemId = plugin.getConfig().getString("special-mobs.relic-item-id", "");
            ItemStack item = plugin.getItemManager().getItemsAdderItem(configuredItemId, 1);
            if (item == null) {
                item = new ItemStack(Material.PUMPKIN_PIE);
                if (!configuredItemId.isBlank()) {
                    plugin.getItemManager().warnIfMissing(configuredItemId, "special mob relic");
                }
            }
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(plugin.color("&6&lProkleté cukroví &8[Halloween 2026]"));
                meta.setLore(java.util.List.of(
                        plugin.color("&7Padá ze speciálních Halloween mobů."),
                        plugin.color("&7Malá památka na letošní Halloween.")
                ));
                meta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, "cursed-candy-2026");
                item.setItemMeta(meta);
            }
            event.getDrops().add(item);
        }
    }

    public boolean spawnEventMob(Player player) {
        if (!plugin.isEventEnabled() || player == null) return false;

        World world = player.getWorld();
        int maxEventMobs = Math.max(1, plugin.getConfig().getInt("random-events.max-event-mobs", 12));
        if (countEventMobs(world) >= maxEventMobs) return false;

        String[] ids = {"cursed-zombie", "gravekeeper", "blood-spider", "hex-witch"};
        String mobId = ids[ThreadLocalRandom.current().nextInt(ids.length)];

        org.bukkit.Location spawnLocation = findSafeEventLocation(player);
        if (spawnLocation == null) return false;

        EntityType type = switch (mobId) {
            case "gravekeeper" -> EntityType.SKELETON;
            case "blood-spider" -> EntityType.SPIDER;
            case "hex-witch" -> EntityType.WITCH;
            default -> EntityType.ZOMBIE;
        };

        Entity entity = world.spawnEntity(spawnLocation, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return false;
        }

        configureSpecialMob(living, mobId);
        living.getPersistentDataContainer().set(eventMobKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    private void configureSpecialMob(LivingEntity entity, String mobId) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types." + mobId);
        if (section == null) return;

        entity.getPersistentDataContainer().set(cursedKey, PersistentDataType.STRING, mobId);
        entity.setCustomName(plugin.color(section.getString("name", mobId)));
        entity.setCustomNameVisible(true);
        entity.setGlowing(section.getBoolean("glowing", true));

        double maxHealthMultiplier = Math.max(1.0D, section.getDouble("health-multiplier", 1.35D));
        entity.setMaxHealth(entity.getMaxHealth() * maxHealthMultiplier);
        entity.setHealth(entity.getMaxHealth());

        if (entity instanceof Monster monster) {
            monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, Math.max(0, section.getInt("speed-level", 0)), true, false, false));
            monster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, Math.max(0, section.getInt("strength-level", 0)), true, false, false));
        }

        if (entity instanceof Creeper creeper) {
            creeper.setExplosionRadius(Math.max(1, section.getInt("explosion-radius", 3)));
        }
    }

    private org.bukkit.Location findSafeEventLocation(Player player) {
        World world = player.getWorld();

        for (int attempt = 0; attempt < 6; attempt++) {
            int radius = ThreadLocalRandom.current().nextInt(12, 29);
            double angle = ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
            int x = player.getLocation().getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = player.getLocation().getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = world.getHighestBlockYAt(x, z) + 1;

            if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) continue;

            org.bukkit.Location location = new org.bukkit.Location(world, x + 0.5D, y, z + 0.5D);
            if (!world.getWorldBorder().isInside(location)) continue;

            Block feet = location.getBlock();
            Block head = feet.getRelative(0, 1, 0);
            Block below = feet.getRelative(0, -1, 0);

            if (!feet.isPassable() || !head.isPassable() || below.isPassable()
                    || below.isLiquid() || feet.isLiquid() || head.isLiquid()) {
                continue;
            }

            return location;
        }

        return null;
    }

    private long countEventMobs(World world) {
        return world.getLivingEntities().stream()
                .filter(entity -> entity.getPersistentDataContainer().has(eventMobKey, PersistentDataType.BYTE))
                .count();
    }

    public boolean isSpecial(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(cursedKey, PersistentDataType.STRING);
    }
}
