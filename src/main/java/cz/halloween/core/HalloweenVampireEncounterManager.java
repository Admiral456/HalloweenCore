package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
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
import java.util.function.Consumer;

public final class HalloweenVampireEncounterManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey bossKey;
    private final Set<String> loggedWarnings = new HashSet<>();

    private LivingEntity boss;
    private LivingEntity previewEntity;
    private BukkitTask previewCleanupTask;
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
    private boolean testEncounter;
    private long startedAt;
    private int phase;
    private long lastAbilityAt;
    private boolean modelAnimationWarningLogged;

    public HalloweenVampireEncounterManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "vampire_boss");
    }

    private void warnOnce(String key, String message) {
        if (loggedWarnings.add(key)) {
            plugin.getLogger().warning(message);
        }
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
            warnOnce("natural-summoning-failed",
                    "Natural Vampire summoning failed; the next attempt is in " + retryMinutes
                            + " minute(s). Repeated retries will not spam the console. Check /halloween boss status and the MythicMobs definition.");
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

    /**
     * Safely spawns a temporary, invulnerable copy of the configured MythicMobs boss for model testing.
     * It never starts the real encounter, registers participants, or grants rewards.
     */
    public boolean previewModelAnimation(Player player, String animation) {
        if (player == null || !player.isOnline() || animation == null) return false;
        if (isActive()) return false;
        Set<String> allowed = Set.of("idle", "walk", "attack", "fly",
                "false_sigil", "blood_pulse", "mirror_strike", "nightfall");
        if (!allowed.contains(animation.toLowerCase(java.util.Locale.ROOT))) return false;
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MythicMobs")
                || !plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            return false;
        }

        stopModelPreview();
        Location location = player.getLocation().clone();
        org.bukkit.util.Vector direction = location.getDirection().setY(0.0D);
        if (direction.lengthSquared() < 0.001D) direction = new org.bukkit.util.Vector(0, 0, 1);
        direction.normalize().multiply(4.0D);
        location.add(direction);
        location.setPitch(0.0F);
        location.setYaw(player.getLocation().getYaw() + 180.0F);

        LivingEntity preview = spawnMythicMob(location);
        if (preview == null) return false;
        preview.setInvulnerable(true);
        preview.setSilent(true);
        preview.setGravity(false);
        preview.setCustomName(plugin.color("&d&lNÁHLED MODELU KRÁLE UPÍRŮ"));
        preview.setCustomNameVisible(true);
        if (preview instanceof org.bukkit.entity.Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
        }
        previewEntity = preview;

        String requestedAnimation = animation.toLowerCase(java.util.Locale.ROOT);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (previewEntity != preview || !preview.isValid() || preview.isDead()) return;
            if (!playModelAnimation(preview, requestedAnimation)) {
                player.sendMessage(plugin.color("&eModel byl vytvořen, ale animaci &f" + requestedAnimation
                        + " &eModelEngine nepřehrál. Zkontroluj import modelu a verzi API."));
            } else {
                player.sendMessage(plugin.color("&aAnimace &f" + requestedAnimation
                        + " &abyla spuštěna na dočasném modelu."));
            }
        }, 1L);

        previewCleanupTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::stopModelPreview, 200L);
        return true;
    }

    public void stopModelPreview() {
        if (previewCleanupTask != null) {
            previewCleanupTask.cancel();
            previewCleanupTask = null;
        }
        if (previewEntity != null) {
            if (previewEntity.isValid() && !previewEntity.isDead()) previewEntity.remove();
            previewEntity = null;
        }
    }

    /** Normal finale start: enforce progression, event, and model readiness. */
    public boolean startEncounter() {
        return startEncounter(false);
    }

    /**
     * Admin-only test start. Skips global progress, finale unlock, one-time defeat,
     * the normal enable gate and the model-ready gate, but still requires an arena
     * location and a real MythicMobs mob definition so this tests the actual boss.
     * Test kills grant no rewards and cannot mark the finale as completed.
     */
    public boolean startTestEncounter() {
        return startEncounter(true);
    }

    private boolean startEncounter(boolean testMode) {
        if (isActive()) return false;
        if (!testMode && !plugin.isEventEnabled()) return false;
        if (!testMode && !plugin.getConfig().getBoolean("bosses.vampire.enabled", false)) return false;
        if (!testMode && plugin.getDataManager().isVampireDefeated()) return false;
        if (!testMode && !plugin.getBossManager().isVampireReady()) return false;

        Location location = getArenaLocation();
        if (location == null) {
            warnOnce("arena-centre-unavailable",
                    "Cannot start Vampire encounter: arena centre is not configured or its world is not loaded. Further identical warnings are suppressed.");
            return false;
        }

        stopModelPreview();
        LivingEntity spawned = spawnMythicMob(location);
        boolean fallbackTestBoss = false;
        if (spawned == null && testMode) {
            spawned = spawnTestFallback(location);
            fallbackTestBoss = spawned != null;
        }
        if (spawned == null) {
            warnOnce("mythic-boss-spawn-failed",
                    "MythicMobs could not spawn boss ID '"
                            + plugin.getBossManager().getVampireSpec().id()
                            + "'. Check plugins/MythicMobs/Mobs/vampire-king.yml and /mm reload.");
            return false;
        }

        boss = spawned;
        bossUuid = spawned.getUniqueId();
        testEncounter = testMode;
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
        if (testMode) {
            Bukkit.broadcastMessage(plugin.color(fallbackTestBoss
                    ? "&c&lTEST BOSSE &8» &fVyvolán zkušební zombie boss, protože MythicMobs definice nebyla dostupná. &7Bez modelu, progressu a odměn."
                    : "&c&lTEST BOSSE &8» &fKrál upírů byl vyvolán bez progresu. &7Test neodemkne finále a nedává odměny."));
        } else {
            Bukkit.broadcastMessage(plugin.color(
                    plugin.getConfig().getString("messages.vampire-start",
                            "&4&lHALLOWEEN &8» &fKrál upírů sestoupil do středu arény. &7Poražte ho společně.")
            ));
        }
        broadcastPhase(1);
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    public void stopEncounter() {
        stopModelPreview();
        testEncounter = false;
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
        if (!plugin.isEventEnabled() && !testEncounter) {
            stopEncounter();
            return;
        }
        if (!isActive()) {
            finishNoReward();
            return;
        }

        int maxMinutes = Math.max(1, plugin.getConfig().getInt("bosses.vampire.encounter.max-duration-minutes", 20));
        if (System.currentTimeMillis() - startedAt >= maxMinutes * 60_000L) {
            if (testEncounter) {
                Bukkit.broadcastMessage(plugin.color("&c&lTEST BOSSE &8» &7Testovací souboj skončil časovým limitem; progress ani odměny se nemění."));
            } else {
                Bukkit.broadcastMessage(plugin.color(
                        plugin.getConfig().getString("messages.vampire-timeout",
                                "&4&lHALLOWEEN &8» &cKrál upírů zmizel v temnotě. Aréna je zticha.")
                ));
                delayNaturalSummoningAfterStop();
            }
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
        if (phase == 4) {
            // The final phase adds the looping wing/fly animation; attack animations can layer over it.
            playModelAnimation(boss, "fly");
        }
        broadcastPhase(phase);
        maintainPhaseEffects();
    }


    /**
     * Shows the boss attack telegraphs at a player's location without spawning a boss or damaging anyone.
     * This is intentionally usable while the real model is still locked behind its readiness gate.
     */
    public boolean previewAbility(Player player, int previewPhase) {
        if (player == null || !player.isOnline() || previewPhase < 1 || previewPhase > 4) return false;

        Location anchor = player.getLocation().clone();
        switch (previewPhase) {
            case 1 -> {
                double radius = Math.max(2.5D,
                        plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-1-radius", 2.5D));
                player.sendTitle(plugin.color("&5&lFALEŠNÁ KOŘIST"),
                        plugin.color("&7Runy se rozžhaví, energie se stáhne a kruh exploduje."), 0, 34, 6);
                anchor.getWorld().playSound(anchor, "minecraft:block.amethyst_block.chime", 0.9f, 0.55f);
                animateFalseLoot(anchor, radius);
            }
            case 2 -> {
                double radius = Math.max(3.0D,
                        plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-2-radius", 7.0D));
                player.sendTitle(plugin.color("&4&lKRVAVÝ PULS"),
                        plugin.color("&7Energetická vlna se šíří od středu. Připrav se na náraz."), 0, 38, 6);
                anchor.getWorld().playSound(anchor, "minecraft:entity.warden.heartbeat", 0.8f, 0.55f);
                animateBloodPulse(anchor, radius);
            }
            case 3 -> {
                double markerDistance = Math.max(3.5D,
                        plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-3-teleport-distance", 5.0D));
                double radius = Math.max(1.5D,
                        plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-3-radius", 2.75D));
                List<Location> markers = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    double angle = (2.0D * Math.PI * i / 3.0D);
                    markers.add(anchor.clone().add(Math.cos(angle) * markerDistance, 0.0D,
                            Math.sin(angle) * markerDistance));
                }
                player.sendTitle(plugin.color("&d&lZRCADLOVÝ VÝPAD"),
                        plugin.color("&7Tři magické portály. Jeden z nich provede skutečný výpad."), 0, 38, 6);
                anchor.getWorld().playSound(anchor, "minecraft:entity.enderman.stare", 0.9f, 0.45f);
                animateMirrorStrike(anchor, markers, radius);
            }
            case 4 -> {
                double radius = Math.max(4.0D,
                        plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-4-radius", 10.0D));
                player.sendTitle(plugin.color("&4&lZATMĚNÍ"),
                        plugin.color("&7Tři runové kruhy pulzují. Temná energie vrcholí silným výbojem."), 0, 42, 6);
                anchor.getWorld().playSound(anchor, "minecraft:entity.warden.sonic_boom", 0.8f, 0.45f);
                animateEclipse(anchor, radius);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void animateFalseLoot(Location anchor, double maxRadius) {
        animateFalseLoot(anchor, maxRadius, () -> {});
    }

    private void animateFalseLoot(Location anchor, double maxRadius, Runnable onImpact) {
        final int totalFrames = 20;
        final int[] frame = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (anchor.getWorld() == null) {
                task[0].cancel();
                return;
            }
            int f = frame[0]++;
            double progress = f / (double) totalFrames;
            double radius = maxRadius * (0.48D + 0.52D * progress);
            World world = anchor.getWorld();

            drawDustRing(anchor, radius, 48, Color.fromRGB(255, 42, 30), 1.25F, 0.12D);
            drawDustRing(anchor, radius * 0.68D, 36, Color.fromRGB(255, 126, 36), 0.9F, 0.10D);
            drawRing(anchor, radius * 0.38D, Particle.SOUL_FIRE_FLAME, 20);

            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4.0D - f * 0.075D;
                Location rune = anchor.clone().add(Math.cos(angle) * radius * 0.82D, 0.18D,
                        Math.sin(angle) * radius * 0.82D);
                world.spawnParticle(Particle.ENCHANT, rune, 3, 0.07D, 0.03D, 0.07D, 0.02D);
                if (f % 2 == 0) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, rune, 1, 0.0D, 0.04D, 0.0D, 0.005D);
                }
            }

            for (int i = 0; i < 5; i++) {
                double angle = f * 0.20D + i * Math.PI * 2.0D / 5.0D;
                double spiralRadius = radius * (0.15D + 0.025D * f);
                double y = 0.18D + (f % 8) * 0.12D;
                Location spiral = anchor.clone().add(Math.cos(angle) * spiralRadius, y,
                        Math.sin(angle) * spiralRadius);
                world.spawnParticle(Particle.SOUL, spiral, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            if (f >= totalFrames) {
                drawDustRing(anchor, maxRadius * 1.04D, 64, Color.fromRGB(255, 210, 105), 1.5F, 0.16D);
                drawRing(anchor, maxRadius * 0.78D, Particle.SOUL, 44);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, anchor.clone().add(0, 0.5D, 0),
                        65, maxRadius * 0.42D, 0.65D, maxRadius * 0.42D, 0.025D);
                world.spawnParticle(Particle.FLASH, anchor.clone().add(0, 0.6D, 0), 1, 0, 0, 0, 0);
                world.playSound(anchor, "minecraft:entity.generic.explode", 0.8f, 0.65f);
                task[0].cancel();
                onImpact.run();
            }
        }, 0L, 2L);
    }

    private void animateBloodPulse(Location anchor, double maxRadius) {
        animateBloodPulse(anchor, maxRadius, () -> {});
    }

    private void animateBloodPulse(Location anchor, double maxRadius, Runnable onImpact) {
        final int totalFrames = 28;
        final int[] frame = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (anchor.getWorld() == null) {
                task[0].cancel();
                return;
            }
            int f = frame[0]++;
            double progress = f / (double) totalFrames;
            double radius = maxRadius * (0.08D + 0.92D * progress);
            World world = anchor.getWorld();

            drawDustRing(anchor, radius, 64, Color.fromRGB(255, 32, 28), 1.35F, 0.12D);
            drawRing(anchor, radius * 0.94D, Particle.DUST_PLUME, 36);
            drawDustRing(anchor, Math.max(0.25D, radius * 0.56D), 36,
                    Color.fromRGB(255, 104, 38), 0.8F, 0.18D);

            for (int i = 0; i < 10; i++) {
                double angle = i * Math.PI * 2.0D / 10.0D + f * 0.06D;
                Location spark = anchor.clone().add(Math.cos(angle) * radius, 0.20D + (i % 3) * 0.12D,
                        Math.sin(angle) * radius);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, spark, 1, 0.03D, 0.12D, 0.03D, 0.01D);
            }

            world.spawnParticle(Particle.ASH, anchor.clone().add(0, 0.3D, 0),
                    8, radius * 0.24D, 0.08D, radius * 0.24D, 0.01D);
            if (f >= totalFrames) {
                drawDustRing(anchor, maxRadius, 88, Color.fromRGB(255, 190, 92), 1.45F, 0.15D);
                drawRing(anchor, maxRadius * 0.72D, Particle.FLAME, 64);
                world.spawnParticle(Particle.DUST_PLUME, anchor.clone().add(0, 0.55D, 0),
                        95, maxRadius * 0.45D, 0.75D, maxRadius * 0.45D, 0.035D);
                world.playSound(anchor, "minecraft:entity.generic.explode", 0.85f, 0.45f);
                task[0].cancel();
                onImpact.run();
            }
        }, 0L, 2L);
    }

    private void animateMirrorStrike(Location anchor, List<Location> markers, double radius) {
        animateMirrorStrike(anchor, markers, radius, impact -> {});
    }

    private void animateMirrorStrike(Location anchor, List<Location> markers, double radius, Consumer<Location> onImpact) {
        final int totalFrames = 18;
        final int[] frame = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (anchor.getWorld() == null) {
                task[0].cancel();
                return;
            }
            int f = frame[0]++;
            World world = anchor.getWorld();
            for (int index = 0; index < markers.size(); index++) {
                Location marker = markers.get(index);
                double pulse = 0.88D + 0.12D * Math.sin(f * 0.55D + index);
                drawDustRing(marker, radius * pulse, 36, Color.fromRGB(178, 70, 255), 1.05F, 0.12D);
                drawRing(marker, radius * 0.76D, Particle.PORTAL, 26);

                for (int i = 0; i < 5; i++) {
                    double angle = f * 0.22D + i * Math.PI * 2.0D / 5.0D + index;
                    double spiralRadius = radius * (0.15D + (f % 9) * 0.045D);
                    double y = 0.25D + (f % 9) * 0.16D;
                    Location spiral = marker.clone().add(Math.cos(angle) * spiralRadius, y,
                            Math.sin(angle) * spiralRadius);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, spiral, 1, 0.0D, 0.03D, 0.0D, 0.005D);
                    if (i % 2 == 0) world.spawnParticle(Particle.PORTAL, spiral, 2, 0.05D, 0.08D, 0.05D, 0.01D);
                }
            }

            if (f >= totalFrames) {
                task[0].cancel();
                Location impact = markers.get(ThreadLocalRandom.current().nextInt(markers.size()));
                animateMirrorImpact(impact, radius, () -> onImpact.accept(impact));
            }
        }, 0L, 2L);
    }

    private void animateMirrorImpact(Location impact, double maxRadius) {
        animateMirrorImpact(impact, maxRadius, () -> {});
    }

    private void animateMirrorImpact(Location impact, double maxRadius, Runnable onImpact) {
        final int totalFrames = 12;
        final int[] frame = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (impact.getWorld() == null) {
                task[0].cancel();
                return;
            }
            int f = frame[0]++;
            double radius = maxRadius * (0.15D + 0.85D * (f / (double) totalFrames));
            World world = impact.getWorld();

            drawDustRing(impact, radius, 48, Color.fromRGB(255, 86, 220), 1.2F, 0.15D);
            drawRing(impact, radius * 0.7D, Particle.CRIT, 36);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, impact.clone().add(0, 0.6D, 0),
                    35, radius * 0.35D, 0.65D, radius * 0.35D, 0.03D);

            if (f >= totalFrames) {
                world.spawnParticle(Particle.FLASH, impact.clone().add(0, 0.75D, 0), 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.CRIT, impact.clone().add(0, 0.7D, 0),
                        50, 0.8D, 0.95D, 0.8D, 0.12D);
                world.playSound(impact, "minecraft:entity.player.attack.sweep", 1.0f, 0.45f);
                task[0].cancel();
                onImpact.run();
            }
        }, 0L, 2L);
    }

    private void animateEclipse(Location anchor, double maxRadius) {
        animateEclipse(anchor, maxRadius, () -> {});
    }

    private void animateEclipse(Location anchor, double maxRadius, Runnable onImpact) {
        final int totalFrames = 26;
        final int[] frame = {0};
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (anchor.getWorld() == null) {
                task[0].cancel();
                return;
            }
            int f = frame[0]++;
            World world = anchor.getWorld();
            double pulse = 0.78D + 0.22D * Math.sin(f * 0.42D);

            double inner = maxRadius * 0.34D * pulse;
            double middle = maxRadius * 0.66D * (0.90D + 0.10D * Math.sin(f * 0.42D + 1.1D));
            double outer = maxRadius * pulse;
            drawDustRing(anchor, inner, 40, Color.fromRGB(130, 15, 25), 1.15F, 0.12D);
            drawDustRing(anchor, middle, 48, Color.fromRGB(255, 68, 25), 1.0F, 0.15D);
            drawDustRing(anchor, outer, 64, Color.fromRGB(255, 150, 65), 1.25F, 0.18D);
            drawRing(anchor, outer * 0.91D, Particle.SOUL_FIRE_FLAME, 34);

            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI * 2.0D / 12.0D + f * 0.035D;
                double x = Math.cos(angle) * outer;
                double z = Math.sin(angle) * outer;
                double height = 0.3D + 1.7D * Math.abs(Math.sin(f * 0.18D + i * 0.75D));
                Location pillar = anchor.clone().add(x, height, z);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, pillar, 2, 0.07D, height * 0.22D, 0.07D, 0.015D);
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.DUST_PLUME, pillar, 2, 0.12D, height * 0.30D, 0.12D, 0.015D);
                }
            }

            world.spawnParticle(Particle.SOUL, anchor.clone().add(0, 0.35D, 0),
                    4, inner * 0.28D, 0.12D, inner * 0.28D, 0.01D);
            if (f >= totalFrames) {
                drawDustRing(anchor, maxRadius, 72, Color.fromRGB(255, 210, 120), 1.6F, 0.18D);
                drawRing(anchor, maxRadius * 0.67D, Particle.FLAME, 58);
                world.spawnParticle(Particle.DUST_PLUME, anchor.clone().add(0, 0.65D, 0),
                        110, maxRadius * 0.43D, 0.85D, maxRadius * 0.43D, 0.05D);
                world.spawnParticle(Particle.FLASH, anchor.clone().add(0, 0.75D, 0), 1, 0, 0, 0, 0);
                world.playSound(anchor, "minecraft:entity.warden.sonic_boom", 0.9f, 0.65f);
                task[0].cancel();
                onImpact.run();
            }
        }, 0L, 2L);
    }

    private void drawDustRing(Location center, double radius, int points, Color color, float size, double yOffset) {
        if (center == null || center.getWorld() == null || radius <= 0.0D || points < 3) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = 2.0D * Math.PI * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, yOffset, Math.sin(angle) * radius);
            center.getWorld().spawnParticle(Particle.DUST, point, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
        }
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

        // Each phase has its own authored Blockbench pose, played before its telegraph and impact.
        String attackAnimation = switch (phase) {
            case 1 -> "false_sigil";
            case 2 -> "blood_pulse";
            case 3 -> "mirror_strike";
            default -> "nightfall";
        };
        if (!playModelAnimation(boss, attackAnimation)) {
            // Compatibility fallback for older ModelEngine builds or missing animation tracks.
            playModelAnimation(boss, "attack");
        }
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

        target.sendTitle(plugin.color("&5&lFALEŠNÁ KOŘIST"),
                plugin.color("&7Runy se nabíjejí. Uteč z označeného kruhu!"), 0, 30, 5);
        target.playSound(mark, "minecraft:block.amethyst_block.chime", 0.9f, 0.55f);
        animateFalseLoot(mark, radius, () -> {
            if (!isSameEncounter(encounterId)) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                if (!player.getWorld().getUID().equals(mark.getWorld().getUID())) continue;
                if (player.getLocation().distanceSquared(mark) > radius * radius) continue;
                if (damage > 0.0D) player.damage(damage, boss);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 25, 0, true, true, true
                ));
            }
        });
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

        mark.getWorld().playSound(mark, "minecraft:entity.warden.heartbeat", 0.8f, 0.55f);
        target.sendTitle(plugin.color("&4&lKRVAVÝ PULS"),
                plugin.color("&7Vlna se šíří od středu. Opusť rudou zónu!"), 0, 38, 5);
        animateBloodPulse(mark, radius, () -> {
            if (!isSameEncounter(encounterId)) return;
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
        });
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
            double angle = (2.0D * Math.PI * i / 3.0D)
                    + ThreadLocalRandom.current().nextDouble(-0.12D, 0.12D);
            markers.add(anchor.clone().add(Math.cos(angle) * markerDistance, 0.0D,
                    Math.sin(angle) * markerDistance));
        }

        anchor.getWorld().playSound(anchor, "minecraft:entity.enderman.stare", 0.9f, 0.45f);
        target.sendTitle(plugin.color("&d&lZRCADLOVÝ VÝPAD"),
                plugin.color("&7Tři portály. Jeden výpad. Sleduj značky a uhni!"), 0, 38, 5);
        animateMirrorStrike(anchor, markers, radius, impact -> {
            if (!isSameEncounter(encounterId)) return;
            if (impact.getBlock().isPassable()
                    && impact.clone().add(0, 1, 0).getBlock().isPassable()) {
                boss.teleport(impact);
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                if (!player.getWorld().getUID().equals(impact.getWorld().getUID())) continue;
                if (player.getLocation().distanceSquared(impact) > radius * radius) continue;
                if (damage > 0.0D) player.damage(damage, boss);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 30, 0, true, true, true
                ));
            }
        });
    }

    private void nightfall() {
        if (boss == null) return;
        Location origin = boss.getLocation().clone();
        UUID encounterId = bossUuid;
        double radius = Math.max(4.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-4-radius", 10.0D));
        double damage = Math.max(0.0D,
                plugin.getConfig().getDouble("bosses.vampire.encounter.abilities.phase-4-damage", 7.0D));

        origin.getWorld().playSound(origin, "minecraft:entity.warden.sonic_boom", 0.8f, 0.45f);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayPlayer(player)) continue;
            if (!player.getWorld().getUID().equals(origin.getWorld().getUID())) continue;
            if (player.getLocation().distanceSquared(origin) <= radius * radius * 1.5D) {
                player.sendMessage(plugin.color("&4ZATMĚNÍ &8» &7Vnitřní kruhy klamou. Vnější značí skutečný dosah!"));
            }
        }
        animateEclipse(origin, radius, () -> {
            if (!isSameEncounter(encounterId)) return;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)) continue;
                if (!player.getWorld().getUID().equals(origin.getWorld().getUID())) continue;
                if (player.getLocation().distanceSquared(origin) > radius * radius) continue;
                if (damage > 0.0D) player.damage(damage, boss);
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 60, 0, true, true, true
                ));
            }
        });
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
        testEncounter = false;
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

        for (UUID uuid : qualifiedParticipants) {
            Player participant = Bukkit.getPlayer(uuid);
            if (participant != null && plugin.getQuestManager().isActiveObjective(participant, "vampire-king-defeated")) {
                plugin.getQuestManager().recordAction(participant, "vampire-king-defeated");
            }
        }
        plugin.getDataManager().markVampireDefeated();
        plugin.getDataManager().save();
        stopEncounter();
    }

    /**
     * Plays a Blockbench animation on the ModelEngine model attached by the MythicMobs spawn skill.
     * Reflection keeps HalloweenCore loadable when ModelEngine is not installed; the finale readiness
     * gate still prevents production spawning until the model has been verified on a real client.
     */
    private boolean playModelAnimation(LivingEntity target, String animation) {
        if (target == null || !target.isValid() || target.isDead()) return false;
        if (animation == null || animation.isBlank()) return false;
        if (plugin.getServer().getPluginManager().getPlugin("ModelEngine") == null
                || !plugin.getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object modeledEntity = apiClass.getMethod("getModeledEntity", Entity.class).invoke(null, target);
            if (modeledEntity == null) return false;

            // Invoke methods through public API interfaces rather than implementation classes,
            // which may be package-private in a given ModelEngine build.
            Class<?> modeledEntityApi = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");
            Object modelResult = modeledEntityApi.getMethod("getModel", String.class)
                    .invoke(modeledEntity, "vampire_king");
            if (!(modelResult instanceof Optional<?> modelOptional) || modelOptional.isEmpty()) return false;

            Object activeModel = modelOptional.get();
            Class<?> activeModelApi = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
            Object handler = activeModelApi.getMethod("getAnimationHandler").invoke(activeModel);
            Class<?> handlerApi = Class.forName("com.ticxo.modelengine.api.animation.handler.AnimationHandler");
            Method playAnimation;
            try {
                playAnimation = handlerApi.getMethod("playAnimation",
                        String.class, double.class, double.class, double.class, boolean.class);
            } catch (NoSuchMethodException ex) {
                if (!modelAnimationWarningLogged) {
                    plugin.getLogger().warning("ModelEngine is present but its animation API does not expose the expected playAnimation(String,double,double,double,boolean) method.");
                    modelAnimationWarningLogged = true;
                }
                return false;
            }
            Object result = playAnimation.invoke(handler, animation, 0.08D, 0.12D, 1.0D, true);
            return result != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (!modelAnimationWarningLogged) {
                plugin.getLogger().warning("Could not trigger Vampire King model animation '" + animation
                        + "': " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                modelAnimationWarningLogged = true;
            }
            return false;
        }
    }

    private LivingEntity spawnTestFallback(Location location) {
        try {
            org.bukkit.entity.Entity entity = location.getWorld().spawnEntity(
                    location.clone().add(0.5D, 0.0D, 0.5D), org.bukkit.entity.EntityType.ZOMBIE);
            if (!(entity instanceof LivingEntity living)) {
                entity.remove();
                return null;
            }
            living.setCustomName(plugin.color("&4&lKRÁL UPÍRŮ &c[TEST BEZ MODELU]"));
            living.setCustomNameVisible(true);
            living.setGlowing(true);
            living.setPersistent(true);
            if (living instanceof org.bukkit.entity.Mob mob) {
                mob.setRemoveWhenFarAway(false);
                mob.setAware(true);
            }
            org.bukkit.attribute.AttributeInstance maxHealth =
                    living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(500.0D);
                living.setHealth(500.0D);
            }
            warnOnce("test-boss-fallback",
                    "MythicMobs boss definition unavailable; using a vanilla zombie as an admin-only boss test.");
            return living;
        } catch (RuntimeException ex) {
            warnOnce("test-boss-fallback-failed",
                    "Could not spawn fallback test boss: " + ex.getMessage());
            return null;
        }
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
            warnOnce("mythic-api-spawn-" + ex.getClass().getSimpleName(),
                    "MythicMobs API spawn failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
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
        if (testEncounter) {
            Bukkit.broadcastMessage(plugin.color("&a&lTEST BOSSE &8» &7Testovací Král upírů byl poražen. Progress, odměny a finále zůstaly beze změny."));
            finishNoReward();
            return;
        }
        if (!plugin.isEventEnabled()) {
            finishNoReward();
            return;
        }
        finishVictory(event.getEntity().getKiller());
    }
}
