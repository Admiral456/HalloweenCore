package cz.halloween.core;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class HalloweenItemListener implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey relicKey;

    public HalloweenItemListener(HalloweenCore plugin) {
        this.plugin = plugin;
        this.relicKey = new NamespacedKey(plugin, "halloween_relic");
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getItemMeta() == null) return;

        String relicId = item.getItemMeta().getPersistentDataContainer()
                .get(relicKey, PersistentDataType.STRING);
        if (!"cursed-candy-2026".equalsIgnoreCase(relicId)) return;

        Player player = event.getPlayer();
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 45, 0, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 12, 0, true, true, true));
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &eProkleté cukroví &7se ti zakouslo do duše..."));
    }
}
