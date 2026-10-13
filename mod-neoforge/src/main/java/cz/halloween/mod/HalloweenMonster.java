package cz.halloween.mod;

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

public final class HalloweenMonster extends Zombie {
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
        if (level().isClientSide() || tickCount % 60 != 0) return;
        if (!(level() instanceof ServerLevel serverLevel) || !HalloweenWorldPolicy.isAllowed(serverLevel)) return;
        LivingEntity target = getTarget();
        if (!(target instanceof Player player) || !player.isAlive() || distanceToSqr(player) > 36.0D) return;

        String id = BuiltInRegistries.ENTITY_TYPE.getKey(getType()).getPath();
        switch (id) {
            case "cursed_zombie" -> player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
            case "void_reaper" -> player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this);
            case "gravekeeper" -> player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 0), this);
            case "frost_stalker" -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
            case "blood_spider" -> player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
            case "pumpkin_wraith" -> player.igniteForSeconds(2.5F);
            case "hex_witch" -> player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
            case "nightmare" -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), this);
            }
            case "headless_nick" -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), this);
            case "goblin_king" -> {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), this);
                player.knockback(0.9D, getX() - player.getX(), getZ() - player.getZ());
            }
            case "vampire_king" -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
            }
            default -> { }
        }
    }
}
