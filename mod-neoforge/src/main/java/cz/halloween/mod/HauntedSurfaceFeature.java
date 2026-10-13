package cz.halloween.mod;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public final class HauntedSurfaceFeature extends Feature<NoneFeatureConfiguration> {
    public HauntedSurfaceFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        if (!HalloweenConfig.WORLD_DECORATIONS_ENABLED.get()
                || !HalloweenWorldPolicy.isAllowedGeneration(level)) {
            return false;
        }

        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, context.origin());
        BlockPos groundPos = surface.below();
        BlockState ground = level.getBlockState(groundPos);
        if (!isNaturalGround(ground) || !level.getBlockState(surface).isAir()
                || !level.getBlockState(surface.above()).isAir()) {
            return false;
        }

        RandomSource random = context.random();
        BlockState decoration = switch (random.nextInt(100)) {
            case 0, 1 -> Blocks.CARVED_PUMPKIN.defaultBlockState();
            case 2, 3, 4 -> Blocks.BROWN_MUSHROOM.defaultBlockState();
            case 5, 6 -> Blocks.RED_MUSHROOM.defaultBlockState();
            case 7, 8 -> Blocks.COBWEB.defaultBlockState();
            case 9 -> Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState();
            default -> null;
        };
        if (decoration == null || level.getBlockState(surface).hasBlockEntity()) return false;
        return level.setBlock(surface, decoration, 2);
    }

    private boolean isNaturalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.ROOTED_DIRT);
    }
}
