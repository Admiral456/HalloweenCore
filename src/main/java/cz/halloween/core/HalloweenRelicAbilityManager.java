package cz.halloween.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side abilities for two portable Halloween relics.
 *
 * Custom appearance is supplied by the optional ItemsAdder items with matching IDs.
 * Without those assets, the same tagged vanilla bases still provide server mechanics.
 */
public final class HalloweenRelicAbilityManager implements Listener {
    private static final String BATWING = "batwing-elytra";
    private static final String COWL = "nocturne-sovereign-cowl";
    private static final long BATWING_COOLDOWN_MS = 20_000L;
    private static final long COWL_COOLDOWN_MS = 30_000L;

    private final HalloweenCore plugin;
    private final NamespacedKey relicKey;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private BukkitTask passiveTask;

    public HalloweenRelicAbilityManager(HalloweenCore plugin) {
        this.plugin = plugin;
        this.relicKey = new NamespacedKey(plugin, "halloween_relic");
    }

    public void start() {
        if (passiveTask != null) return;
        passiveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::applyCowlPassive, 20L, 40L);
    }

    public void stop() {
        if (passiveTask != null) {
            passiveTask.cancel();
            passiveTask = null;
        }
        cooldowns.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!plugin.isEventEnabled() || !plugin.isEligibleGameplayWorld(player.getWorld())
                || !plugin.isEligibleGameplayPlayer(player)) return;

        ItemStack chest = player.getInventory().getChestplate();
        if (player.isGliding() && isRelic(chest, BATWING)) {
            useBatwingDash(player);
            return;
        }

        ItemStack helmet = player.getInventory().getHelmet();
        if (isRelic(helmet, COWL)) useCowlPulse(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void useBatwingDash(Player player) {
        if (!ready(player, BATWING, BATWING_COOLDOWN_MS)) return;

        Vector velocity = player.getVelocity();
        Vector boosted = velocity.multiply(1.18D);
        boosted.setX(Math.max(-1.9D, Math.min(1.9D, boosted.getX())));
        boosted.setZ(Math.max(-1.9D, Math.min(1.9D, boosted.getZ())));
        boosted.setY(Math.max(-0.25D, Math.min(0.9D, boosted.getY() + 0.14D)));
        player.setVelocity(boosted);

        Location effect = player.getLocation().clone().add(0.0D, 0.6D, 0.0D);
        player.getWorld().spawnParticle(Particle.ASH, effect, 30, 0.45D, 0.35D, 0.45D, 0.025D);
        player.getWorld().spawnParticle(Particle.CRIMSON_SPORE, effect, 22, 0.35D, 0.25D, 0.35D, 0.015D);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.9f, 0.7f);
        player.sendActionBar(plugin.color("&5Batwing &8» &fTemný vzdušný výpad"));
    }

    private void useCowlPulse(Player player) {
        if (!ready(player, COWL, COWL_COOLDOWN_MS)) return;

        int affected = 0;
        Location center = player.getLocation();
        for (var entity : player.getWorld().getNearbyEntities(center, 9.0D, 5.0D, 9.0D)) {
            if (!(entity instanceof Monster monster) || !monster.isValid() || monster.isDead()) continue;
            monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, true, false, false));
            monster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, true, false, false));
            Location location = monster.getLocation().clone().add(0.0D, 1.0D, 0.0D);
            monster.getWorld().spawnParticle(Particle.SOUL, location, 10, 0.3D, 0.45D, 0.3D, 0.015D);
            affected++;
        }

        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                player.getLocation().clone().add(0.0D, 1.0D, 0.0D),
                48, 2.2D, 1.0D, 2.2D, 0.08D);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_LISTENING, 0.7f, 0.65f);
        player.sendMessage(plugin.color("&8&lNocturne Cowl &8» &7Temná vlna zasáhla &e" + affected + " &7nepřátel."));
    }

    private void applyCowlPassive() {
        if (!plugin.isEventEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.isEligibleGameplayWorld(player.getWorld())
                    || !plugin.isEligibleGameplayPlayer(player)
                    || !isRelic(player.getInventory().getHelmet(), COWL)) continue;
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, 100, 0, true, false, false
            ));
        }
    }

    private boolean ready(Player player, String ability, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long until = playerCooldowns.getOrDefault(ability, 0L);
        if (until > now) {
            long seconds = Math.max(1L, (until - now + 999L) / 1000L);
            player.sendActionBar(plugin.color("&8Relikvie se obnovuje: &e" + seconds + " s"));
            return false;
        }
        playerCooldowns.put(ability, now + cooldownMillis);
        return true;
    }

    private boolean isRelic(ItemStack item, String identity) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null) return false;
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(relicKey, PersistentDataType.STRING);
        return identity.equalsIgnoreCase(value);
    }
}
