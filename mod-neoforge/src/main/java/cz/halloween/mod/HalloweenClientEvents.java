package cz.halloween.mod;

import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = HalloweenCoreMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HalloweenClientEvents {
    private HalloweenClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // First pass intentionally uses a working renderer for every registered entity.
        // Bespoke geometry, animation states and per-mob render layers are still pending.
        event.registerEntityRenderer(HalloweenEntities.CURSED_ZOMBIE.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.VOID_REAPER.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.GRAVEKEEPER.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.FROST_STALKER.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.BLOOD_SPIDER.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.PUMPKIN_WRAITH.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.HEX_WITCH.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.NIGHTMARE.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.HEADLESS_NICK.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.GOBLIN_KING.get(), ZombieRenderer::new);
        event.registerEntityRenderer(HalloweenEntities.VAMPIRE_KING.get(), ZombieRenderer::new);
    }
}
