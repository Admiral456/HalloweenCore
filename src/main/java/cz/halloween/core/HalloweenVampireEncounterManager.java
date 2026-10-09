package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class HalloweenVampireEncounterManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey bossKey;

    private LivingEntity boss;
    private UUID bossUuid;
    private BukkitTask tickTask;
    private BukkitTask autoProgressionTask;
    private boolean omenAnnounced;
    private long naturalSpawnAt;
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Integer> participationSeconds = new HashMap<>();
    private final Map<UUID, Double> damageContribution = new HashMap<>();
    private boolean victoryHandled;
    private long startedAt;
    private int phase;
    private long lastAbilityAt;

    public HalloweenVampireEncounterManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "vampire_boss");
    }

    public void startAutoProgression() {
        if (autoProgressionTask != null) return;
        autoProgressionTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickAutomaticProgression, 20L, 20L);
    }

    public void shutdown() {
        if (autoProgressionTask != null) {
            autoProgressionTask.cancel();
            autoProgressionTask = null;
        }
        stopEncounter();
    }

    private void tickAutomaticProgression() {
        if (isActive()) return;

        if (!plugin.isEventEnabled()
                || !plugin.getConfig().getBoolean("bosses.vampire.auto-start.enabled", true)
                || plugin.getDataManager().isVampireDefeated()
                || !plugin.getDataManager().isFinaleUnlocked()
                || !plugin.getBossManager().isVampireReady()) {
            clearOmenCountdown();
            return;
        }

        long unlockedAt = plugin.getDataManager().getFinaleUnlockedAt();
        if (unlockedAt <= 0L) {
            plugin.getDataManager().unlockFinale();
            plugin.getDataManager().save();
            unlockedAt = plugin.getDataManager().getFinaleUnlockedAt();
        }

        long delaySeconds = Math.max(0L,
                plugin.getConfig().getLong("bosses.vampire.auto-start.delay-after-finale-seconds", 300L));
        long now = System.currentTimeMillis();
        if (now < unlockedAt + delaySeconds * 1000L) {
            clearOmenCountdown();
            return;
        }

        Location center = getArenaLocation();
        if (center == null) {
            clearOmenCountdown();
            return;
        }

        double radius = Math.max(16.0D,
                plugin.getConfig().getDouble("bosses.vampire.auto-start.nearby-radius-blocks", 64.0D));
        int minimumPlayers = Math.max(1,
                plugin.getConfig().getInt("bosses.vampire.auto-start.minimum-nearby-players", 1));
        if (countEligiblePlayersNear(center, radius) < minimumPlayers) {
            clearOmenCountdown();
            return;
        }

        if (!omenAnnounced) {
            omenAnnounced = true;
            long countdownSeconds = Math.max(0L,
                    plugin.getConfig().getLong("bosses.vampire.auto-start.omen-countdown-seconds", 30L));
            naturalSpawnAt = now + countdownSeconds * 1000L;

            String warning = plugin.getConfig().getString("messages.vampire-omen",
                    "&5&lHALLOWEEN &8» &dZemě se zachvěla. Ve světě &e%world% &7na souřadnicích &e%x% %y% %z% &7se probouzí Král upírů. Máte &e%countdown% sekund &7na přípravu.");
            warning = warning.replace("%world%", center.getWorld().getName())
                    .replace("%x%", Integer.toString(center.getBlockX()))
                    .replace("%y%", Integer.toString(center.getBlockY()))
                    .replace("%z%", Integer.toString(center.getBlockZ()))
                    .replace("%countdown%", Long.toString(countdownSeconds));
            Bukkit.broadcastMessage(plugin.color(warning));
            center.getWorld().playSound(center, "minecraft:entity.warden.heartbeat", 0.8f, 0.55f);
            center.getWorld().spawnParticle(org.bukkit.Particle.SOUL, center.clone().add(0, 1, 0),
                    70, 2.5D, 1.5D, 2.5D, 0.04D);
            if (countdownSeconds > 0L) return;
        }

        if (System.currentTimeMillis() < naturalSpawnAt) return;
        if (startEncounter()) {
            clearOmenCountdown();
        } else {
            clearOmenCountdown();
        }
    }

    private int countEligiblePlayersNear(Location center, double radius) {
        int count = 0;
        double radiusSquared = radius * radius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(center.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(center) <= radiusSquared) count++;
        }
        return count;
    }

    private void clearOmenCountdown() {
        omenAnnounced = false;
        naturalSpawnAt = 0L;
    }

    public boolean isActive() {
        return boss != null && bossUuid != null && !boss.isDead() && boss.isValid();
    }

    public LivingEntity getBoss() {
        return boss;
    }

    public int getPhase() {
        return phase;
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public boolean isTrackedVampireBoss(Entity entity) {
        return entity != null
                && bossUuid != null
                && bossUuid.equals(entity.getUniqueId());
    }

    public boolean startEncounter() {
        if (!plugin.isEventEnabled()) return false;
        if (isActive()) return false;
        if (!plugin.getConfig().getBoolean("bosses.vampire.enabled", false)) return false;
        if (plugin.getDataManager().isVampireDefeated()) return false;
        if (!plugin.getBossManager().isVampireReady()) return false;

        String worldName = plugin.getConfig().getString("bosses.vampire.arena.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Cannot start Vampire encounter: arena world is not loaded: " + worldName);
            return false;
        }

        double x = plugin.getConfig().getDouble("bosses.vampire.arena.x", 0.0D);
        double y = plugin.getConfig().getDouble("bosses.vampire.arena.y", 100.0D);
        double z = plugin.getConfig().getDouble("bosses.vampire.arena.z", 0.0D);
        Location location = new Location(world, x, y, z);

        LivingEntity spawned = spawnMythicMob(location);
        if (spawned == null) {
            plugin.getLogger().severe("Cannot start Vampire encounter: MythicMobs mob '" 
                    + plugin.getBossManager().getVampireSpec().id() + "' could not be spawned.");
            return false;
        }

        boss = spawned;
        bossUuid = spawned.getUniqueId();
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        startedAt = System.currentTimeMillis();
        phase = 1;
        lastAbilityAt = 0L;
        participants.clear();
        participationSeconds.clear();
        damageContribution.clear();
        victoryHandled = false;
        plugin.getBossManager().trackVampireBoss(boss);
        Bukkit.broadcastMessage(plugin.color(
                plugin.getConfig().getString("messages.vampire-start",
                        "&4&lHALLOWEEN &8» &fKrál upírů sestoupil do arény. &7Poražte ho společně.")
        ));
        broadcastPhase(1);

        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public void stopEncounter() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        if (boss != null && !boss.isDead()) {
            boss.remove();
        }

        boss = null;
        bossUuid = null;
        startedAt = 0L;
        phase = 0;
        lastAbilityAt = 0L;
        participants.clear();
        participationSeconds.clear();
        damageContribution.clear();
        clearOmenCountdown();
        if (plugin.getBossManager() != null) {
            plugin.getBossManager().stopVampireBossBar();
        }
    }

    private void tick() {
        if (!plugin.isEventEnabled()) {
            stopEncounter();
            return;
        }
        if (!isActive()) {
            finishNoReward();
            return;
        }

        int maxMinutes = Math.max(1, plugin.getConfig().getInt("bosses.vampire.encounter.max-duration-minutes", 20));
        if (System.currentTimeMillis() - startedAt >= maxMinutes * 60_000L) {
            Bukkit.broadcastMessage(plugin.color(
                    plugin.getConfig().getString("messages.vampire-timeout",
                            "&4&lHALLOWEEN &8» &cKrál upírů zmizel v temnotě. Aréna je zticha.")
            ));
            stopEncounter();
            return;
        }

        trackNearbyPlayers();
        enforceArena();
        updatePhase();
        runSpecialAbility();
    }

    private void trackNearbyPlayers() {
        Location center = getArenaLocation();
        if (center == null || boss == null) return;

        double radius = Math.max(16.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.participation-radius-blocks", 96.0D));
        double radiusSquared = radius * radius;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(center.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(center) > radiusSquared) continue;

            participants.add(player.getUniqueId());
            participationSeconds.merge(player.getUniqueId(), 1, Integer::sum);
        }
    }

    private void enforceArena() {
        if (boss == null) return;
        if (!plugin.getConfig().getBoolean("bosses.vampire.encounter.leash-to-arena", true)) return;

        Location center = getArenaLocation();
        if (center == null || !boss.getWorld().getUID().equals(center.getWorld().getUID())) return;

        double radius = Math.max(12.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.arena-radius-blocks", 48.0D));
        if (boss.getLocation().distanceSquared(center) <= radius * radius) return;

        boss.teleport(center);
    }

    private void updatePhase() {
        if (boss == null) return;

        double max = Math.max(1.0D, boss.getMaxHealth());
        double hp = Math.max(0.0D, boss.getHealth()) / max;

        int nextPhase;
        if (hp <= 0.15D) nextPhase = 4;
        else if (hp <= 0.40D) nextPhase = 3;
        else if (hp <= 0.70D) nextPhase = 2;
        else nextPhase = 1;

        if (nextPhase == phase) {
            maintainPhaseEffects();
            return;
        }

        phase = nextPhase;
        broadcastPhase(phase);
        maintainPhaseEffects();
    }

    private void runSpecialAbility() {
        if (boss == null || phase < 2) return;
        if (!plugin.getConfig().getBoolean("bosses.vampire.encounter.abilities.enabled", true)) return;

        long now = System.currentTimeMillis();
        long cooldownSeconds = switch (phase) {
            case 2 -> Math.max(4L, plugin.getConfig().getLong("bosses.vampire.encounter.abilities.phase-2-cooldown-seconds", 8L));
            case 3 -> Math.max(4L, plugin.getConfig().getLong("bosses.vampire.encounter.abilities.phase-3-cooldown-seconds", 7L));
            default -> Math.max(3L, plugin.getConfig().getLong("bosses.vampire.encounter.abilities.phase-4-cooldown-seconds", 5L));
        };
        if (now - lastAbilityAt < cooldownSeconds * 1000L) return;

        Player target = selectTarget();
        if (target == null) return;

        if (phase == 2) {
            bloodPulse(target);
        } else if (phase == 3) {
            shadowStrike(target);
        } else {
            nightfall();
        }

        lastAbilityAt = now;
    }

    private Player selectTarget() {
        Player selected = null;
        double bestDistance = Double.MAX_VALUE;
        double maxDistance = Math.max(16.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.target-radius-blocks", 48.0D));
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || !plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(boss.getWorld().getUID())) continue;
            double distance = player.getLocation().distanceSquared(boss.getLocation());
            if (distance > maxDistance * maxDistance) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                selected = player;
            }
        }
        return selected;
    }

    private void bloodPulse(Player center) {
        double radius = Math.max(3.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-2-radius", 7.0D));
        double damage = Math.max(0.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-2-damage", 4.0D));

        boss.getWorld().spawnParticle(
                org.bukkit.Particle.DUST_PLUME,
                center.getLocation().add(0, 1, 0),
                30, radius * 0.45D, 0.8D, radius * 0.45D, 0.03D
        );
        boss.getWorld().playSound(center.getLocation(), "minecraft:entity.generic.explode", 0.55f, 0.55f);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(boss.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(center.getLocation()) > radius * radius) continue;
            if (damage > 0.0D) player.damage(damage, boss);
        }
    }

    private void shadowStrike(Player target) {
        Location from = target.getLocation();
        double distance = Math.max(3.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-3-teleport-distance", 5.0D));
        double angle = java.util.concurrent.ThreadLocalRandom.current().nextDouble(0.0D, Math.PI * 2.0D);
        Location destination = from.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
        destination.setY(target.getWorld().getHighestBlockYAt(destination) + 1.0D);

        if (!destination.getBlock().isPassable()) return;
        boss.getWorld().spawnParticle(
                org.bukkit.Particle.PORTAL,
                boss.getLocation().add(0, 1, 0),
                35, 0.7D, 1.5D, 0.7D, 0.05D
        );
        if (boss.teleport(destination)) {
            boss.getWorld().spawnParticle(
                    org.bukkit.Particle.PORTAL,
                    destination.clone().add(0, 1, 0),
                    45, 0.8D, 1.5D, 0.8D, 0.05D
            );
            boss.getWorld().playSound(destination, "minecraft:entity.enderman.teleport", 0.9f, 0.55f);
            double damage = Math.max(1.0D,
                    plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-3-damage", 6.0D));
            target.damage(damage, boss);
        }
    }

    private void nightfall() {
        double radius = Math.max(4.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-4-radius", 10.0D));
        double damage = Math.max(0.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-4-damage", 6.0D));

        Location origin = boss.getLocation().add(0, 1, 0);
        boss.getWorld().spawnParticle(
                org.bukkit.Particle.DUST_PLUME,
                origin, 80, radius * 0.45D, 1.0D, radius * 0.45D, 0.04D
        );
        boss.getWorld().playSound(origin, "minecraft:entity.warden.sonic_boom", 0.6f, 0.65f);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(boss.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(origin) > radius * radius) continue;
            if (damage > 0.0D) player.damage(damage, boss);
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 0, true, true, true
            ));
        }
    }

    private void maintainPhaseEffects() {
        if (boss == null) return;

        switch (phase) {
            case 2 -> {
                addEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 12, 0);
                addEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 20 * 8, 0);
            }
            case 3 -> {
                addEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 12, 1);
                addEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 20 * 8, 0);
                addEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 20 * 12, 0);
            }
            case 4 -> {
                addEffect(org.bukkit.potion.PotionEffectType.SPEED, 20 * 12, 1);
                addEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 20 * 8, 1);
                addEffect(org.bukkit.potion.PotionEffectType.STRENGTH, 20 * 12, 1);
            }
            default -> {
            }
        }
    }

    private void addEffect(org.bukkit.potion.PotionEffectType type, int duration, int amplifier) {
        boss.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amplifier, true, false, false));
    }

    private void broadcastPhase(int currentPhase) {
        String message = switch (currentPhase) {
            case 2 -> "&5&lKRÁL UPÍRŮ &8» &dFÁZE II &7— krev se probouzí.";
            case 3 -> "&4&lKRÁL UPÍRŮ &8» &cFÁZE III &7— jeho křídla se otevírají.";
            case 4 -> "&4&lKRÁL UPÍRŮ &8» &4FÁZE IV &7— poslední odpor.";
            default -> "&5&lKRÁL UPÍRŮ &8» &7FÁZE I — lov začíná.";
        };
        Bukkit.broadcastMessage(plugin.color(message));
        if (boss != null) {
            String barLabel = switch (currentPhase) {
                case 2 -> "&5&lKRÁL UPÍRŮ &8• &dFÁZE II";
                case 3 -> "&4&lKRÁL UPÍRŮ &8• &cFÁZE III";
                case 4 -> "&4&lKRÁL UPÍRŮ &8• &4FÁZE IV";
                default -> "&5&lKRÁL UPÍRŮ &8• &7FÁZE I";
            };
            plugin.getBossManager().setVampireBossBarLabel(barLabel);
            Location location = boss.getLocation().add(0, 1.0D, 0);
            boss.getWorld().spawnParticle(
                    org.bukkit.Particle.SOUL_FIRE_FLAME,
                    location, 35, 1.5D, 1.8D, 1.5D, 0.03D
            );
            boss.getWorld().playSound(location, "minecraft:entity.wither.spawn", 0.65f, 0.75f);
        }
    }

    private void finishNoReward() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        boss = null;
        bossUuid = null;
        startedAt = 0L;
        phase = 0;
        lastAbilityAt = 0L;
        participants.clear();
        participationSeconds.clear();
        damageContribution.clear();
        clearOmenCountdown();
        if (plugin.getBossManager() != null) {
            plugin.getBossManager().stopVampireBossBar();
        }
    }

    private void finishVictory(Player killer) {
        if (victoryHandled || plugin.getDataManager().isVampireDefeated()) return;
        victoryHandled = true;

        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        int minSeconds = Math.max(0,
                plugin.getConfig().getInt("bosses.vampire.encounter.min-participation-seconds", 60));
        long participationReward = Math.max(0L,
                plugin.getConfig().getLong("bosses.vampire.encounter.participation-reward-fragments", 250L));
        long victoryReward = Math.max(0L,
                plugin.getConfig().getLong("bosses.vampire.encounter.victory-reward-fragments", 2500L));
        long topBonus = Math.max(0L,
                plugin.getConfig().getLong("bosses.vampire.encounter.top-contributor-bonus-fragments", 750L));

        Bukkit.broadcastMessage(plugin.color(
                plugin.getConfig().getString("messages.vampire-victory",
                        "&4&lHALLOWEEN &8» &6Král upírů byl poražen! Temnota ustupuje.")
        ));

        UUID topPlayer = null;
        double topDamage = 0.0D;
        for (UUID uuid : participants) {
            int seconds = participationSeconds.getOrDefault(uuid, 0);
            if (seconds < minSeconds) continue;

            Player player = Bukkit.getPlayer(uuid);
            if (participationReward > 0L) {
                plugin.getService().addFragments(uuid, participationReward, "boss-participation");
            }
            if (player != null) {
                player.sendMessage(plugin.color("&6HALLOWEEN &8» &fZa účast v boji získáváš &e+"
                        + participationReward + " &ffragmentů."));
            }
            double damageDealt = damageContribution.getOrDefault(uuid, 0.0D);
            if (Double.isFinite(damageDealt) && damageDealt > topDamage) {
                topDamage = damageDealt;
                topPlayer = uuid;
            }
        }

        if (killer != null && participants.contains(killer.getUniqueId()) && victoryReward > 0L) {
            plugin.getService().addFragments(killer.getUniqueId(), victoryReward, "boss-victory");
            killer.sendMessage(plugin.color("&6HALLOWEEN &8» &6Vražda Krále upírů: &e+"
                    + victoryReward + " &ffragmentů."));
        }

        if (topPlayer != null && topBonus > 0L) {
            plugin.getService().addFragments(topPlayer, topBonus, "boss-top-contributor");
            Player top = Bukkit.getPlayer(topPlayer);
            if (top != null) {
                top.sendMessage(plugin.color("&5HALLOWEEN &8» &dNejvětší poškození bosse: &e+"
                        + topBonus + " &dfragmentů."));
            }
        }

        plugin.getDataManager().markVampireDefeated();
        plugin.getDataManager().save();
        stopEncounter();
    }

    private LivingEntity spawnMythicMob(Location location) {
        try {
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object mythic = mythicBukkitClass.getMethod("inst").invoke(null);
            Object mobManager = mythic.getClass().getMethod("getMobManager").invoke(mythic);

            String mobId = plugin.getBossManager().getVampireSpec().id();
            Method getMythicMob = mobManager.getClass().getMethod("getMythicMob", String.class);
            Object optional = getMythicMob.invoke(mobManager, mobId);
            if (!(optional instanceof Optional<?> maybe) || maybe.isEmpty()) {
                return null;
            }

            Object mythicMob = maybe.get();
            Class<?> adapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter");
            Object abstractLocation = adapterClass.getMethod("adapt", Location.class).invoke(null, location);

            Method spawn = findSpawnMethod(mythicMob.getClass());
            if (spawn == null) return null;

            Object activeMob = spawn.invoke(mythicMob, abstractLocation, 1.0D);
            if (activeMob == null) return null;

            Object entityRef = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
            Object bukkitEntity = entityRef.getClass().getMethod("getBukkitEntity").invoke(entityRef);
            return bukkitEntity instanceof LivingEntity living ? living : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("MythicMobs API spawn failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    private Method findSpawnMethod(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals("spawn") || method.getParameterCount() != 2) continue;
            Class<?> second = method.getParameterTypes()[1];
            if (second == double.class || second == Double.class || Number.class.isAssignableFrom(second)) {
                return method;
            }
        }
        return null;
    }

    private Location getArenaLocation() {
        String worldName = plugin.getConfig().getString("bosses.vampire.arena.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(
                world,
                plugin.getConfig().getDouble("bosses.vampire.arena.x", 0.0D),
                plugin.getConfig().getDouble("bosses.vampire.arena.y", 100.0D),
                plugin.getConfig().getDouble("bosses.vampire.arena.z", 0.0D)
        );
    }

    private Player resolvePlayer(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBossDamaged(EntityDamageByEntityEvent event) {
        if (isTrackedVampireBoss(event.getEntity())) {
            Player player = resolvePlayer(event);
            if (player != null && plugin.isEligibleGameplayPlayer(player)) {
                UUID playerId = player.getUniqueId();
                participants.add(playerId);
                double damage = event.getFinalDamage();
                if (Double.isFinite(damage) && damage > 0.0D) {
                    damageContribution.merge(playerId, damage, Double::sum);
                }
            }
            return;
        }

        if (boss == null || boss.isDead() || !boss.isValid()) return;
        if (!event.getDamager().getUniqueId().equals(boss.getUniqueId())) return;
        if (phase < 2 || event.isCancelled()) return;

        double maximum = Math.max(1.0D, boss.getMaxHealth());
        double heal = Math.min(maximum * 0.02D, Math.max(0.25D, event.getFinalDamage() * 0.15D));
        double missing = Math.max(0.0D, maximum - boss.getHealth());
        if (missing > 0.0D) {
            boss.setHealth(Math.min(maximum, boss.getHealth() + Math.min(heal, missing)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        if (!isTrackedVampireBoss(event.getEntity())) return;
        if (!plugin.isEventEnabled()) {
            finishNoReward();
            return;
        }
        finishVictory(event.getEntity().getKiller());
    }
}
