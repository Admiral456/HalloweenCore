package cz.halloween.core;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class HalloweenMobManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey cursedKey;
    private final NamespacedKey relicKey;
    private final NamespacedKey eventMobKey;
    private final NamespacedKey abilityCooldownKey;
    private final Set<String> warnedCustomModelIssues = new HashSet<>();

    public HalloweenMobManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.cursedKey = new NamespacedKey(plugin, "cursed_mob");
        this.relicKey = new NamespacedKey(plugin, "halloween_relic");
        this.eventMobKey = new NamespacedKey(plugin, "event_mob");
        this.abilityCooldownKey = new NamespacedKey(plugin, "special_ability_cooldown");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("special-mobs.enabled", true)) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.PATROL) return;

        LivingEntity entity = event.getEntity();
        if (!plugin.isEligibleGameplayWorld(entity.getWorld())) return;
        String mobId = switch (entity.getType()) {
            case ZOMBIE -> "cursed-zombie";
            case HUSK -> "void-reaper";
            case SKELETON -> "gravekeeper";
            case STRAY -> "frost-stalker";
            case SPIDER -> "blood-spider";
            case CREEPER -> "pumpkin-wraith";
            case WITCH -> "hex-witch";
            case PHANTOM -> "nightmare";
            default -> null;
        };
        if (mobId == null) return;

        double chance = Math.max(0.0D, Math.min(1.0D,
                plugin.getConfig().getDouble("special-mobs.chance", 0.04D)));
        int phase = plugin.getEventManager() == null ? 0 : plugin.getEventManager().getGlobalPhase();
        double phaseBonus = Math.max(0.0D,
                plugin.getConfig().getDouble("special-mobs.phase-bonus-chance", 0.005D));
        chance = Math.min(1.0D, chance + phase * phaseBonus);
        if (plugin.getEventManager() != null && plugin.getEventManager().getActiveEventId() != null) {
            chance = Math.min(1.0D, chance * Math.max(1.0D,
                    plugin.getConfig().getDouble("random-events.special-mob-chance-multiplier", 1.50D)));
        }
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        // If the ModelEngine pack is installed, replace the natural vanilla mob with
        // its distinct textured MythicMob at the same location.
        LivingEntity modeled = spawnMythicModelMob(entity.getLocation(), mobId);
        if (modeled != null) {
            configureSpecialMob(modeled, mobId);
            event.setCancelled(true);
            return;
        }
        configureSpecialMob(entity, mobId);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyBloodMoonDamageMultiplier(EntityDamageByEntityEvent event) {
        if (!plugin.isEventEnabled() || plugin.getEventManager() == null
                || !plugin.getEventManager().isActive("blood-moon-invasion")) return;
        if (!(event.getEntity() instanceof Player target)
                || !plugin.isEligibleGameplayPlayer(target)
                || !plugin.isEligibleGameplayWorld(target.getWorld())) return;

        LivingEntity attacker = resolveLivingAttacker(event.getDamager());
        if (!isHostileDamageSource(attacker)) return;

        double multiplier = Math.max(1.0D, Math.min(5.0D,
                plugin.getConfig().getDouble("random-events.blood-moon-damage-multiplier", 3.0D)));
        event.setDamage(event.getDamage() * multiplier);
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
                if (useSecondaryAbility(mobId)) zombieFrenzy(attacker, target);
                else soulSiphon(attacker, target);
            }
            case "gravekeeper" -> {
                if (!(event.getDamager() instanceof Arrow arrow)
                        || !(arrow.getShooter() instanceof LivingEntity shooter)
                        || !shooter.getUniqueId().equals(attacker.getUniqueId())
                        || !canUseSpecialAbility(attacker, mobId)) return;
                if (useSecondaryAbility(mobId)) {
                    var section = plugin.getConfig().getConfigurationSection("special-mobs.types.gravekeeper");
                    telegraphedGroundStrike(attacker, target, "Hrobník", "&8Hrobník vyvolává zemní výboj — uskoč z run!",
                            Particle.SOUL,
                            section == null ? 20L : section.getLong("grave-pulse-delay-ticks", 20L),
                            section == null ? 2.4D : section.getDouble("grave-pulse-radius-blocks", 2.4D),
                            section == null ? 2.5D : section.getDouble("grave-pulse-damage", 2.5D),
                            section == null ? 30 : section.getInt("grave-pulse-slowness-ticks", 30), 0);
                } else {
                    graveMark(attacker, target);
                }
            }
            case "blood-spider" -> {
                if (event.getDamager() != attacker || !canUseSpecialAbility(attacker, mobId)) return;
                if (useSecondaryAbility(mobId)) bloodDrain(attacker, target);
                else bloodWeb(attacker, target);
            }
            case "void-reaper" -> {
                if (event.getDamager() != attacker || !canUseSpecialAbility(attacker, mobId)) return;
                if (useSecondaryAbility(mobId)) {
                    var section = plugin.getConfig().getConfigurationSection("special-mobs.types.void-reaper");
                    telegraphedGroundStrike(attacker, target, "Ženec prázdnoty", "&5Ženec otevírá trhlinu pod tvýma nohama — uhni!",
                            Particle.REVERSE_PORTAL,
                            section == null ? 22L : section.getLong("void-rift-delay-ticks", 22L),
                            section == null ? 2.2D : section.getDouble("void-rift-radius-blocks", 2.2D),
                            section == null ? 2.5D : section.getDouble("void-rift-damage", 2.5D),
                            section == null ? 35 : section.getInt("void-rift-slowness-ticks", 35), 0);
                } else {
                    reaperGloom(attacker, target);
                }
            }
            case "frost-stalker" -> {
                if (!(event.getDamager() instanceof Arrow arrow)
                        || !(arrow.getShooter() instanceof LivingEntity shooter)
                        || !shooter.getUniqueId().equals(attacker.getUniqueId())
                        || !canUseSpecialAbility(attacker, mobId)) return;
                if (useSecondaryAbility(mobId)) {
                    var section = plugin.getConfig().getConfigurationSection("special-mobs.types.frost-stalker");
                    telegraphedGroundStrike(attacker, target, "Ledový stopař", "&bLedový stopař míří na ledový výboj — vyběhni z kruhu!",
                            Particle.SNOWFLAKE,
                            section == null ? 18L : section.getLong("ice-lance-delay-ticks", 18L),
                            section == null ? 2.25D : section.getDouble("ice-lance-radius-blocks", 2.25D),
                            section == null ? 2.0D : section.getDouble("ice-lance-damage", 2.0D),
                            section == null ? 40 : section.getInt("ice-lance-slowness-ticks", 40), 1);
                } else {
                    frostbite(attacker, target);
                }
            }
            case "nightmare" -> {
                if (event.getDamager() != attacker || !canUseSpecialAbility(attacker, mobId)) return;
                if (useSecondaryAbility(mobId)) nightmareSlam(attacker, target);
                else nightmareDread(attacker, target);
            }
            default -> {
            }
        }
    }

    private boolean isHostileDamageSource(LivingEntity attacker) {
        if (attacker == null) return false;
        // Most hostile mobs implement Monster; include hostile types which don't share
        // that interface in Bukkit (notably the Ender Dragon, Wither and Shulker).
        return attacker instanceof Monster
                || attacker instanceof EnderDragon
                || attacker instanceof Wither
                || attacker instanceof Shulker
                || attacker instanceof Ghast
                || attacker instanceof Phantom
                || attacker instanceof Slime;
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

    private boolean useSecondaryAbility(String mobId) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types." + mobId);
        double chance = section == null ? 0.5D : section.getDouble("secondary-ability-chance", 0.5D);
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    /**
     * A readable ground warning followed by a bounded strike. The player can escape;
     * the task never loads a chunk or damages a target who moved to another world.
     */
    private void telegraphedGroundStrike(LivingEntity attacker, Player target, String label, String message,
                                         Particle warningParticle, long delayTicks, double radius,
                                         double damage, int slownessTicks, int slownessAmplifier) {
        if (!plugin.isEligibleGameplayPlayer(target)) return;
        Location marked = target.getLocation().clone();
        World world = marked.getWorld();
        if (world == null) return;

        long delay = Math.max(10L, Math.min(80L, delayTicks));
        double strikeRadius = Math.max(0.75D, Math.min(5.0D, radius));
        double strikeDamage = Math.max(0.0D, Math.min(10.0D, damage));
        world.spawnParticle(warningParticle, marked.clone().add(0.0D, 0.12D, 0.0D),
                36, strikeRadius * 0.45D, 0.08D, strikeRadius * 0.45D, 0.015D);
        world.playSound(marked, "minecraft:block.sculk_sensor.clicking", 0.8f, 0.65f);
        target.sendMessage(plugin.color(message));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEventEnabled() || !attacker.isValid() || attacker.isDead()
                    || !target.isOnline() || target.isDead()
                    || !plugin.isEligibleGameplayPlayer(target)
                    || !world.getUID().equals(target.getWorld().getUID())
                    || !world.isChunkLoaded(marked.getBlockX() >> 4, marked.getBlockZ() >> 4)) return;

            Location impact = marked.clone().add(0.0D, 0.18D, 0.0D);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, impact, 34,
                    strikeRadius * 0.4D, 0.22D, strikeRadius * 0.4D, 0.025D);
            world.playSound(impact, "minecraft:block.soul_sand.break", 0.8f, 0.65f);
            if (target.getLocation().distanceSquared(marked) > strikeRadius * strikeRadius) return;

            if (strikeDamage > 0.0D) target.damage(strikeDamage, attacker);
            if (slownessTicks > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        Math.min(100, slownessTicks), Math.max(0, Math.min(1, slownessAmplifier)),
                        true, true, false));
            }
            target.sendMessage(plugin.color("&c" + label + " zasáhl místo označené runami!"));
        }, delay);
    }

    private void zombieFrenzy(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.cursed-zombie");
        int duration = Math.max(20, Math.min(100,
                section == null ? 40 : section.getInt("frenzy-ticks", 40)));
        int speed = Math.max(0, Math.min(2,
                section == null ? 1 : section.getInt("frenzy-speed-level", 1)));
        int strength = Math.max(0, Math.min(1,
                section == null ? 0 : section.getInt("frenzy-strength-level", 0)));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speed, true, false, true));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, strength, true, false, true));
        Location impact = attacker.getLocation().clone().add(0.0D, 0.8D, 0.0D);
        attacker.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, impact, 28, 0.4D, 0.5D, 0.4D, 0.04D);
        attacker.getWorld().playSound(impact, "minecraft:entity.zombie.angry", 0.8f, 0.65f);
        target.sendMessage(plugin.color("&cProkletý zombie se rozzuřil — drž si odstup, než jeho vztek odezní."));
    }

    private void bloodDrain(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.blood-spider");
        double heal = Math.max(0.0D, Math.min(8.0D,
                section == null ? 3.0D : section.getDouble("blood-drain-heal", 3.0D)));
        int poisonTicks = Math.max(20, Math.min(80,
                section == null ? 40 : section.getInt("blood-drain-poison-ticks", 40)));
        attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonTicks, 0, true, true, false));
        Location impact = target.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, impact, 14, 0.35D, 0.45D, 0.35D, 0.08D);
        target.getWorld().spawnParticle(Particle.CRIMSON_SPORE, attacker.getLocation().clone().add(0, 0.7D, 0),
                16, 0.35D, 0.4D, 0.35D, 0.02D);
        target.playSound(impact, "minecraft:entity.spider.hurt", 0.9f, 0.45f);
        target.sendMessage(plugin.color("&cKrvavý pavouk vysál část tvé životní síly."));
    }

    private void nightmareSlam(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.nightmare");
        double strength = Math.max(0.2D, Math.min(1.5D,
                section == null ? 0.75D : section.getDouble("spectral-slam-knockback", 0.75D)));
        int weaknessTicks = Math.max(20, Math.min(100,
                section == null ? 40 : section.getInt("spectral-slam-weakness-ticks", 40)));
        org.bukkit.util.Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        push.setY(0.0D);
        if (push.lengthSquared() < 0.001D) push = attacker.getLocation().getDirection().multiply(-1.0D).setY(0.0D);
        if (push.lengthSquared() > 0.001D) target.setVelocity(push.normalize().multiply(strength).setY(0.28D));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks, 0, true, true, false));
        Location impact = target.getLocation().clone().add(0.0D, 0.9D, 0.0D);
        target.getWorld().spawnParticle(Particle.ASH, impact, 34, 0.5D, 0.6D, 0.5D, 0.035D);
        target.getWorld().playSound(impact, "minecraft:entity.phantom.flap", 0.9f, 0.55f);
        target.sendMessage(plugin.color("&dNoční můra tě odrazila přízračným úderem!"));
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

    private void reaperGloom(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.void-reaper");
        int duration = Math.max(20, section == null ? 70 : section.getInt("gloom-ticks", 70));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, true, true, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 0, true, true, false));
        Location impact = target.getLocation().clone().add(0, 1.0D, 0);
        target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, impact, 28, 0.45D, 0.55D, 0.45D, 0.035D);
        target.getWorld().playSound(impact, "minecraft:entity.warden.heartbeat", 0.9f, 0.55f);
        target.sendMessage(plugin.color("&5Ženec prázdnoty kolem tebe stáhl svět do temnoty."));
    }

    private void frostbite(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.frost-stalker");
        int duration = Math.max(20, section == null ? 80 : section.getInt("frostbite-ticks", 80));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Math.max(20, duration / 2), 0, true, true, false));
        Location impact = target.getLocation().clone().add(0, 1.0D, 0);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, impact, 28, 0.5D, 0.55D, 0.5D, 0.015D);
        target.getWorld().playSound(impact, "minecraft:block.glass.break", 0.7f, 0.6f);
        target.sendMessage(plugin.color("&bLedový stopař tě zasáhl mrazivým jedem."));
    }

    private void nightmareDread(LivingEntity attacker, Player target) {
        var section = plugin.getConfig().getConfigurationSection("special-mobs.types.nightmare");
        int duration = Math.max(20, section == null ? 60 : section.getInt("night-terror-ticks", 60));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, true, true, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0, true, true, false));
        Location impact = target.getLocation().clone().add(0, 1.0D, 0);
        target.getWorld().spawnParticle(Particle.ASH, impact, 35, 0.6D, 0.7D, 0.6D, 0.025D);
        target.getWorld().spawnParticle(Particle.PORTAL, impact, 20, 0.5D, 0.5D, 0.5D, 0.04D);
        target.getWorld().playSound(impact, "minecraft:entity.phantom.ambient", 0.9f, 0.55f);
        target.sendMessage(plugin.color("&dNoční můra ti vnukla děsivé vidiny."));
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

        boolean emberRing = useSecondaryAbility("pumpkin-wraith");
        int emberFireTicks = Math.max(0, Math.min(80, section == null ? 30 : section.getInt("ember-ring-fire-ticks", 30)));
        int emberSlownessTicks = Math.max(0, Math.min(80, section == null ? 35 : section.getInt("ember-ring-slowness-ticks", 35)));
        double emberKnockback = Math.max(0.0D, Math.min(1.2D,
                section == null ? 0.55D : section.getDouble("ember-ring-knockback", 0.55D)));

        for (Player target : origin.getWorld().getPlayers()) {
            if (!plugin.isEligibleGameplayPlayer(target)) continue;
            if (target.getLocation().distanceSquared(origin) > radius * radius) continue;
            if (emberRing) {
                if (emberFireTicks > 0) target.setFireTicks(Math.max(target.getFireTicks(), emberFireTicks));
                org.bukkit.util.Vector push = target.getLocation().toVector().subtract(origin.toVector());
                push.setY(0.0D);
                if (push.lengthSquared() > 0.001D && emberKnockback > 0.0D) {
                    target.setVelocity(push.normalize().multiply(emberKnockback).setY(0.22D));
                }
                if (emberSlownessTicks > 0) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, emberSlownessTicks, 0, true, true, false));
                }
                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().clone().add(0, 0.8D, 0),
                        18, 0.35D, 0.45D, 0.35D, 0.025D);
                target.sendMessage(plugin.color("&6Dýňový přízrak vypustil žhnoucí kruh — popel ustupuje žáru!"));
            } else {
                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaTicks, 0, true, true, false));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessTicks, 0, true, false, false));
                target.sendMessage(plugin.color("&6Dýňový přízrak vybuchl v závoji popela."));
            }
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
        boolean markedHex = useSecondaryAbility("hex-witch");
        for (Player target : targets) {
            if (markedHex) {
                telegraphedGroundStrike(attacker, target, "Hexová čarodějka",
                        "&5Hexová čarodějka označila zem pod tebou — opusti kruh kletby!",
                        Particle.WITCH,
                        section == null ? 20L : section.getLong("hex-mark-delay-ticks", 20L),
                        section == null ? 2.25D : section.getDouble("hex-mark-radius-blocks", 2.25D),
                        section == null ? 2.0D : section.getDouble("hex-mark-damage", 2.0D),
                        section == null ? 35 : section.getInt("hex-mark-slowness-ticks", 35), 0);
                target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().clone().add(0, 1, 0),
                        18, 0.35D, 0.45D, 0.35D, 0.04D);
            } else {
                switch (ThreadLocalRandom.current().nextInt(3)) {
                    case 0 -> target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, duration, 0, true, false, false));
                    case 1 -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, false));
                    default -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 1, true, true, false));
                }
                target.getWorld().spawnParticle(Particle.WITCH, target.getLocation().clone().add(0, 1, 0),
                        22, 0.45D, 0.55D, 0.45D, 0.06D);
                target.playSound(target.getLocation(), "minecraft:entity.witch.ambient", 0.8f, 0.5f);
                target.sendMessage(plugin.color("&5Hexová čarodějka na tebe uvalila náhodnou kletbu."));
            }
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
        String[] ids = {"cursed-zombie", "gravekeeper", "blood-spider", "pumpkin-wraith", "hex-witch"};
        return spawnEventMob(player, ids[ThreadLocalRandom.current().nextInt(ids.length)]);
    }

    /** Spawn a specific special type so every event can choose its own enemy mix. */
    public boolean spawnEventMob(Player player, String mobId) {
        if (!plugin.isEventEnabled() || player == null
                || !plugin.getConfig().getBoolean("random-events.event-mobs-enabled", true)) return false;
        if (!isKnownSpecialMob(mobId)) return false;

        World world = player.getWorld();
        if (!plugin.isEligibleGameplayWorld(world)) return false;
        int maxEventMobs = Math.max(1, Math.min(80,
                plugin.getConfig().getInt("random-events.max-event-mobs", 48)));
        if (countEventMobs(world) >= maxEventMobs) return false;

        Location spawnLocation = findSafeEventLocation(player);
        if (spawnLocation == null) return false;

        LivingEntity living = spawnMythicModelMob(spawnLocation, mobId);
        if (living == null) {
            Entity entity = world.spawnEntity(spawnLocation, baseEntityType(mobId));
            if (!(entity instanceof LivingEntity spawned)) {
                entity.remove();
                return false;
            }
            living = spawned;
        }

        configureSpecialMob(living, mobId);
        living.getPersistentDataContainer().set(eventMobKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    /**
     * Spawns the named captain for the Blood Moon invasion. It is counted as an event mob
     * so it is cleaned up at the end of the event and respects the configured per-world cap.
     */
    public boolean spawnInvasionCaptain(Player player) {
        if (!plugin.isEventEnabled() || player == null || !player.isOnline()) return false;
        if (!plugin.getConfig().getBoolean("random-events.event-mobs-enabled", true)) return false;
        World world = player.getWorld();
        if (!plugin.isEligibleGameplayWorld(world)) return false;

        int maxEventMobs = Math.max(1, Math.min(80,
                plugin.getConfig().getInt("random-events.max-event-mobs", 48)));
        if (countEventMobs(world) >= maxEventMobs) return false;
        Location spawnLocation = findSafeEventLocation(player);
        if (spawnLocation == null) return false;

        LivingEntity living = spawnMythicModelMob(spawnLocation, "pumpkin-wraith");
        if (living == null) {
            Entity entity = world.spawnEntity(spawnLocation, EntityType.CREEPER);
            if (!(entity instanceof LivingEntity spawned)) {
                entity.remove();
                return false;
            }
            living = spawned;
        }

        configureSpecialMob(living, "pumpkin-wraith");
        living.setCustomName(plugin.color("&4&lKAPITÁN KRVAVÉ INVAZE"));
        living.setCustomNameVisible(true);
        living.setGlowing(true);
        living.setMaxHealth(Math.min(400.0D, living.getMaxHealth() * 3.0D));
        living.setHealth(living.getMaxHealth());
        living.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, true, false, false));
        if (living instanceof Creeper creeper) {
            creeper.setExplosionRadius(Math.max(1, Math.min(3,
                    plugin.getConfig().getInt("special-mobs.types.pumpkin-wraith.explosion-radius", 3))));
        }
        living.getPersistentDataContainer().set(eventMobKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    private boolean isKnownSpecialMob(String mobId) {
        return mobId != null && switch (mobId) {
            case "cursed-zombie", "gravekeeper", "blood-spider", "pumpkin-wraith", "hex-witch" -> true;
            default -> false;
        };
    }

    private EntityType baseEntityType(String mobId) {
        return switch (mobId) {
            case "gravekeeper" -> EntityType.SKELETON;
            case "blood-spider" -> EntityType.SPIDER;
            case "pumpkin-wraith" -> EntityType.CREEPER;
            case "hex-witch" -> EntityType.WITCH;
            default -> EntityType.ZOMBIE;
        };
    }

    /**
     * The ModelEngine texture/model layer is used only when its dependencies and the
     * matching MythicMobs definition are present. If not, callers safely use vanilla.
     */
    private LivingEntity spawnMythicModelMob(Location location, String mobId) {
        if (!plugin.getConfig().getBoolean("special-mobs.custom-models.enabled", true)) return null;
        var mythicPlugin = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        var modelPlugin = plugin.getServer().getPluginManager().getPlugin("ModelEngine");
        if (mythicPlugin == null || !mythicPlugin.isEnabled() || modelPlugin == null || !modelPlugin.isEnabled()) return null;

        String customId = switch (mobId) {
            case "cursed-zombie" -> "halloween_cursed_zombie";
            case "void-reaper" -> "halloween_void_reaper";
            case "gravekeeper" -> "halloween_gravekeeper";
            case "frost-stalker" -> "halloween_frost_stalker";
            case "blood-spider" -> "halloween_blood_spider";
            case "pumpkin-wraith" -> "halloween_pumpkin_wraith";
            case "hex-witch" -> "halloween_hex_witch";
            case "nightmare" -> "halloween_nightmare";
            default -> null;
        };
        if (customId == null) return null;

        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythic = mythicBukkitClass.getMethod("inst").invoke(null);
            Object manager = mythic.getClass().getMethod("getMobManager").invoke(mythic);
            Method getter = manager.getClass().getMethod("getMythicMob", String.class);
            Object maybeMob = getter.invoke(manager, customId);
            Object mythicMob = maybeMob instanceof Optional<?> optional ? optional.orElse(null) : maybeMob;
            if (mythicMob == null) return null;

            Object adaptedLocation = adaptMythicLocation(location);
            if (adaptedLocation == null) {
                warnCustomModelOnce("location-adapter",
                        "MythicMobs API location adapter was not found; special mobs will use vanilla fallback entities.");
                return null;
            }

            Method spawnMethod = null;
            for (Method method : mythicMob.getClass().getMethods()) {
                if (!method.getName().equals("spawn") || method.getParameterCount() != 2) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (!parameters[0].isInstance(adaptedLocation)) continue;
                if (parameters[1] != double.class && parameters[1] != Double.class) continue;
                spawnMethod = method;
                break;
            }
            if (spawnMethod == null) {
                warnCustomModelOnce("spawn-method",
                        "MythicMobs API does not expose spawn(location, level); special mobs will use vanilla fallback entities.");
                return null;
            }

            Object activeMob = spawnMethod.invoke(mythicMob, adaptedLocation, 1.0D);
            if (activeMob == null) return null;
            Object entityWrapper = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
            Object bukkitEntity = entityWrapper.getClass().getMethod("getBukkitEntity").invoke(entityWrapper);
            return bukkitEntity instanceof LivingEntity living ? living : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warnCustomModelOnce(exception.getClass().getName(),
                    "Could not spawn a Halloween MythicMob through the API (" + exception.getClass().getSimpleName()
                            + "); keeping vanilla fallback special mobs available.");
            return null;
        }
    }

    private Object adaptMythicLocation(Location location) throws ReflectiveOperationException {
        String[] adapters = {
                "io.lumine.mythic.bukkit.BukkitAdapter",
                "io.lumine.mythic.bukkit.adapters.BukkitAdapter"
        };
        for (String adapterName : adapters) {
            try {
                Class<?> adapter = Class.forName(adapterName);
                for (Method method : adapter.getMethods()) {
                    if (!Modifier.isStatic(method.getModifiers()) || !method.getName().equals("adapt")
                            || method.getParameterCount() != 1) continue;
                    if (method.getParameterTypes()[0].isAssignableFrom(location.getClass())) {
                        return method.invoke(null, location);
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Try the alternative package used by another MythicMobs API release.
            }
        }
        return null;
    }

    private void warnCustomModelOnce(String key, String message) {
        if (warnedCustomModelIssues.add(key)) plugin.getLogger().warning(message);
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
        playMobSpawnVfx(entity, mobId);
    }

    private void playMobSpawnVfx(LivingEntity entity, String mobId) {
        Location effect = entity.getLocation().clone().add(0.0D, 0.85D, 0.0D);
        switch (mobId) {
            case "cursed-zombie" -> {
                entity.getWorld().spawnParticle(Particle.SOUL, effect, 18, 0.35D, 0.55D, 0.35D, 0.035D);
                entity.getWorld().spawnParticle(Particle.ASH, effect, 12, 0.3D, 0.35D, 0.3D, 0.01D);
            }
            case "void-reaper" -> {
                entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, effect, 24, 0.45D, 0.6D, 0.45D, 0.035D);
                entity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, effect, 14, 0.35D, 0.5D, 0.35D, 0.02D);
            }
            case "gravekeeper" -> {
                entity.getWorld().spawnParticle(Particle.SOUL, effect, 22, 0.4D, 0.6D, 0.4D, 0.04D);
                entity.getWorld().spawnParticle(Particle.REVERSE_PORTAL, effect, 14, 0.35D, 0.5D, 0.35D, 0.02D);
            }
            case "frost-stalker" -> {
                entity.getWorld().spawnParticle(Particle.SNOWFLAKE, effect, 26, 0.5D, 0.4D, 0.5D, 0.02D);
                entity.getWorld().spawnParticle(Particle.END_ROD, effect, 12, 0.25D, 0.3D, 0.25D, 0.01D);
            }
            case "nightmare" -> {
                entity.getWorld().spawnParticle(Particle.ASH, effect, 22, 0.45D, 0.6D, 0.45D, 0.02D);
                entity.getWorld().spawnParticle(Particle.PORTAL, effect, 26, 0.4D, 0.6D, 0.4D, 0.04D);
            }
            case "blood-spider" -> {
                entity.getWorld().spawnParticle(Particle.CRIT, effect, 20, 0.25D, 0.25D, 0.25D, 0.12D);
                entity.getWorld().spawnParticle(Particle.CRIMSON_SPORE, effect, 14, 0.3D, 0.25D, 0.3D, 0.01D);
            }
            case "pumpkin-wraith" -> {
                entity.getWorld().spawnParticle(Particle.FLAME, effect, 22, 0.3D, 0.45D, 0.3D, 0.025D);
                entity.getWorld().spawnParticle(Particle.ASH, effect, 16, 0.35D, 0.35D, 0.35D, 0.01D);
            }
            case "hex-witch" -> {
                entity.getWorld().spawnParticle(Particle.WITCH, effect, 20, 0.35D, 0.55D, 0.35D, 0.05D);
                entity.getWorld().spawnParticle(Particle.PORTAL, effect, 14, 0.3D, 0.4D, 0.3D, 0.08D);
            }
            default -> { }
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
