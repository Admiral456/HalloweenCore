package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HalloweenPassiveEffectManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey rewardKey;
    private final NamespacedKey talismanHealthKey;
    private final NamespacedKey talismanHealCooldownKey;
    private static final long TALISMAN_HEAL_COOLDOWN_MS = 60_000L;

    public HalloweenPassiveEffectManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.rewardKey = new NamespacedKey(plugin, "halloween_reward");
        this.talismanHealthKey = new NamespacedKey(plugin, "cursed_talisman_bonus_health");
        this.talismanHealCooldownKey = new NamespacedKey(plugin, "cursed_talisman_heal_cooldown_until");
    }

    public void start() {
        long period = 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeTalismanHealthModifier(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Do not leave a health modifier on the player data when the talisman
        // might no longer be held after they reconnect.
        removeTalismanHealthModifier(event.getPlayer());
    }

    private void tick() {
        boolean passiveEnabled = plugin.isEventEnabled()
                && plugin.getConfig().getBoolean("rewards.passive-effects.enabled", true);

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean gameplayEligible = passiveEnabled
                    && plugin.isEligibleGameplayPlayer(player)
                    && plugin.isEligibleGameplayWorld(player.getWorld());

            // The talisman works while held in either hand. Always reconcile the
            // modifier, even when Halloween is switched off, so it cannot get stuck.
            boolean talismanActive = gameplayEligible
                    && (isReward(player.getInventory().getItemInMainHand(), "cursed-talisman")
                    || isReward(player.getInventory().getItemInOffHand(), "cursed-talisman"));
            updateTalismanHealth(player, talismanActive);

            if (!gameplayEligible) continue;
            ItemStack helmet = player.getInventory().getHelmet();
            if (isReward(helmet, "hunter-mask")) {
                int duration = Math.max(2, plugin.getConfig().getInt("rewards.passive-effects.hunter-mask-night-vision-seconds", 5));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        duration * 20,
                        0,
                        true,
                        false,
                        true
                ));
            }

            if (!plugin.getConfig().getBoolean("gear-effects.enabled", true)) continue;
            ItemStack chestplate = player.getInventory().getChestplate();
            ItemStack leggings = player.getInventory().getLeggings();
            ItemStack boots = player.getInventory().getBoots();
            int nightVisionSeconds = Math.max(2, plugin.getConfig().getInt("gear-effects.armor.helmet-night-vision-seconds", 4));
            int fireResistanceSeconds = Math.max(2, plugin.getConfig().getInt("gear-effects.armor.chest-fire-resistance-seconds", 3));
            int speedSeconds = Math.max(2, plugin.getConfig().getInt("gear-effects.armor.leggings-speed-seconds", 3));
            int jumpSeconds = Math.max(2, plugin.getConfig().getInt("gear-effects.armor.boots-jump-boost-seconds", 3));

            if (isReward(helmet, "crimson-warden-helmet")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        nightVisionSeconds * 20, 0, true, false, true));
            }
            if (isReward(chestplate, "crimson-warden-chestplate")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                        fireResistanceSeconds * 20, 0, true, false, true));
            }
            if (isReward(leggings, "crimson-warden-leggings")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        speedSeconds * 20, 0, true, false, true));
            }
            if (isReward(boots, "crimson-warden-boots")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,
                        jumpSeconds * 20, 0, true, false, true));
            }

            boolean fullSet = isReward(helmet, "crimson-warden-helmet")
                    && isReward(chestplate, "crimson-warden-chestplate")
                    && isReward(leggings, "crimson-warden-leggings")
                    && isReward(boots, "crimson-warden-boots");
            int auraInterval = Math.max(20, plugin.getConfig().getInt("gear-effects.armor.set-aura-interval-ticks", 40));
            if (fullSet && player.getTicksLived() % auraInterval < 20) {
                player.getWorld().spawnParticle(org.bukkit.Particle.DUST,
                        player.getLocation().add(0, 0.9D, 0), 2, 0.35D, 0.45D, 0.35D, 0.0D,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(190, 25, 52), 1.0F));
                player.getWorld().spawnParticle(org.bukkit.Particle.SOUL,
                        player.getLocation().add(0, 0.8D, 0), 1, 0.25D, 0.3D, 0.25D, 0.01D);
            }
        }
    }

    private void updateTalismanHealth(Player player, boolean active) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double bonusHealth = Math.max(0.0D,
                plugin.getConfig().getDouble("rewards.shop.cursed-talisman.bonus-health", 20.0D));
        AttributeModifier existing = maxHealth.getModifiers().stream()
                .filter(modifier -> modifier.getKey().equals(talismanHealthKey))
                .findFirst()
                .orElse(null);

        if (existing != null && (!active || Math.abs(existing.getAmount() - bonusHealth) > 0.001D)) {
            maxHealth.removeModifier(existing);
            existing = null;
            // If the maximum dropped, clamp current health to the new maximum.
            player.setHealth(Math.min(player.getHealth(), Math.max(1.0D, maxHealth.getValue())));
        }

        if (active && bonusHealth > 0.0D && existing == null) {
            maxHealth.addModifier(new AttributeModifier(
                    talismanHealthKey, bonusHealth, AttributeModifier.Operation.ADD_NUMBER));

            // Fill the newly granted hearts on a genuine activation. A persistent
            // cooldown prevents rapid re-equipping from becoming unlimited healing.
            long now = System.currentTimeMillis();
            var playerData = player.getPersistentDataContainer();
            long nextHealAt = playerData.getOrDefault(talismanHealCooldownKey, PersistentDataType.LONG, 0L);
            if (now >= nextHealAt) {
                double currentAfterBonus = Math.min(maxHealth.getValue(), player.getHealth() + bonusHealth);
                player.setHealth(Math.max(1.0D, currentAfterBonus));
                playerData.set(talismanHealCooldownKey, PersistentDataType.LONG,
                        now + TALISMAN_HEAL_COOLDOWN_MS);
            }
        }
    }

    private void removeTalismanHealthModifier(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;
        var modifiers = maxHealth.getModifiers().stream()
                .filter(modifier -> modifier.getKey().equals(talismanHealthKey))
                .toList();
        for (AttributeModifier modifier : modifiers) {
            maxHealth.removeModifier(modifier);
        }
        player.setHealth(Math.min(player.getHealth(), Math.max(1.0D, maxHealth.getValue())));
    }

    private boolean isReward(ItemStack item, String rewardId) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        String stored = item.getItemMeta().getPersistentDataContainer()
                .get(rewardKey, PersistentDataType.STRING);
        return rewardId.equalsIgnoreCase(stored);
    }
}
