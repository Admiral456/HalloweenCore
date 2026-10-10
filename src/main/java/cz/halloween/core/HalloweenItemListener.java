package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
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
    private final NamespacedKey rewardKey;
    private final Map<UUID, Long> mapCooldowns = new HashMap<>();
    private final Map<UUID, Long> shieldCooldowns = new HashMap<>();

    public HalloweenItemListener(HalloweenCore plugin) {
        this.plugin = plugin;
        this.relicKey = new NamespacedKey(plugin, "halloween_relic");
        this.rewardKey = new NamespacedKey(plugin, "halloween_reward");
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
        ItemStack item = event.getItem();
        if (plugin.isEventEnabled() && player.isSneaking() && isCrimsonWardenShield(item)) {
            event.setCancelled(true);
            activateCrimsonWardenShield(player);
            return;
        }
        if (!plugin.isEventEnabled()) return;
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

    private boolean isCrimsonWardenShield(ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.SHIELD || item.getItemMeta() == null) return false;
        String reward = item.getItemMeta().getPersistentDataContainer().get(rewardKey, PersistentDataType.STRING);
        return "crimson-warden-shield".equalsIgnoreCase(reward);
    }

    private void activateCrimsonWardenShield(Player player) {
        long cooldownMillis = Math.max(10L,
                plugin.getConfig().getLong("rewards.shop.crimson-warden-shield.ability-cooldown-seconds", 45L)) * 1000L;
        long now = System.currentTimeMillis();
        long last = shieldCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMillis) {
            long remaining = Math.max(1L, (cooldownMillis - (now - last) + 999L) / 1000L);
            player.sendMessage(plugin.color("&4Krvavá bariéra &8» &7Síla se obnoví za &c" + remaining + " s&7."));
            return;
        }
        shieldCooldowns.put(player.getUniqueId(), now);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 160, 1, true, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, true, true));
        var world = player.getWorld();
        Location center = player.getLocation();
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0.0D, 1.0D, 0.0D), 55, 2.4D, 1.0D, 2.4D, 0.035D);
        world.spawnParticle(Particle.CRIMSON_SPORE, center.clone().add(0.0D, 1.0D, 0.0D), 45, 2.2D, 0.8D, 2.2D, 0.02D);
        world.playSound(center, Sound.ITEM_SHIELD_BLOCK, 1.3F, 0.65F);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.65F, 1.35F);
        int repelled = 0;
        for (Entity entity : world.getNearbyEntities(center, 5.0D, 3.0D, 5.0D)) {
            if (!(entity instanceof Monster target) || entity.getUniqueId().equals(player.getUniqueId())) continue;
            if (target.isDead() || !target.isValid()) continue;
            target.damage(6.0D, player);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, true, true));
            var direction = target.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() > 0.01D) target.setVelocity(direction.normalize().multiply(1.15D).setY(0.42D));
            target.getWorld().spawnParticle(Particle.CRIMSON_SPORE, target.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.35D, 0.5D, 0.35D, 0.02D);
            repelled++;
        }
        player.sendMessage(plugin.color("&4&lKRVAVÁ BARIÉRA &8» &cSíla strážce tě chrání a odráží nepřátele!"));
        if (repelled > 0) player.sendMessage(plugin.color("&7Odražení nepřátelé: &c" + repelled + "&7."));
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
