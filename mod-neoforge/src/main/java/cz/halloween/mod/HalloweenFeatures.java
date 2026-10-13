package cz.halloween.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class HalloweenFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, HalloweenCoreMod.MOD_ID);
    public static final Supplier<Feature<NoneFeatureConfiguration>> HAUNTED_SURFACE =
            FEATURES.register("haunted_surface", HauntedSurfaceFeature::new);

    private HalloweenFeatures() {}
}
