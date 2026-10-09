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

    public HalloweenPassiveEffectManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.rewardKey = new NamespacedKey(plugin, "halloween_reward");
        this.talismanHealthKey = new NamespacedKey(plugin, "cursed_talisman_bonus_health");
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
            if (!isReward(helmet, "hunter-mask")) continue;

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
