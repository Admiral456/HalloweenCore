package cz.halloween.mod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class HalloweenEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES =
            DeferredRegister.createEntities(HalloweenCoreMod.MOD_ID);

    public static final Supplier<EntityType<HalloweenMonster>> CURSED_ZOMBIE = entity("cursed_zombie", 0.6F, 1.95F);
    public static final Supplier<EntityType<HalloweenMonster>> VOID_REAPER = entity("void_reaper", 0.7F, 2.1F);
    public static final Supplier<EntityType<HalloweenMonster>> GRAVEKEEPER = entity("gravekeeper", 0.8F, 2.1F);
    public static final Supplier<EntityType<HalloweenMonster>> FROST_STALKER = entity("frost_stalker", 0.6F, 1.95F);
    public static final Supplier<EntityType<HalloweenMonster>> BLOOD_SPIDER = entity("blood_spider", 1.0F, 0.8F);
    public static final Supplier<EntityType<HalloweenMonster>> PUMPKIN_WRAITH = entity("pumpkin_wraith", 0.8F, 2.0F);
    public static final Supplier<EntityType<HalloweenMonster>> HEX_WITCH = entity("hex_witch", 0.6F, 1.95F);
    public static final Supplier<EntityType<HalloweenMonster>> NIGHTMARE = entity("nightmare", 0.9F, 2.4F);
    public static final Supplier<EntityType<HalloweenMonster>> HEADLESS_NICK = entity("headless_nick", 0.8F, 2.4F);
    public static final Supplier<EntityType<HalloweenMonster>> GOBLIN_KING = entity("goblin_king", 3.5F, 8.0F);
    public static final Supplier<EntityType<HalloweenMonster>> VAMPIRE_KING = entity("vampire_king", 3.0F, 6.5F);

    private HalloweenEntities() {}

    private static Supplier<EntityType<HalloweenMonster>> entity(String id, float width, float height) {
        return ENTITY_TYPES.registerEntityType(id, HalloweenMonster::new, MobCategory.MONSTER,
                builder -> builder.sized(width, height).clientTrackingRange(10).updateInterval(2));
    }

    public static EntityType<HalloweenMonster> byId(String id) {
        if (id == null) return null;
        return switch (id.toLowerCase(java.util.Locale.ROOT).replace('-', '_')) {
            case "cursed_zombie" -> CURSED_ZOMBIE.get();
            case "void_reaper" -> VOID_REAPER.get();
            case "gravekeeper" -> GRAVEKEEPER.get();
            case "frost_stalker" -> FROST_STALKER.get();
            case "blood_spider" -> BLOOD_SPIDER.get();
            case "pumpkin_wraith" -> PUMPKIN_WRAITH.get();
            case "hex_witch" -> HEX_WITCH.get();
            case "nightmare" -> NIGHTMARE.get();
            case "headless_nick" -> HEADLESS_NICK.get();
            case "goblin_king" -> GOBLIN_KING.get();
            case "vampire_king" -> VAMPIRE_KING.get();
            default -> null;
        };
    }

    public static boolean isBoss(EntityType<?> type) {
        return type != null && (type == HEADLESS_NICK.get()
                || type == GOBLIN_KING.get()
                || type == VAMPIRE_KING.get());
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CURSED_ZOMBIE.get(), attributes(36.0D, 8.0D, 0.30D));
        event.put(VOID_REAPER.get(), attributes(42.0D, 10.0D, 0.32D));
        event.put(GRAVEKEEPER.get(), attributes(54.0D, 11.0D, 0.23D));
        event.put(FROST_STALKER.get(), attributes(34.0D, 9.0D, 0.38D));
        event.put(BLOOD_SPIDER.get(), attributes(24.0D, 7.0D, 0.42D));
        event.put(PUMPKIN_WRAITH.get(), attributes(44.0D, 12.0D, 0.30D));
        event.put(HEX_WITCH.get(), attributes(32.0D, 8.0D, 0.27D));
        event.put(NIGHTMARE.get(), attributes(80.0D, 14.0D, 0.34D));
        event.put(HEADLESS_NICK.get(), attributes(100.0D, 16.0D, 0.42D));
        event.put(GOBLIN_KING.get(), attributes(600.0D, 24.0D, 0.28D));
        event.put(VAMPIRE_KING.get(), attributes(1000.0D, 28.0D, 0.32D));
    }

    private static AttributeSupplier attributes(double health, double damage, double speed) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, health)
                .add(Attributes.ATTACK_DAMAGE, damage)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .build();
    }
}
