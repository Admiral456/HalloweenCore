package cz.halloween.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vanilla-only, persistent pumpkin-headed villager NPC.
 *
 * The marker is stored on the entity itself, so the NPC survives a server restart
 * without a separate coordinate registry or custom model/resource-pack dependency.
 */
public final class HalloweenPumpkinVillagerManager implements Listener {
    private final HalloweenCore plugin;
    private final NamespacedKey npcKey;
    private final Map<UUID, Long> lastInteraction = new HashMap<>();

    public HalloweenPumpkinVillagerManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "pumpkin_villager");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("pumpkin-villager.enabled", true);
    }

    public Villager spawn(Player creator, String requestedName) {
        if (creator == null || !creator.isOnline() || !isEnabled()) return null;

        String name = requestedName == null ? "" : requestedName.trim();
        if (name.isEmpty()) name = "Dýňový poutník";
        if (name.length() > 48) name = name.substring(0, 48);

        Location spawnLocation = creator.getLocation().clone();
        Vector facing = spawnLocation.getDirection().setY(0.0D);
        if (facing.lengthSquared() > 0.0001D) {
            facing.normalize();
            spawnLocation.add(facing.multiply(2.0D));
        }
        spawnLocation.setYaw(creator.getLocation().getYaw() + 180.0F);
        spawnLocation.setPitch(0.0F);

        final String displayName = plugin.color(name);
        return creator.getWorld().spawn(spawnLocation, Villager.class, villager -> {
            villager.getPersistentDataContainer().set(npcKey, PersistentDataType.BYTE, (byte) 1);
            villager.setCustomName(displayName);
            villager.setCustomNameVisible(true);
            villager.setPersistent(true);
            villager.setInvulnerable(true);
            villager.setAI(false);
            villager.setAware(false);
            villager.setCanPickupItems(false);
            villager.setSilent(true);
            villager.setProfession(Villager.Profession.LIBRARIAN);
            villager.setVillagerLevel(5);
            villager.setVillagerExperience(0);

            if (villager.getEquipment() != null) {
                villager.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                villager.getEquipment().setHelmetDropChance(0.0F);
            }
        });
    }

    public boolean removeNearest(Player player, double radius) {
        if (player == null || !player.isOnline()) return false;
        double safeRadius = Math.max(1.0D, Math.min(32.0D, radius));
        double maxDistanceSquared = safeRadius * safeRadius;
        Villager nearest = null;
        double nearestDistanceSquared = maxDistanceSquared;

        for (Entity entity : player.getNearbyEntities(safeRadius, safeRadius, safeRadius)) {
            if (!(entity instanceof Villager villager) || !isPumpkinVillager(villager)) continue;
            double distanceSquared = villager.getLocation().distanceSquared(player.getLocation());
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = villager;
            }
        }

        if (nearest == null) return false;
        nearest.remove();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager) || !isPumpkinVillager(villager)) return;

        // Cancel both hands to prevent the vanilla trade window; only the main hand
        // renders a single dialogue if the client sends an event for each hand.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !isEnabled()) return;

        Player player = event.getPlayer();
        if (!plugin.isEventEnabled()) {
            player.sendMessage(plugin.color("&8[&6Halloween&8] &7Halloween je momentálně vypnutý."));
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(250L, Math.min(10_000L,
                plugin.getConfig().getLong("pumpkin-villager.interact-cooldown-millis", 2_000L)));
        long previous = lastInteraction.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < cooldownMillis) return;
        lastInteraction.put(player.getUniqueId(), now);

        player.sendMessage(Component.text("Dýňový poutník", NamedTextColor.GOLD)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Dýně šeptají, že se blíží dlouhá noc.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("Našel jsi nějaké fragmenty? Podívej se, co za ně získáš.",
                NamedTextColor.GRAY));
        player.sendMessage(action("[ Otevřít odměny ]", "/halloween rewards",
                "Otevře obchod s Halloween odměnami.").append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(action("[ Denní výzva ]", "/halloween challenge",
                        "Zobrazí dnešní výzvu."))
                .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                .append(action("[ Serverový postup ]", "/halloween progress",
                        "Zobrazí společný postup serveru.")));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager villager && isPumpkinVillager(villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastInteraction.remove(event.getPlayer().getUniqueId());
    }

    private Component action(String label, String command, String tooltip) {
        return Component.text(label, NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text(tooltip, NamedTextColor.GRAY)));
    }

    private boolean isPumpkinVillager(Villager villager) {
        return villager != null
                && villager.getPersistentDataContainer().has(npcKey, PersistentDataType.BYTE);
    }
}
