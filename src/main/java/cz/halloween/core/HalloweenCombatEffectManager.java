package cz.halloween.core;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight combat effects for the Crimson Warden set.
 * All effects are tied to items granted by HalloweenCore, use per-player
 * cooldowns, avoid block damage, and do not require MythicMobs/ModelEngine.
 */
public final class HalloweenCombatEffectManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey rewardKey;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public HalloweenCombatEffectManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.rewardKey = new NamespacedKey(plugin, "halloween_reward");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void protectCrimsonWardenWearer(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !plugin.isEventEnabled()) return;
        if (!plugin.isEligibleGameplayPlayer(player) || !plugin.isEligibleGameplayWorld(player.getWorld())) return;

        ItemStack chest = player.getInventory().getChestplate();
        boolean chestplate = isReward(chest, "crimson-warden-chestplate");
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (chestplate && (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                || cause == EntityDamageEvent.DamageCause.CAMPFIRE)) {
            event.setDamage(event.getDamage() * 0.65D);
            player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1.0D, 0),
                    4, 0.35D, 0.35D, 0.35D, 0.01D);
        }

        if (!hasFullSet(player) || event.getFinalDamage() <= 0.0D) return;
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute == null) return;
        double maxHealth = maxHealthAttribute.getValue();
        if (player.getHealth() - event.getFinalDamage() > maxHealth * 0.35D) return;
        if (!ready(player.getUniqueId(), "warden-aegis", 30_000L)) return;

        // Brief emergency ward, triggered once per 30 seconds when a hit would
        // take the wearer below 35% health. This is not a permanent resistance buff.
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, true, true, true));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1.0D, 0), 24, 0.65D, 0.85D, 0.65D, 0.08D);
        player.getWorld().spawnParticle(Particle.DUST,
                player.getLocation().add(0, 1.0D, 0), 18, 0.5D, 0.7D, 0.5D, 0.0D,
                new Particle.DustOptions(Color.fromRGB(205, 36, 57), 1.1F));
        player.sendActionBar(plugin.color("&4✦ &cKrvavá záštita &8• &fAbsorpce aktivována"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void triggerWeaponAndShieldEffects(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            reflectShieldEffect(event);
            return;
        }
        if (!plugin.isEventEnabled() || event.getFinalDamage() <= 0.0D) return;
        if (!plugin.isEligibleGameplayPlayer(attacker) || !plugin.isEligibleGameplayWorld(attacker.getWorld())) return;
        if (!(event.getEntity() instanceof LivingEntity target) || target.isDead()) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (isReward(weapon, "crimson-warden-sword") && ready(attacker.getUniqueId(), "blood-rend", 5_000L)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 0, true, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, true, true));
            target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                    target.getLocation().add(0, 0.85D, 0), 12, 0.35D, 0.4D, 0.35D, 0.05D);
            target.getWorld().spawnParticle(Particle.DUST,
                    target.getLocation().add(0, 1.0D, 0), 8, 0.3D, 0.35D, 0.3D, 0.0D,
                    new Particle.DustOptions(Color.fromRGB(180, 20, 42), 1.2F));
            attacker.sendActionBar(plugin.color("&4✦ &cKrvavá rána"));
        } else if (isReward(weapon, "crimson-warden-axe") && ready(attacker.getUniqueId(), "warden-break", 6_000L)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
            target.getWorld().spawnParticle(Particle.CRIT,
                    target.getLocation().add(0, 1.0D, 0), 18, 0.4D, 0.55D, 0.4D, 0.15D);
            target.getWorld().spawnParticle(Particle.SOUL,
                    target.getLocation().add(0, 0.2D, 0), 6, 0.35D, 0.15D, 0.35D, 0.02D);
            attacker.sendActionBar(plugin.color("&4✦ &6Rozlomení obrany"));
        } else if (isReward(weapon, "crimson-warden-pickaxe") && ready(attacker.getUniqueId(), "grave-piercer", 6_000L)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1, true, true, true));
            target.getWorld().spawnParticle(Particle.END_ROD,
                    target.getLocation().add(0, 0.9D, 0), 10, 0.3D, 0.4D, 0.3D, 0.015D);
            attacker.sendActionBar(plugin.color("&4✦ &bPropíchnutí hrobu"));
        } else if (isReward(weapon, "crimson-warden-shovel") && ready(attacker.getUniqueId(), "grave-shock", 8_000L)) {
            target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    target.getLocation(), 18, 1.1D, 0.2D, 1.1D, 0.02D);
            for (Entity nearby : target.getNearbyEntities(2.5D, 1.0D, 2.5D)) {
                if (!(nearby instanceof LivingEntity splash) || splash == attacker || splash == target || splash.isDead()) continue;
                splash.damage(2.0D, attacker);
                splash.getWorld().spawnParticle(Particle.CRIT,
                        splash.getLocation().add(0, 0.75D, 0), 5, 0.25D, 0.25D, 0.25D, 0.04D);
            }
            attacker.sendActionBar(plugin.color("&4✦ &8Rázová vlna hrobníka"));
        } else if (isReward(weapon, "crimson-warden-hoe") && ready(attacker.getUniqueId(), "soul-harvest", 5_000L)) {
            AttributeInstance maxHealthAttribute = attacker.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttribute != null) {
                attacker.setHealth(Math.min(maxHealthAttribute.getValue(), attacker.getHealth() + 2.0D));
                attacker.getWorld().spawnParticle(Particle.HEART,
                        attacker.getLocation().add(0, 1.1D, 0), 4, 0.35D, 0.35D, 0.35D, 0.01D);
                attacker.getWorld().spawnParticle(Particle.SOUL,
                        attacker.getLocation().add(0, 0.6D, 0), 8, 0.35D, 0.35D, 0.35D, 0.03D);
                attacker.sendActionBar(plugin.color("&4✦ &dSklizeň duší &8• &c+1 srdce"));
            }
        }
        reflectShieldEffect(event);
    }

    private void reflectShieldEffect(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player defender) || !(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!plugin.isEventEnabled() || !defender.isBlocking()) return;
        if (!plugin.isEligibleGameplayPlayer(defender) || !plugin.isEligibleGameplayWorld(defender.getWorld())) return;
        ItemStack main = defender.getInventory().getItemInMainHand();
        ItemStack off = defender.getInventory().getItemInOffHand();
        if (!isReward(main, "crimson-warden-shield") && !isReward(off, "crimson-warden-shield")) return;
        if (!ready(defender.getUniqueId(), "warden-shield", 4_000L)) return;

        Vector push = attacker.getLocation().toVector().subtract(defender.getLocation().toVector());
        push.setY(0.0D);
        if (push.lengthSquared() > 0.001D) attacker.setVelocity(push.normalize().multiply(0.5D).setY(0.18D));
        defender.getWorld().spawnParticle(Particle.BLOCK,
                defender.getLocation().add(0, 1.0D, 0), 16, 0.45D, 0.5D, 0.45D, 0.0D,
                Material.POLISHED_BLACKSTONE_BRICKS.createBlockData());
        defender.getWorld().spawnParticle(Particle.SOUL,
                defender.getLocation().add(0, 1.0D, 0), 8, 0.35D, 0.45D, 0.35D, 0.03D);
        defender.sendActionBar(plugin.color("&4✦ &8Štít Krvavého strážce &7• odražení"));
    }

    private boolean hasFullSet(Player player) {
        return isReward(player.getInventory().getHelmet(), "crimson-warden-helmet")
                && isReward(player.getInventory().getChestplate(), "crimson-warden-chestplate")
                && isReward(player.getInventory().getLeggings(), "crimson-warden-leggings")
                && isReward(player.getInventory().getBoots(), "crimson-warden-boots");
    }

    private boolean ready(UUID uuid, String effect, long cooldownMillis) {
        long now = System.currentTimeMillis();
        String key = uuid + ":" + effect;
        long last = cooldowns.getOrDefault(key, 0L);
        if (now - last < cooldownMillis) return false;
        cooldowns.put(key, now);
        if (cooldowns.size() > 4096) cooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 120_000L);
        return true;
    }

    private boolean isReward(ItemStack item, String rewardId) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        String stored = item.getItemMeta().getPersistentDataContainer().get(rewardKey, PersistentDataType.STRING);
        return rewardId.equalsIgnoreCase(stored);
    }
}
