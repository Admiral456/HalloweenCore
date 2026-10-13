package cz.halloween.mod;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(HalloweenCoreMod.MOD_ID)
public final class HalloweenCoreMod {
    public static final String MOD_ID = "halloweencore";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HalloweenCoreMod(IEventBus modBus, ModContainer modContainer) {
        HalloweenConfig.register(modContainer);
        HalloweenEntities.ENTITY_TYPES.register(modBus);
        HalloweenItems.ITEMS.register(modBus);
        HalloweenItems.CREATIVE_TABS.register(modBus);
        HalloweenFeatures.FEATURES.register(modBus);
        modBus.addListener(HalloweenEntities::registerAttributes);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> modBus.addListener(HalloweenClientEvents::registerRenderers));
        NeoForge.EVENT_BUS.register(HalloweenGameplay.class);
        LOGGER.info("HalloweenCore Native initialized for Minecraft 1.21.10 / NeoForge 21.10.64.");
        LOGGER.warn("Halloween world operations use a default-deny allow-list; configure intended world IDs before enabling gameplay.");
    }
}
