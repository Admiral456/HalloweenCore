package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Final-boss replacement built entirely from a vanilla Wither Skeleton.
 * It deliberately does not depend on MythicMobs, ModelEngine, or custom assets.
 */
public final class HalloweenVanillaBossManager implements Listener {
    private static final String BOSS_ID = "vanilla-vampire-king";

    private final HalloweenCore plugin;
    private final NamespacedKey bossKey;
    private final Map<UUID, Double> contributorDamage = new HashMap<>();
    private final Map<UUID, Integer> participationSeconds = new HashMap<>();

    private WitherSkeleton boss;
    private BossBar bossBar;
    private BukkitTask ticker;
    private int phase = 1;
    private int secondsUntilPulse;

    public HalloweenVanillaBossManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "vanilla_boss");
    }

    public boolean spawnBoss(Player administrator) {
        if (administrator == null || !administrator.isOnline()) return false;
        if (!plugin.getConfig().getBoolean("vanilla-boss.enabled", true)) {
            administrator.sendMessage(plugin.color("&cVanilla boss je vypnutý v konfiguraci."));
            return false;
        }
        if (!plugin.isEventEnabled()) {
            administrator.sendMessage(plugin.color("&cNejdřív zapni Halloween pomocí /halloween on."));
            return false;
        }
        if (!plugin.getDataManager().isFinaleUnlocked()) {
            administrator.sendMessage(plugin.color("&cServerové finále ještě není odemčené. Nejdřív musí být splněn globální Halloween cíl."));
            return false;
        }
        if (plugin.getDataManager().isVampireDefeated()) {
            administrator.sendMessage(plugin.color("&eKrál upírů už byl poražen. Finále je dokončeno."));
            return false;
        }
        if (!plugin.isEligibleGameplayPlayer(administrator)
                || !plugin.isEligibleGameplayWorld(administrator.getWorld())) {
            administrator.sendMessage(plugin.color("&cBosse lze vyvolat jen v povoleném světě a herním režimu."));
            return false;
        }
        if (adoptExistingBoss()) {
            administrator.sendMessage(plugin.color("&eKrál upírů už je aktivní. Použij /halloween boss status."));
            return false;
        }

        Location spawnLocation = findSafeSpawn(administrator);
        if (spawnLocation == null) {
            administrator.sendMessage(plugin.color("&cPřed tebou není bezpečné místo pro vyvolání bosse."));
            return false;
        }

        Entity created = spawnLocation.getWorld().spawnEntity(
                spawnLocation, org.bukkit.entity.EntityType.WITHER_SKELETON);
        if (!(created instanceof WitherSkeleton spawned)) {
            created.remove();
            administrator.sendMessage(plugin.color("&cNepodařilo se vytvořit vanilla boss entitu."));
            return false;
        }

        double maxHealth = boundedConfig("vanilla-boss.max-health", 240.0D, 100.0D, 500.0D);
        spawned.setMaxHealth(maxHealth);
        spawned.setHealth(maxHealth);
        spawned.setCustomName(plugin.color("&4&lKRÁL UPÍRŮ"));
        spawned.setCustomNameVisible(true);
        spawned.setGlowing(true);
        spawned.setRemoveWhenFarAway(false);
        spawned.setPersistent(true);
        spawned.setCanPickupItems(false);
        spawned.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, BOSS_ID);
        EntityEquipment equipment = spawned.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            equipment.setHelmetDropChance(0.0F);
            equipment.setItemInMainHandDropChance(0.0F);
        }
        spawned.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, 0, true, false, false));
        spawned.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, 1, true, false, false));
        activate(spawned);

        spawned.getWorld().playSound(spawned.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.2F, 0.55F);
        spawned.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                spawned.getLocation().add(0, 1.0D, 0), 60, 0.7D, 1.0D, 0.7D, 0.035D);
        for (Player player : spawned.getWorld().getPlayers()) {
            if (plugin.isEligibleGameplayPlayer(player)
                    && plugin.isEligibleGameplayWorld(player.getWorld())) {
                player.sendMessage(plugin.color("&4&lHALLOWEEN &8» &cKrál upírů se probudil. Uhýbej před jeho krvavými výboji!"));
            }
        }
        return true;
    }

    public void sendStatus(org.bukkit.command.CommandSender sender) {
        if (!plugin.getConfig().getBoolean("vanilla-boss.enabled", true)) {
            sender.sendMessage(plugin.color("&7Vanilla boss: &cVYPNUTÝ V KONFIGURACI"));
            return;
        }
        if (plugin.getDataManager().isVampireDefeated()) {
            sender.sendMessage(plugin.color("&7Král upírů: &aPORAŽENÝ — finále dokončeno"));
            return;
        }
        if (!adoptExistingBoss()) {
            sender.sendMessage(plugin.color("&7Král upírů: &eŽádný aktivní boss."));
            sender.sendMessage(plugin.color("&7Administrátor může použít &f/halloween boss spawn&7."));
            return;
        }
        double health = Math.max(0.0D, boss.getHealth());
        double maxHealth = Math.max(1.0D, boss.getMaxHealth());
        sender.sendMessage(plugin.color("&4&lKRÁL UPÍRŮ"));
        sender.sendMessage(plugin.color("&7Zdraví: &c" + Math.round(health) + "&7/&c" + Math.round(maxHealth)));
        sender.sendMessage(plugin.color("&7Fáze: &e" + phase + "&7/3"));
        sender.sendMessage(plugin.color("&7Další krvavý výboj přibližně za &e" + secondsUntilPulse + " s&7."));
    }

    public boolean stopBoss(org.bukkit.command.CommandSender sender) {
        if (!adoptExistingBoss()) {
            if (sender != null) sender.sendMessage(plugin.color("&7Žádný aktivní Král upírů nebyl nalezen."));
            return false;
        }
        cleanup(true);
        if (sender != null) sender.sendMessage(plugin.color("&aKrál upírů byl bezpečně odstraněn."));
        return true;
    }

    public void shutdown() {
        cleanup(true);
        // Also remove tagged bosses loaded from disk after an unclean server stop.
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isBoss(entity)) entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamage(EntityDamageByEntityEvent event) {
        if (!isBoss(event.getEntity())) return;
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("vanilla-boss.enabled", true)) return;

        Player attacker = null;
        Entity damager = event.getDamager();
        if (damager instanceof Player player) attacker = player;
        else if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) attacker = player;

        if (attacker == null || !plugin.isEligibleGameplayPlayer(attacker)
                || !plugin.isEligibleGameplayWorld(attacker.getWorld())) return;
        contributorDamage.merge(attacker.getUniqueId(),
                Math.max(0.0D, event.getFinalDamage()), Double::sum);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        if (!isBoss(event.getEntity())) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        boolean eligibleVictory = plugin.isEventEnabled()
                && plugin.getConfig().getBoolean("vanilla-boss.enabled", true);
        if (eligibleVictory) {
            rewardContributors(event.getEntity());
            plugin.getDataManager().markVampireDefeated();
            plugin.getDataManager().save();
        }
        if (eligibleVictory) {
            for (Player player : event.getEntity().getWorld().getPlayers()) {
                if (!plugin.isEligibleGameplayPlayer(player)
                        || !plugin.isEligibleGameplayWorld(player.getWorld())) continue;
                player.sendMessage(plugin.color("&6&lHALLOWEEN &8» &aKrál upírů byl poražen!"));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
            }
        }
        cleanup(false);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!isBoss(entity) || !(entity instanceof WitherSkeleton loadedBoss)) continue;
            if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("vanilla-boss.enabled", true)) {
                loadedBoss.remove();
                continue;
            }
            if (boss != null && boss.isValid() && !boss.getUniqueId().equals(loadedBoss.getUniqueId())) {
                loadedBoss.remove();
                continue;
            }
            if (boss == null || !boss.isValid()) activate(loadedBoss);
        }
    }

    private Location findSafeSpawn(Player player) {
        World world = player.getWorld();
        Vector direction = player.getLocation().getDirection().clone().setY(0.0D);
        if (direction.lengthSquared() < 0.01D) direction = new Vector(0.0D, 0.0D, 1.0D);
        direction.normalize();

        Location origin = player.getLocation().clone().add(direction.multiply(4.0D));
        for (int verticalOffset : new int[]{0, 1, -1, 2, -2}) {
            Location candidate = origin.clone().add(0.0D, verticalOffset, 0.0D);
            candidate.setX(Math.floor(candidate.getX()) + 0.5D);
            candidate.setZ(Math.floor(candidate.getZ()) + 0.5D);
            Block feet = candidate.getBlock();
            Block head = feet.getRelative(org.bukkit.block.BlockFace.UP);
            Block aboveHead = head.getRelative(org.bukkit.block.BlockFace.UP);
            Block ground = feet.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (!world.getWorldBorder().isInside(candidate)
                    || !feet.isPassable() || !head.isPassable() || !aboveHead.isPassable()
                    || feet.isLiquid() || head.isLiquid() || aboveHead.isLiquid()
                    || ground.isLiquid() || !ground.getType().isSolid()) continue;
            return candidate;
        }
        return null;
    }

    private void activate(WitherSkeleton activeBoss) {
        cleanup(false);
        boss = activeBoss;
        contributorDamage.clear();
        participationSeconds.clear();
        phase = 1;
        secondsUntilPulse = (int) boundedConfig(
                "vanilla-boss.pulse.interval-seconds", 18.0D, 8.0D, 60.0D);
        bossBar = Bukkit.createBossBar(plugin.color("&4&lKRÁL UPÍRŮ"),
                BarColor.RED, BarStyle.SEGMENTED_20);
        bossBar.setVisible(true);
        updateBossBar();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (boss == null || !boss.isValid() || boss.isDead()) {
            cleanup(false);
            return;
        }
        if (!plugin.isEventEnabled() || !plugin.getConfig().getBoolean("vanilla-boss.enabled", true)) {
            cleanup(true);
            return;
        }

        updateBossBar();
        recordPresenceSeconds();
        updatePhase();

        secondsUntilPulse--;
        if (secondsUntilPulse <= 0) {
            pulse();
            secondsUntilPulse = (int) boundedConfig(
                    "vanilla-boss.pulse.interval-seconds", 18.0D, 8.0D, 60.0D);
        }
    }

    private void recordPresenceSeconds() {
        if (boss == null || !boss.isValid()) return;
        double trackingRadius = boundedConfig("vanilla-boss.tracking-radius-blocks", 96.0D, 16.0D, 192.0D);
        double radiusSquared = trackingRadius * trackingRadius;
        for (Player player : boss.getWorld().getPlayers()) {
            if (plugin.isEligibleGameplayPlayer(player)
                    && plugin.isEligibleGameplayWorld(player.getWorld())
                    && player.getLocation().distanceSquared(boss.getLocation()) <= radiusSquared) {
                participationSeconds.merge(player.getUniqueId(), 1, (previous, increment) ->
                        Math.min(3600, previous + increment));
            }
        }
    }

    private boolean qualifiesForReward(UUID playerId, double maxHealth) {
        int minimumSeconds = Math.max(1, Math.min(3600,
                plugin.getConfig().getInt("vanilla-boss.rewards.minimum-participation-seconds", 120)));
        double damagePercent = plugin.getConfig().getDouble(
                "vanilla-boss.rewards.minimum-damage-percent", 0.025D);
        if (!Double.isFinite(damagePercent)) damagePercent = 0.025D;
        damagePercent = Math.max(0.0D, Math.min(1.0D, damagePercent));
        double dealt = contributorDamage.getOrDefault(playerId, 0.0D);
        return participationSeconds.getOrDefault(playerId, 0) >= minimumSeconds
                && maxHealth > 0.0D && dealt / maxHealth >= damagePercent;
    }

    public boolean isActive() {
        return adoptExistingBoss() && boss != null && boss.isValid() && !boss.isDead();
    }

    public int getPhase() {
        return isActive() ? phase : 0;
    }

    public double getHealthPercent() {
        if (!adoptExistingBoss() || boss == null || !boss.isValid()) return 0.0D;
        return Math.max(0.0D, Math.min(100.0D, boss.getHealth()
                / Math.max(1.0D, boss.getMaxHealth()) * 100.0D));
    }

    public int getParticipantCount() {
        if (!adoptExistingBoss() || boss == null || !boss.isValid()) return 0;
        double maxHealth = boss.getMaxHealth();
        return (int) contributorDamage.keySet().stream()
                .filter(id -> qualifiesForReward(id, maxHealth))
                .count();
    }

    private void updateBossBar() {
        if (boss == null || bossBar == null || !boss.isValid()) return;
        double maxHealth = Math.max(1.0D, boss.getMaxHealth());
        bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, boss.getHealth() / maxHealth)));
        bossBar.setTitle(plugin.color("&4&lKRÁL UPÍRŮ &8[&c"
                + Math.round(boss.getHealth()) + "&8/&c" + Math.round(maxHealth) + " HP&8]"));

        double trackingRadius = boundedConfig("vanilla-boss.tracking-radius-blocks", 96.0D, 16.0D, 192.0D);
        double radiusSquared = trackingRadius * trackingRadius;
        List<Player> currentViewers = new ArrayList<>();
        for (Player player : boss.getWorld().getPlayers()) {
            if (plugin.isEligibleGameplayPlayer(player)
                    && plugin.isEligibleGameplayWorld(player.getWorld())
                    && player.getLocation().distanceSquared(boss.getLocation()) <= radiusSquared) {
                currentViewers.add(player);
                if (!bossBar.getPlayers().contains(player)) bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
        for (Player viewer : new ArrayList<>(bossBar.getPlayers())) {
            if (!currentViewers.contains(viewer)) bossBar.removePlayer(viewer);
        }
    }

    private void updatePhase() {
        if (boss == null || !boss.isValid()) return;
        double ratio = boss.getHealth() / Math.max(1.0D, boss.getMaxHealth());
        int newPhase = ratio <= 0.33D ? 3 : ratio <= 0.66D ? 2 : 1;
        if (newPhase == phase) return;

        phase = newPhase;
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                Integer.MAX_VALUE, phase == 3 ? 1 : 0, true, false, false));
        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, phase == 3 ? 2 : 1, true, false, false));
        if (bossBar != null) bossBar.setColor(phase == 3 ? BarColor.RED : BarColor.PURPLE);
        boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                boss.getLocation().add(0.0D, 1.0D, 0.0D), 55, 0.7D, 1.0D, 0.7D, 0.04D);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SKELETON_HURT, 1.1F, 0.5F);
        String phaseText = phase == 2
                ? "&5Druhá fáze! Král upírů zrychluje."
                : "&4Poslední fáze! Král upírů udeří silněji.";
        for (Player viewer : bossBar == null ? List.<Player>of() : bossBar.getPlayers()) {
            viewer.sendMessage(plugin.color("&4&lHALLOWEEN &8» " + phaseText));
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_WITHER_SKELETON_HURT, 0.7F, 0.6F);
        }
    }

    private void pulse() {
        if (boss == null || !boss.isValid()) return;
        Location bossLocation = boss.getLocation().clone();
        double radius = boundedConfig("vanilla-boss.pulse.radius-blocks", 8.0D, 3.0D, 20.0D);
        double radiusSquared = radius * radius;
        int windupTicks = (int) boundedConfig("vanilla-boss.pulse.windup-ticks", 30.0D, 10.0D, 80.0D);
        for (Player target : bossLocation.getWorld().getPlayers()) {
            if (!plugin.isEligibleGameplayPlayer(target)
                    || !plugin.isEligibleGameplayWorld(target.getWorld())
                    || target.getLocation().distanceSquared(bossLocation) > radiusSquared) continue;

            Location markedSpot = target.getLocation().clone();
            target.getWorld().spawnParticle(Particle.SOUL, markedSpot, 22, 0.35D, 0.1D, 0.35D, 0.025D);
            target.getWorld().playSound(markedSpot, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.65F, 0.65F);
            target.sendMessage(plugin.color("&5Král upírů označil místo pod tebou — uhni, než se krvavý kruh vyšlehne!"));

            WitherSkeleton sourceBoss = boss;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (boss != sourceBoss || !sourceBoss.isValid() || sourceBoss.isDead()
                        || !target.isOnline() || target.isDead()
                        || !target.getWorld().getUID().equals(markedSpot.getWorld().getUID())
                        || !plugin.isEligibleGameplayPlayer(target)
                        || !plugin.isEligibleGameplayWorld(target.getWorld())) return;

                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, markedSpot,
                        32, 0.55D, 0.15D, 0.55D, 0.04D);
                target.getWorld().playSound(markedSpot, Sound.ENTITY_WITHER_SKELETON_HURT, 0.8F, 0.5F);
                double dodgeRadius = boundedConfig("vanilla-boss.pulse.dodge-radius-blocks", 2.0D, 0.75D, 5.0D);
                if (target.getLocation().distanceSquared(markedSpot) <= dodgeRadius * dodgeRadius) {
                    double damage = boundedConfig("vanilla-boss.pulse.damage", 4.0D, 0.0D, 10.0D);
                    if (damage > 0.0D) target.damage(damage, sourceBoss);
                    int witherTicks = Math.max(20, Math.min(200,
                            plugin.getConfig().getInt("vanilla-boss.pulse.wither-ticks", 60)));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER,
                            witherTicks, 0, true, true, false));
                    target.sendMessage(plugin.color("&4Krvavý výboj tě zasáhl!"));
                } else {
                    target.sendMessage(plugin.color("&aKrvavému výboji ses úspěšně vyhnul."));
                }
            }, windupTicks);
        }

        bossLocation.getWorld().spawnParticle(Particle.ASH,
                bossLocation.clone().add(0, 1.0D, 0), 55, radius / 2.5D, 0.8D, radius / 2.5D, 0.025D);
    }

    private void rewardContributors(LivingEntity defeatedBoss) {
        if (contributorDamage.isEmpty()) return;
        double maxHealth = Math.max(1.0D, defeatedBoss.getMaxHealth());
        List<Map.Entry<UUID, Double>> qualified = contributorDamage.entrySet().stream()
                .filter(entry -> qualifiesForReward(entry.getKey(), maxHealth))
                .toList();
        if (qualified.isEmpty()) {
            plugin.getLogger().info("Vanilla vampire boss was defeated without a qualified reward participant.");
            return;
        }
        UUID topContributor = qualified.stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
        long participationReward = Math.max(0L, Math.min(5000L,
                plugin.getConfig().getLong("vanilla-boss.rewards.participation-fragments", 300L)));
        long topBonus = Math.max(0L, Math.min(5000L,
                plugin.getConfig().getLong("vanilla-boss.rewards.top-contributor-bonus-fragments", 250L)));
        for (Map.Entry<UUID, Double> entry : qualified) {
            if (entry.getValue() <= 0.0D) continue;
            UUID contributorId = entry.getKey();
            if (participationReward > 0L) {
                plugin.getService().addFragments(contributorId, participationReward, "vanilla-boss-participation");
            }
            if (contributorId.equals(topContributor) && topBonus > 0L) {
                plugin.getService().addFragments(contributorId, topBonus, "vanilla-boss-top-contributor");
            }
            Player player = Bukkit.getPlayer(contributorId);
            if (player != null && player.isOnline()) {
                player.sendMessage(plugin.color("&aOdměna za účast na souboji s Králem upírů: &e+"
                        + participationReward + " fragmentů."));
                if (contributorId.equals(topContributor) && topBonus > 0L) {
                    player.sendMessage(plugin.color("&6Nejvyšší příspěvek do souboje: &e+" + topBonus + " fragmentů."));
                }
            }
        }
        plugin.getDataManager().save();
    }

    private boolean adoptExistingBoss() {
        if (boss != null && boss.isValid() && !boss.isDead()) return true;
        boss = null;
        List<WitherSkeleton> found = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isBoss(entity) && entity instanceof WitherSkeleton witherSkeleton) found.add(witherSkeleton);
            }
        }
        if (found.isEmpty()) return false;
        activate(found.get(0));
        for (int i = 1; i < found.size(); i++) found.get(i).remove();
        return true;
    }

    private boolean isBoss(Entity entity) {
        return entity != null
                && BOSS_ID.equals(entity.getPersistentDataContainer().get(bossKey, PersistentDataType.STRING));
    }

    private void cleanup(boolean removeEntity) {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }
        WitherSkeleton oldBoss = boss;
        boss = null;
        phase = 1;
        secondsUntilPulse = 0;
        contributorDamage.clear();
        if (removeEntity && oldBoss != null && oldBoss.isValid() && !oldBoss.isDead()) {
            oldBoss.remove();
        }
    }

    private double boundedConfig(String path, double fallback, double min, double max) {
        double value = plugin.getConfig().getDouble(path, fallback);
        if (!Double.isFinite(value)) value = fallback;
        return Math.max(min, Math.min(max, value));
    }
}
