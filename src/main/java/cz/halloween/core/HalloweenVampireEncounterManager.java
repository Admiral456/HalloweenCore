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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class HalloweenVampireEncounterManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey bossKey;

    private LivingEntity boss;
    private UUID bossUuid;
    private BukkitTask tickTask;
    private BukkitTask summoningTask;
    private long summoningDueAt;
    private long naturalRetryAfterAt;
    private int summoningWarningStage;
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

    public void startNaturalSummoningMonitor() {
        if (summoningTask != null) return;
        summoningTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tickNaturalSummoning, 20L, 20L
        );
    }

    public void stopNaturalSummoningMonitor() {
        if (summoningTask != null) {
            summoningTask.cancel();
            summoningTask = null;
        }
        summoningDueAt = 0L;
        summoningWarningStage = 0;
    }

    public void delayNaturalSummoningAfterStop() {
        summoningDueAt = 0L;
        summoningWarningStage = 0;
        long retryMinutes = Math.max(1L,
                plugin.getConfig().getLong("bosses.vampire.summoning.retry-delay-minutes", 20L));
        naturalRetryAfterAt = System.currentTimeMillis() + retryMinutes * 60_000L;
    }

    private void tickNaturalSummoning() {
        if (!plugin.getConfig().getBoolean("bosses.vampire.summoning.automatic", true)) {
            summoningDueAt = 0L;
            summoningWarningStage = 0;
            return;
        }
        if (!plugin.isEventEnabled()) {
            summoningDueAt = 0L;
            summoningWarningStage = 0;
            return;
        }
        if (isActive() || plugin.getDataManager().isVampireDefeated()
                || !plugin.getDataManager().isFinaleUnlocked()) {
            summoningDueAt = 0L;
            summoningWarningStage = 0;
            return;
        }
        if (!plugin.getBossManager().isVampireReady()) {
            summoningDueAt = 0L;
            summoningWarningStage = 0;
            return;
        }

        long eligibleOnline = Bukkit.getOnlinePlayers().stream()
                .filter(plugin::isEligibleGameplayPlayer)
                .count();
        int minimumPlayers = Math.max(1,
                plugin.getConfig().getInt("bosses.vampire.summoning.minimum-online-players", 1));
        if (eligibleOnline < minimumPlayers) {
            summoningDueAt = 0L;
            summoningWarningStage = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (now < naturalRetryAfterAt) return;

        if (summoningDueAt == 0L) {
            long delaySeconds = Math.max(180L,
                    plugin.getConfig().getLong("bosses.vampire.summoning.delay-after-readiness-seconds", 300L));
            summoningDueAt = now + delaySeconds * 1000L;
            summoningWarningStage = 0;
            Bukkit.broadcastMessage(plugin.color(plugin.getConfig().getString(
                    "messages.vampire-omen",
                    "&5&lHALLOWEEN &8» &7Vzduch ztěžkl. Z hlubin arény se ozývá tlukot, který nepatří živým..."
            )));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                player.playSound(player.getLocation(), "minecraft:ambient.cave", 0.8f, 0.55f);
            }
            return;
        }

        long remainingSeconds = Math.max(0L, (summoningDueAt - now + 999L) / 1000L);
        if (remainingSeconds <= 180L && summoningWarningStage < 1) {
            announceSummoningWarning("messages.vampire-summoning-3m",
                    "&4&lHALLOWEEN &8» &cNad arénou se trhá závoj. Král upírů se probudí za 3 minuty.");
            summoningWarningStage = 1;
        }
        if (remainingSeconds <= 60L && summoningWarningStage < 2) {
            announceSummoningWarning("messages.vampire-summoning-1m",
                    "&4&lHALLOWEEN &8» &cKřídla se rozevírají. Do probuzení Krále upírů zbývá minuta.");
            summoningWarningStage = 2;
        }
        if (remainingSeconds <= 10L && summoningWarningStage < 3) {
            announceSummoningWarning("messages.vampire-summoning-10s",
                    "&4&lHALLOWEEN &8» &c10 sekund. Opusťte runy a připravte se na boj!");
            summoningWarningStage = 3;
        }
        if (remainingSeconds > 0L) return;

        if (startEncounter()) {
            summoningDueAt = 0L;
            summoningWarningStage = 0;
            naturalRetryAfterAt = 0L;
        } else {
            summoningDueAt = 0L;
            summoningWarningStage = 0;
            long retryMinutes = Math.max(1L,
                    plugin.getConfig().getLong("bosses.vampire.summoning.retry-delay-minutes", 20L));
            naturalRetryAfterAt = now + retryMinutes * 60_000L;
            plugin.getLogger().warning("Natural Vampire summoning failed. Retrying after "
                    + retryMinutes + " minute(s); inspect /halloween boss status and the MythicMobs definition.");
        }
    }

    private void announceSummoningWarning(String path, String fallback) {
        Bukkit.broadcastMessage(plugin.color(plugin.getConfig().getString(path, fallback)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            player.sendTitle(
                    plugin.color("&4&lKRÁL UPÍRŮ"),
                    plugin.color(plugin.getConfig().getString(path, fallback)),
                    5, 35, 10
            );
            player.playSound(player.getLocation(), "minecraft:block.bell.use", 0.75f, 0.55f);
        }
    }

    public boolean startEncounter() {
        if (!plugin.isEventEnabled()) return false;
        if (isActive()) return false;
        if (!plugin.getConfig().getBoolean("bosses.vampire.enabled", false)) return false;
        if (plugin.getDataManager().isVampireDefeated()) return false;
        if (!plugin.getBossManager().isVampireReady()) return false;

        // The configured point is the arena's centre block at floor level.
        Location location = getArenaLocation();
        if (location == null) {
            plugin.getLogger().warning("Cannot start Vampire encounter: arena centre is not configured or its world is not loaded.");
            return false;
        }

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
        summoningDueAt = 0L;
        summoningWarningStage = 0;
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
            delayNaturalSummoningAfterStop();
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
        if (boss == null || phase < 1) return;
        if (!plugin.getConfig().getBoolean("bosses.vampire.encounter.abilities.enabled", true)) return;

        long now = System.currentTimeMillis();
        long cooldownSeconds = switch (phase) {
            case 1 -> Math.max(8L, plugin.getConfig().getLong("bosses.vampire.encounter.abilities.phase-1-cooldown-seconds", 14L));
            case 2 -> Math.max(6L, plugin.getConfig().getLong("bosses.vampire.encounter.abilities.phase-2-cooldown-seconds", 11L));
            case 3 -> Math.max(6L, plugin.getConfig().getLong("bosses.vampire.encounter.abilities.phase-3-cooldown-seconds", 10L));
            default -> Math.max(6L, plugin.getConfig().getLong("bosses.vampire.encounter.abilities.phase-4-cooldown-seconds", 9L));
        };
        if (now - lastAbilityAt < cooldownSeconds * 1000L) return;

        Player target = selectTarget();
        if (target == null) return;

        switch (phase) {
            case 1 -> falseSigil(target);
            case 2 -> bloodPulse(target);
            case 3 -> mirrorStrike(target);
            default -> nightfall();
        }
        lastAbilityAt = now;
    }

    private void falseSigil(Player target) {
        if (boss == null) return;
        Location mark = target.getLocation().clone();
        UUID encounterId = bossUuid;
        double radius = Math.max(1.5D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-1-radius", 2.5D));
        double damage = Math.max(0.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-1-damage", 3.0D));

        drawRing(mark, radius, org.bukkit.Particle.SOUL_FIRE_FLAME, 28);
        target.sendTitle(plugin.color("&5&lFALEŠNÁ KOŘIST"),
                plugin.color("&7Runy pod tebou vybuchnou. Uteč!"), 0, 28, 5);
        target.playSound(target.getLocation(), "minecraft:block.amethyst_block.chime", 0.9f, 0.55f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isSameEncounter(encounterId)) return;
            mark.getWorld().spawnParticle(org.bukkit.Particle.SOUL, mark.clone().add(0, 0.15D, 0),
                    45, radius * 0.35D, 0.35D, radius * 0.35D, 0.02D);
            mark.getWorld().playSound(mark, "minecraft:entity.generic.explode", 0.6f, 0.65f);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                if (!player.getWorld().getUID().equals(mark.getWorld().getUID())) continue;
                if (player.getLocation().distanceSquared(mark) > radius * radius) continue;
                if (damage > 0.0D) player.damage(damage, boss);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 25, 0, true, true, true
                ));
            }
        }, 30L);
    }

    private void drawRing(Location center, double radius, org.bukkit.Particle particle, int points) {
        if (center == null || center.getWorld() == null || radius <= 0.0D || points < 3) return;
        for (int i = 0; i < points; i++) {
            double angle = 2.0D * Math.PI * i / points;
            Location point = center.clone().add(
                    Math.cos(angle) * radius, 0.12D, Math.sin(angle) * radius
            );
            center.getWorld().spawnParticle(particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private boolean isSameEncounter(UUID encounterId) {
        return encounterId != null && encounterId.equals(bossUuid)
                && boss != null && boss.isValid() && !boss.isDead();
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

    private void bloodPulse(Player target) {
        if (boss == null) return;
        Location mark = target.getLocation().clone();
        UUID encounterId = bossUuid;
        double radius = Math.max(3.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-2-radius", 7.0D));
        double damage = Math.max(0.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-2-damage", 5.0D));

        drawRing(mark, radius, org.bukkit.Particle.DUST_PLUME, 36);
        mark.getWorld().playSound(mark, "minecraft:entity.warden.heartbeat", 0.8f, 0.55f);
        target.sendMessage(plugin.color("&5Krvavý puls &8» &cKruh se uzavírá. Značenému místu se vyhni!"));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isSameEncounter(encounterId)) return;
            mark.getWorld().spawnParticle(org.bukkit.Particle.DUST_PLUME, mark.clone().add(0, 0.5D, 0),
                    70, radius * 0.45D, 0.8D, radius * 0.45D, 0.02D);
            mark.getWorld().playSound(mark, "minecraft:entity.generic.explode", 0.75f, 0.45f);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                if (!player.getWorld().getUID().equals(mark.getWorld().getUID())) continue;
                if (player.getLocation().distanceSquared(mark) > radius * radius) continue;
                if (damage > 0.0D) player.damage(damage, boss);
                player.setVelocity(player.getVelocity().add(new org.bukkit.util.Vector(
                        (player.getLocation().getX() - mark.getX()) * 0.04D, 0.22D,
                        (player.getLocation().getZ() - mark.getZ()) * 0.04D
                )));
            }
        }, 35L);
    }

    private void mirrorStrike(Player target) {
        if (boss == null) return;
        Location anchor = target.getLocation().clone();
        UUID encounterId = bossUuid;
        double markerDistance = Math.max(3.5D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-3-teleport-distance", 5.0D));
        double radius = Math.max(1.5D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-3-radius", 2.75D));
        double damage = Math.max(0.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-3-damage", 7.0D));
        List<Location> markers = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            double angle = (2.0D * Math.PI * i / 3.0D) + (ThreadLocalRandom.current().nextDouble(-0.12D, 0.12D));
            Location marker = anchor.clone().add(Math.cos(angle) * markerDistance, 0.0D,
                    Math.sin(angle) * markerDistance);
            markers.add(marker);
            drawRing(marker, radius, org.bukkit.Particle.PORTAL, 24);
            marker.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, marker.clone().add(0, 0.7D, 0),
                    16, 0.65D, 0.8D, 0.65D, 0.02D);
        }
        anchor.getWorld().playSound(anchor, "minecraft:entity.enderman.stare", 0.9f, 0.45f);
        target.sendTitle(plugin.color("&5&lZRCADLOVÝ VÝPAD"),
                plugin.color("&7Tři stíny, jedna čepel. Nezůstávej u run!"), 0, 35, 5);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isSameEncounter(encounterId)) return;
            Location impact = markers.get(ThreadLocalRandom.current().nextInt(markers.size()));
            if (impact.getBlock().isPassable()
                    && impact.clone().add(0, 1, 0).getBlock().isPassable()) {
                boss.teleport(impact);
            }
            impact.getWorld().spawnParticle(org.bukkit.Particle.CRIT, impact.clone().add(0, 0.8D, 0),
                    55, 0.9D, 1.1D, 0.9D, 0.15D);
            impact.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, impact.clone().add(0, 0.4D, 0),
                    40, 0.65D, 0.6D, 0.65D, 0.02D);
            impact.getWorld().playSound(impact, "minecraft:entity.player.attack.sweep", 1.0f, 0.45f);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                if (!player.getWorld().getUID().equals(impact.getWorld().getUID())) continue;
                if (player.getLocation().distanceSquared(impact) > radius * radius) continue;
                if (damage > 0.0D) player.damage(damage, boss);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 30, 0, true, true, true
                ));
            }
        }, 35L);
    }

    private void nightfall() {
        if (boss == null) return;
        Location origin = boss.getLocation().clone();
        UUID encounterId = bossUuid;
        double radius = Math.max(4.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-4-radius", 10.0D));
        double damage = Math.max(0.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-4-damage", 7.0D));

        // Inner circles are deceptive echoes; only the outer circle marks the actual blast radius.
        drawRing(origin, radius * 0.55D, org.bukkit.Particle.SOUL_FIRE_FLAME, 32);
        drawRing(origin, radius * 0.78D, org.bukkit.Particle.SOUL_FIRE_FLAME, 40);
        drawRing(origin, radius, org.bukkit.Particle.SOUL_FIRE_FLAME, 52);
        origin.getWorld().playSound(origin, "minecraft:entity.warden.sonic_boom", 0.8f, 0.45f);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(origin.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(origin) <= radius * radius * 1.5D) {
                player.sendMessage(plugin.color("&4ZATMĚNÍ &8» &7Vnitřní kruhy klamou. Skutečný dosah ukazuje vnější runa!"));
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isSameEncounter(encounterId)) return;
            origin.getWorld().spawnParticle(org.bukkit.Particle.DUST_PLUME, origin.clone().add(0, 0.8D, 0),
                    100, radius * 0.42D, 1.0D, radius * 0.42D, 0.05D);
            origin.getWorld().playSound(origin, "minecraft:entity.warden.sonic_boom", 0.9f, 0.65f);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                if (!player.getWorld().getUID().equals(origin.getWorld().getUID())) continue;
                if (player.getLocation().distanceSquared(origin) > radius * radius) continue;
                if (damage > 0.0D) player.damage(damage, boss);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 60, 0, true, true, true
                ));
            }
        }, 40L);
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
                plugin.getConfig().getLong("bosses.vampire.encounter.top-contributor-bonus-fragments", 2000L));
        double minimumDamagePercent = Math.max(0.0D, Math.min(100.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.minimum-damage-percent", 2.5D)));
        double maximumHealth = boss == null ? 1.0D : Math.max(1.0D, boss.getMaxHealth());
        double minimumDamage = maximumHealth * minimumDamagePercent / 100.0D;

        Bukkit.broadcastMessage(plugin.color(
                plugin.getConfig().getString("messages.vampire-victory",
                        "&4&lHALLOWEEN &8» &6Král upírů byl poražen! Temnota ustupuje.")
        ));

        UUID topPlayer = null;
        double topDamage = 0.0D;
        Set<UUID> qualifiedParticipants = new HashSet<>();
        for (UUID uuid : participants) {
            int seconds = participationSeconds.getOrDefault(uuid, 0);
            double damageDealt = damageContribution.getOrDefault(uuid, 0.0D);
            if (seconds < minSeconds || !Double.isFinite(damageDealt) || damageDealt < minimumDamage) continue;

            qualifiedParticipants.add(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (participationReward > 0L) {
                plugin.getService().addFragments(uuid, participationReward, "boss-participation");
            }
            if (player != null) {
                player.sendMessage(plugin.color("&6HALLOWEEN &8» &fZa aktivní účast získáváš &e+"
                        + participationReward + " &ffragmentů."));
            }
            if (damageDealt > topDamage) {
                topDamage = damageDealt;
                topPlayer = uuid;
            }
        }

        if (killer != null && qualifiedParticipants.contains(killer.getUniqueId()) && victoryReward > 0L) {
            plugin.getService().addFragments(killer.getUniqueId(), victoryReward, "boss-victory");
            killer.sendMessage(plugin.color("&6HALLOWEEN &8» &6Vražda Krále upírů: &e+"
                    + victoryReward + " &ffragmentů."));
        }

        if (topPlayer != null && qualifiedParticipants.contains(topPlayer) && topBonus > 0L) {
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
