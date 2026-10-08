package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HalloweenItemListener implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey relicKey;
    private final Map<UUID, Long> mapCooldowns = new HashMap<>();

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

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!plugin.isEventEnabled()) return;
        ItemStack item = event.getItem();
        if (!isRelic(item, "haunted-map-2026")) return;

        event.setCancelled(true);

        long cooldownMillis = Math.max(1L,
                plugin.getConfig().getLong("haunted-village.map-use-cooldown-seconds", 30L)) * 1000L;
        long now = System.currentTimeMillis();
        long lastUse = mapCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastUse < cooldownMillis) {
            long remaining = Math.max(1L, (cooldownMillis - (now - lastUse) + 999L) / 1000L);
            player.sendMessage(plugin.color("&5Mapa je stále rozpálená magií. &7Zkus to znovu za &e" + remaining + " s&7."));
            return;
        }

        mapCooldowns.put(player.getUniqueId(), now);

        if (!plugin.getConfig().getBoolean("haunted-village.enabled", false)) {
            player.sendMessage(plugin.color("&6HALLOWEEN &8» &7Mapa zatím nedokáže najít Haunted Village."));
            return;
        }

        String worldName = plugin.getConfig().getString("haunted-village.world", "");
        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            player.sendMessage(plugin.color("&cHaunted Village má v konfiguraci neplatný svět."));
            return;
        }

        double x = plugin.getConfig().getDouble("haunted-village.x", 0.0D);
        double y = plugin.getConfig().getDouble("haunted-village.y", 100.0D);
        double z = plugin.getConfig().getDouble("haunted-village.z", 0.0D);
        Location target = new Location(targetWorld, x, y, z);

        if (!player.getWorld().getUID().equals(targetWorld.getUID())) {
            player.sendTitle(
                    plugin.color("&6&lPROKLETÁ MAPA"),
                    plugin.color("&5Brána leží v jiném světě."),
                    10, 45, 15
            );
            player.sendMessage(plugin.color("&7Svět: &e" + targetWorld.getName()
                    + " &7• souřadnice: &e" + format(x) + " " + format(y) + " " + format(z)));
            return;
        }

        double distance = player.getLocation().distance(target);
        String direction = direction(player.getLocation(), target);

        player.sendTitle(
                plugin.color("&6&lPROKLETÁ MAPA"),
                plugin.color("&e" + direction + " &8• &f" + Math.round(distance) + " m"),
                10, 55, 15
        );
        player.sendMessage(plugin.color("&6HALLOWEEN &8» &fHaunted Village je &e"
                + Math.round(distance) + " bloků &fsměrem na &e" + direction + "&f."));
        player.sendMessage(plugin.color("&7Souřadnice: &e" + format(x) + " " + format(y) + " " + format(z)));
        player.playSound(player.getLocation(), "minecraft:block.end_portal_frame.fill", 0.8f, 0.75f);
        player.getWorld().spawnParticle(
                org.bukkit.Particle.PORTAL,
                player.getLocation().add(0, 1, 0),
                20,
                0.6D, 0.9D, 0.6D, 0.15D
        );
    }

    private boolean isRelic(ItemStack item, String id) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        String stored = item.getItemMeta().getPersistentDataContainer()
                .get(relicKey, PersistentDataType.STRING);
        return id.equalsIgnoreCase(stored);
    }

    private String direction(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        String horizontal = "";
        String vertical = "";

        if (Math.abs(dz) >= 4.0D) {
            vertical = dz < 0.0D ? "sever" : "jih";
        }
        if (Math.abs(dx) >= 4.0D) {
            horizontal = dx > 0.0D ? "východ" : "západ";
        }

        if (horizontal.isEmpty()) return vertical.isEmpty() ? "přímo" : vertical;
        if (vertical.isEmpty()) return horizontal;
        return vertical + " " + horizontal;
    }

    private String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.0f", value);
    }
}
