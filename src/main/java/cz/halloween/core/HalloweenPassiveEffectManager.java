package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HalloweenPassiveEffectManager {
    private final HalloweenCore plugin;
    private final NamespacedKey rewardKey;

    public HalloweenPassiveEffectManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.rewardKey = new NamespacedKey(plugin, "halloween_reward");
    }

    public void start() {
        long period = 40L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    private void tick() {
        if (!plugin.isEventEnabled()) return;
        if (!plugin.getConfig().getBoolean("rewards.passive-effects.enabled", true)) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
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

    private boolean isReward(ItemStack item, String rewardId) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        String stored = item.getItemMeta().getPersistentDataContainer()
                .get(rewardKey, PersistentDataType.STRING);
        return rewardId.equalsIgnoreCase(stored);
    }
}
