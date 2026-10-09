package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class HalloweenMobManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey cursedKey;
    private final NamespacedKey relicKey;
    private final NamespacedKey eventMobKey;
    private final NamespacedKey abilityCooldownKey;

    public HalloweenMobManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.cursedKey = new NamespacedKey(plugin, "cursed_mob");
        this.relicKey = new NamespacedKey(plugin, "halloween_relic");
        this.eventMobKey = new NamespacedKey(plugin, "event_mob");
        this.abilityCooldownKey = new NamespacedKey(plugin, "special_ability_cooldown");
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
        if (!plugin.isEligibleGameplayWorld(entity.getWorld())) return;
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
        int phase = plugin.getEventManager() == null ? 0 : plugin.getEventManager().getGlobalPhase();
        double phaseBonus = Math.max(0.0D, plugin.getConfig().getDouble("special-mobs.phase-bonus-chance", 0.005D));
        chance = Math.min(1.0D, chance + phase * phaseBonus);
        if (plugin.getEventManager() != null && plugin.getEventManager().getActiveEventId() != null) {
            chance = Math.min(1.0D, chance * Math.max(1.0D,
                    plugin.getConfig().getDouble("random-events.special-mob-chance-multiplier", 1.50D)));
        }
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        configureSpecialMob(entity, mobId);
    }


    @EventHandler(ignoreCancelled = true)
    public void onSpecialMobAttack(EntityDamageByEntityEvent event) {
        if (!plugin.isEventEnabled() || !(event.getEntity() instanceof Player target)) return;
        if (!plugin.isEligibleGameplayPlayer(target) || !plugin.isEligibleGameplayWorld(target.getWorld())) return;

        LivingEntity attacker = resolveLivingAttacker(event.getDamager());
        if (attacker == null) return;

        String mobId = attacker.getPersistentDataContainer().get(cursedKey, PersistentDataType.STRING);
        if (mobId == null) return;

        switch (mobId) {
            case "cursed-zombie" -> {
                if (event.getDamager() != attacker || !canUseSpecialAbility(attacker, mobId)) return;
                soulSiphon(attacker, target);
            }
            case "gravekeeper" -> {
                if (!(event.getDamager() instanceof Arrow arrow)
                        || !(arrow.getShooter() instanceof LivingEntity shooter)
                        || !shooter.getUniqueId().equals(attacker.getUniqueId())
                        || !canUseSpecialAbility(attacker, mobId)) return;
                graveMark(attacker, target);
            }
            case "blood-spider" -> {
                if (event.getDamager() != attacker || !canUseSpecialAbility(attacker, mobId)) return;
                bloodWeb(attacker, target);
            }
            default -> {
            }
        }
    }

    private LivingEntity resolveLivingAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private boolean canUseSpecialAbility(LivingEntity attacker, String mobId) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types." + mobId);
        long cooldownSeconds = Math.max(1L,
                section == null ? 8L : section.getLong("ability-cooldown-seconds", 8L));
        long now = System.currentTimeMillis();
        long last = attacker.getPersistentDataContainer().getOrDefault(
                abilityCooldownKey, PersistentDataType.LONG, 0L);
        if (last > 0L && now - last < cooldownSeconds * 1000L) return false;
        attacker.getPersistentDataContainer().set(abilityCooldownKey, PersistentDataType.LONG, now);
        return true;
    }

    private void soulSiphon(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.cursed-zombie");
        int blindnessTicks = Math.max(10, section == null ? 35 : section.getInt("soul-siphon-blindness-ticks", 35));
        int weaknessTicks = Math.max(20, section == null ? 80 : section.getInt("soul-siphon-weakness-ticks", 80));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, blindnessTicks, 0, true, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks, 0, true, true, false));
        Location impact = target.getLocation().clone().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.SOUL, impact, 22, 0.45D, 0.6D, 0.45D, 0.04D);
        target.playSound(target.getLocation(), "minecraft:entity.zombie.ambient", 0.7f, 0.45f);
        target.sendMessage(plugin.color("&5Prokletý zombie ti vysál část síly."));
    }

    private void graveMark(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.gravekeeper");
        long delayTicks = Math.max(10L, section == null ? 24L : section.getLong("grave-mark-delay-ticks", 24L));
        double markRadius = Math.max(0.5D,
                section == null ? 1.5D : section.getDouble("grave-mark-radius-blocks", 1.5D));
        double markDamage = Math.max(1.0D,
                section == null ? 3.0D : section.getDouble("grave-mark-damage", 3.0D));
        Location markedLocation = target.getLocation().clone();

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, true, false, false));
        target.getWorld().spawnParticle(Particle.SOUL, markedLocation.clone().add(0, 0.1D, 0),
                16, 0.55D, 0.08D, 0.55D, 0.01D);
        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, markedLocation.clone().add(0, 0.15D, 0),
                10, 0.45D, 0.05D, 0.45D, 0.01D);
        target.playSound(markedLocation, "minecraft:block.soul_sand.place", 0.8f, 0.55f);
        target.sendMessage(plugin.color("&8Hrobník označil tvůj stín — uhni z duše na zemi!"));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEventEnabled() || !target.isOnline() || target.isDead()
                    || !plugin.isEligibleGameplayWorld(target.getWorld())
                    || !target.getWorld().getUID().equals(markedLocation.getWorld().getUID())) return;
            target.getWorld().spawnParticle(Particle.SOUL, markedLocation.clone().add(0, 0.15D, 0),
                    32, markRadius / 2.0D, 0.12D, markRadius / 2.0D, 0.02D);
            target.getWorld().playSound(markedLocation, "minecraft:block.soul_sand.break", 0.9f, 0.7f);
            if (target.getLocation().distanceSquared(markedLocation) <= markRadius * markRadius) {
                target.damage(markDamage, attacker);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 55, 1, true, true, false));
            }
        }, delayTicks);
    }

    private void bloodWeb(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.blood-spider");
        int poisonTicks = Math.max(20, section == null ? 80 : section.getInt("blood-web-poison-ticks", 80));
        int slownessTicks = Math.max(20, section == null ? 45 : section.getInt("blood-web-slowness-ticks", 45));
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, 0, true, true, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessTicks, 0, true, false, false));
        Location impact = target.getLocation().clone().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.CRIT, impact, 18, 0.4D, 0.5D, 0.4D, 0.1D);
        target.playSound(target.getLocation(), "minecraft:entity.spider.hurt", 0.8f, 0.55f);
        target.sendMessage(plugin.color("&cKrvavý pavouk tě zachytil do krvavé sítě."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPumpkinWraithExplosion(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        String mobId = creeper.getPersistentDataContainer().get(cursedKey, PersistentDataType.STRING);
        if (!"pumpkin-wraith".equals(mobId)) return;

        // Halloween elites must not grief player builds.
        event.blockList().clear();
        event.setYield(0.0F);

        Location origin = event.getLocation().clone();
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.pumpkin-wraith");
        double radius = Math.max(2.0D,
                section == null ? 5.0D : section.getDouble("ash-flash-radius-blocks", 5.0D));
        int nauseaTicks = Math.max(20, section == null ? 70 : section.getInt("ash-flash-nausea-ticks", 70));
        int blindnessTicks = Math.max(10, section == null ? 30 : section.getInt("ash-flash-blindness-ticks", 30));

        origin.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, origin.clone().add(0, 0.4D, 0),
                50, 1.2D, 0.8D, 1.2D, 0.06D);
        origin.getWorld().spawnParticle(Particle.ASH, origin.clone().add(0, 0.4D, 0),
                70, 1.5D, 1.0D, 1.5D, 0.02D);
        origin.getWorld().playSound(origin, "minecraft:entity.warden.roar", 0.6f, 1.6f);

        for (Player target : origin.getWorld().getPlayers()) {
            if (!plugin.isEligibleGameplayPlayer(target)) continue;
            if (target.getLocation().distanceSquared(origin) > radius * radius) continue;
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaTicks, 0, true, true, false));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, true, false, false));
            target.sendMessage(plugin.color("&6Dýňový přízrak vybuchl v závoji popela."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHexWitchPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof LivingEntity attacker)) return;
        if (!plugin.isEventEnabled()) return;
        String mobId = attacker.getPersistentDataContainer().get(cursedKey, PersistentDataType.STRING);
        if (!"hex-witch".equals(mobId)) return;

        List<Player> targets = new ArrayList<>();
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player player
                    && plugin.isEligibleGameplayPlayer(player)
                    && plugin.isEligibleGameplayWorld(player.getWorld())
                    && event.getIntensity(player) >= 0.25D) {
                targets.add(player);
            }
        }
        if (targets.isEmpty() || !canUseSpecialAbility(attacker, mobId)) return;

        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.hex-witch");
        int duration = Math.max(20, section == null ? 65 : section.getInt("random-hex-duration-ticks", 65));
        for (Player target : targets) {
            switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, true, false, false));
                case 1 -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, false));
                default -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 1, true, true, false));
            }
            target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0),
                    22, 0.45D, 0.55D, 0.45D, 0.06D);
            target.playSound(target.getLocation(), "minecraft:entity.witch.ambient", 0.8f, 0.5f);
            target.sendMessage(plugin.color("&5Hexová čarodějka na tebe uvalila náhodnou kletbu."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!plugin.isEventEnabled() || !isSpecial(event.getEntity())) return;

        String mobId = event.getEntity().getPersistentDataContainer().get(cursedKey, PersistentDataType.STRING);
        if (mobId == null) return;

        Player killer = event.getEntity().getKiller();
        boolean eligibleKiller = killer != null
                && plugin.isEligibleGameplayPlayer(killer)
                && plugin.isEligibleGameplayWorld(killer.getWorld())
                && plugin.isEligibleGameplayWorld(event.getEntity().getWorld());
        // Event loot is earned through eligible player combat, not creative or environmental kills.
        if (!eligibleKiller) return;

        long bonus = Math.max(0L, plugin.getConfig().getLong("special-mobs.bonus-fragments", 12L));
        if (bonus > 0L) {
            plugin.getService().addFragments(killer.getUniqueId(), bonus, "special-mob");
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

        boolean villageConfigured = plugin.getConfig().getBoolean("haunted-village.enabled", false)
                && !plugin.getConfig().getString("haunted-village.world", "").isBlank();
        double mapChance = Math.max(0.0D, Math.min(1.0D,
                plugin.getConfig().getDouble("special-mobs.map-drop-chance", 0.03D)));
        if (villageConfigured && ThreadLocalRandom.current().nextDouble() <= mapChance) {
            String mapItemId = plugin.getConfig().getString("special-mobs.map-item-id", "");
            ItemStack mapItem = plugin.getItemManager().getItemsAdderItem(mapItemId, 1);
            if (mapItem == null) {
                mapItem = new ItemStack(Material.PAPER);
                if (!mapItemId.isBlank()) {
                    plugin.getItemManager().warnIfMissing(mapItemId, "Haunted Village map");
                }
            }

            ItemMeta mapMeta = mapItem.getItemMeta();
            if (mapMeta != null) {
                mapMeta.setDisplayName(plugin.color("&5&lProkletá mapa &8[Halloween 2026]"));
                mapMeta.setLore(java.util.List.of(
                        plugin.color("&7Ukazuje cestu k Haunted Village."),
                        plugin.color("&8Vzácný nález z prokletých mobů.")
                ));
                mapMeta.getPersistentDataContainer().set(relicKey, PersistentDataType.STRING, "haunted-map-2026");
                mapItem.setItemMeta(mapMeta);
            }
            event.getDrops().add(mapItem);
        }
    }

    public boolean spawnEventMob(Player player) {
        if (!plugin.isEventEnabled() || player == null) return false;
        if (!plugin.getConfig().getBoolean("random-events.event-mobs-enabled", true)) return false;

        World world = player.getWorld();
        if (!plugin.isEligibleGameplayWorld(world)) return false;
        int maxEventMobs = Math.max(1, plugin.getConfig().getInt("random-events.max-event-mobs", 12));
        if (countEventMobs(world) >= maxEventMobs) return false;

        String[] ids = {"cursed-zombie", "gravekeeper", "blood-spider", "pumpkin-wraith", "hex-witch"};
        String mobId = ids[ThreadLocalRandom.current().nextInt(ids.length)];

        org.bukkit.Location spawnLocation = findSafeEventLocation(player);
        if (spawnLocation == null) return false;

        EntityType type = switch (mobId) {
            case "gravekeeper" -> EntityType.SKELETON;
            case "blood-spider" -> EntityType.SPIDER;
            case "pumpkin-wraith" -> EntityType.CREEPER;
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

    public int cleanupEventMobs() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!entity.getPersistentDataContainer().has(eventMobKey, PersistentDataType.BYTE)) continue;
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    public boolean isSpecial(LivingEntity entity) {
        return entity.getPersistentDataContainer().has(cursedKey, PersistentDataType.STRING);
    }
}
