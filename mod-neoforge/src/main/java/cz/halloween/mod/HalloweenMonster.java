package cz.halloween.mod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Shared server-side base for the first native pass of Halloween mobs.
 *
 * Each entity currently shares Zombie navigation/geometry, but its signature ability now has
 * an explicit wind-up, a player-visible tell, a cooldown, world-policy checks and a range check
 * at impact time. This is a stepping stone to the bespoke AI and animation systems.
 */
public final class HalloweenMonster extends Zombie {
    private int abilityCooldownTicks;
    private int abilityWindupTicks;
    private String pendingAbility = "";
    private UUID pendingTargetId;

    public HalloweenMonster(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        setCustomName(Component.translatable("entity.halloweencore." + id));
        setCustomNameVisible(false);
        setCanPickUpLoot(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (!(level() instanceof ServerLevel serverLevel) || !HalloweenWorldPolicy.isAllowed(serverLevel)) {
            clearPendingAbility();
            return;
        }

        if (abilityCooldownTicks > 0) abilityCooldownTicks--;

        LivingEntity currentTarget = getTarget();
        if (!(currentTarget instanceof Player player)
                || !player.isAlive()
                || player.level() != level()
                || distanceToSqr(player) > 144.0D) {
            clearPendingAbility();
            return;
        }

        if (!pendingAbility.isBlank()) {
            if (pendingTargetId == null || !pendingTargetId.equals(player.getUUID())) {
                clearPendingAbility();
                return;
            }
            if (abilityWindupTicks > 0) abilityWindupTicks--;
            if (abilityWindupTicks <= 0) {
                executeSignatureAbility(serverLevel, player);
                clearPendingAbility();
            }
            return;
        }

        if (abilityCooldownTicks > 0 || tickCount % 10 != 0) return;
        startSignatureAbility(serverLevel, player);
    }

    private void startSignatureAbility(ServerLevel serverLevel, Player target) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
        String tell;
        if (id.equals("goblin_king") || id.equals("vampire_king") || id.equals("headless_nick")) {
            abilityWindupTicks = 20;
            abilityCooldownTicks = 140;
        } else {
            abilityWindupTicks = 12;
            abilityCooldownTicks = 100;
        }

        pendingAbility = id;
        pendingTargetId = target.getUUID();
        switch (id) {
            case "cursed_zombie" -> {
                tell = "Cursed Zombie se chystá kletbou zasáhnout.";
                serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0D,
                        target.getZ(), 8, 0.25D, 0.4D, 0.25D, 0.015D);
            }
            case "void_reaper" -> {
                tell = "Žnec prázdnoty soustředí trhlinu — ustup!";
                serverLevel.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 0.8D,
                        target.getZ(), 14, 0.35D, 0.6D, 0.35D, 0.05D);
            }
            case "gravekeeper" -> {
                tell = "Hrobník připravuje řetězový úder.";
                serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.2D,
                        target.getZ(), 10, 0.4D, 0.1D, 0.4D, 0.03D);
            }
            case "frost_stalker" -> {
                tell = "Ledový stopař se chystá k výpadu — uhni z dosahu!";
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 0.2D,
                        target.getZ(), 16, 0.45D, 0.2D, 0.45D, 0.02D);
            }
            case "blood_spider" -> {
                tell = "Krvavý pavouk míří na tebe.";
                serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.6D,
                        target.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.02D);
            }
            case "pumpkin_wraith" -> {
                tell = "Dýňový přízrak nabíjí plamen.";
                serverLevel.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.8D,
                        target.getZ(), 12, 0.35D, 0.4D, 0.35D, 0.015D);
            }
            case "hex_witch" -> {
                tell = "Hexová čarodějka kreslí kletbu pod tvýma nohama.";
                serverLevel.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 0.2D,
                        target.getZ(), 12, 0.45D, 0.1D, 0.45D, 0.01D);
            }
            case "nightmare" -> {
                tell = "Noční můra se chystá k drtivému nárazu.";
                serverLevel.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 0.2D,
                        target.getZ(), 14, 0.45D, 0.15D, 0.45D, 0.02D);
            }
            case "headless_nick" -> {
                tell = "Headless Nick pozvedá svou čepel — připrav se na útok.";
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + 0.8D,
                        target.getZ(), 18, 0.35D, 0.45D, 0.35D, 0.015D);
            }
            case "goblin_king" -> {
                tell = "Goblin King nabíjí královský otřes — drž si odstup!";
                serverLevel.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 0.2D,
                        target.getZ(), 22, 0.6D, 0.2D, 0.6D, 0.025D);
            }
            case "vampire_king" -> {
                tell = "Vampire King soustředí krvavou kletbu.";
                serverLevel.sendParticles(ParticleTypes.ASH, target.getX(), target.getY() + 0.8D,
                        target.getZ(), 20, 0.45D, 0.5D, 0.45D, 0.015D);
            }
            default -> {
                clearPendingAbility();
                return;
            }
        }
        target.displayClientMessage(Component.literal(tell), true);
    }

    private void executeSignatureAbility(ServerLevel serverLevel, Player target) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
        if (!target.isAlive() || target.level() != level() || !HalloweenWorldPolicy.isAllowed(target.level())) return;

        double range = switch (id) {
            case "void_reaper", "hex_witch", "vampire_king" -> 9.0D;
            case "pumpkin_wraith", "goblin_king" -> 7.0D;
            default -> 4.0D;
        };
        // The initial tell is avoidable: if the player leaves the effective radius during wind-up,
        // the ability misses and cannot apply its status effect.
        if (distanceToSqr(target) > range * range) {
            target.displayClientMessage(Component.literal("Útok Halloween moba minul."), true);
            serverLevel.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 0.1D,
                    target.getZ(), 5, 0.25D, 0.1D, 0.25D, 0.01D);
            return;
        }

        switch (id) {
            case "cursed_zombie" ->
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 70, 0), this);
            case "void_reaper" -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 35, 0), this);
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0), this);
            }
            case "gravekeeper" -> {
                target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 70, 0), this);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 0), this);
            }
            case "frost_stalker" -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 65, 1), this);
            case "blood_spider" -> {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 50, 0), this);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), this);
            }
            case "pumpkin_wraith" -> target.igniteForSeconds(2.5F);
            case "hex_witch" -> {
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0), this);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0), this);
            }
            case "nightmare" -> {
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 65, 0), this);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), this);
                target.knockback(0.55D, target.getX() - getX(), target.getZ() - getZ());
            }
            case "headless_nick" -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 0), this);
            case "goblin_king" -> {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 75, 1), this);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 0), this);
                target.knockback(0.75D, target.getX() - getX(), target.getZ() - getZ());
            }
            case "vampire_king" -> {
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 65, 0), this);
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 45, 0), this);
            }
            default -> { }
        }

        serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 0.8D,
                target.getZ(), 8, 0.3D, 0.4D, 0.3D, 0.015D);
        target.displayClientMessage(Component.literal("Zásah: " + displayName(id) + "."), true);
    }

    private static String displayName(String id) {
        return switch (id) {
            case "cursed_zombie" -> "Cursed Zombie";
            case "void_reaper" -> "Void Reaper";
            case "gravekeeper" -> "Gravekeeper";
            case "frost_stalker" -> "Frost Stalker";
            case "blood_spider" -> "Blood Spider";
            case "pumpkin_wraith" -> "Pumpkin Wraith";
            case "hex_witch" -> "Hex Witch";
            case "nightmare" -> "Nightmare";
            case "headless_nick" -> "Headless Nick";
            case "goblin_king" -> "Goblin King";
            case "vampire_king" -> "Vampire King";
            default -> "Halloween mob";
        };
    }

    private void clearPendingAbility() {
        pendingAbility = "";
        pendingTargetId = null;
        abilityWindupTicks = 0;
    }
}
