package cz.halloween.mod;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = HalloweenCoreMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HalloweenClientEvents {
    private HalloweenClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Every registered entity gets a valid renderer and a separate 64x64 texture.
        // The first pass still uses zombie geometry; bespoke animated meshes are pending.
        register(event, HalloweenEntities.CURSED_ZOMBIE.get(), "cursed_zombie");
        register(event, HalloweenEntities.VOID_REAPER.get(), "void_reaper");
        register(event, HalloweenEntities.GRAVEKEEPER.get(), "gravekeeper");
        register(event, HalloweenEntities.FROST_STALKER.get(), "frost_stalker");
        register(event, HalloweenEntities.BLOOD_SPIDER.get(), "blood_spider");
        register(event, HalloweenEntities.PUMPKIN_WRAITH.get(), "pumpkin_wraith");
        register(event, HalloweenEntities.HEX_WITCH.get(), "hex_witch");
        register(event, HalloweenEntities.NIGHTMARE.get(), "nightmare");
        register(event, HalloweenEntities.HEADLESS_NICK.get(), "headless_nick");
        register(event, HalloweenEntities.GOBLIN_KING.get(), "goblin_king");
        register(event, HalloweenEntities.VAMPIRE_KING.get(), "vampire_king");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void register(EntityRenderersEvent.RegisterRenderers event,
                                 EntityType<HalloweenMonster> type, String textureId) {
        EntityRendererProvider provider = context -> new TexturedHalloweenZombieRenderer(context, textureId);
        event.registerEntityRenderer((EntityType) type, provider);
    }

    private static final class TexturedHalloweenZombieRenderer extends ZombieRenderer {
        private final ResourceLocation texture;

        private TexturedHalloweenZombieRenderer(EntityRendererProvider.Context context, String textureId) {
            super(context);
            this.texture = ResourceLocation.fromNamespaceAndPath(
                    HalloweenCoreMod.MOD_ID, "entity/" + textureId);
        }

        @Override
        protected ResourceLocation getTextureLocation(ZombieRenderState state) {
            return texture;
        }
    }
}
