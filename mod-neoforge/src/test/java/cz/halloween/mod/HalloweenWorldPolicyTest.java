package cz.halloween.mod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HalloweenWorldPolicyTest {
    @Test
    void spawnIsAlwaysReservedRegardlessOfCapitalizationOrNamespace() {
        assertTrue(HalloweenWorldIdPolicy.isReservedWorldIdentifier("Spawn"));
        assertTrue(HalloweenWorldIdPolicy.isReservedWorldIdentifier("spawn"));
        assertTrue(HalloweenWorldIdPolicy.isReservedWorldIdentifier("minecraft:spawn"));
        assertTrue(HalloweenWorldIdPolicy.isReservedWorldIdentifier("multiverse:Spawn"));
        assertTrue(HalloweenWorldIdPolicy.isReservedWorldIdentifier("spawn/resource"));
        assertTrue(HalloweenWorldIdPolicy.isReservedWorldIdentifier(" "));
        assertTrue(HalloweenWorldIdPolicy.isReservedWorldIdentifier(null));
    }

    @Test
    void anEmptyAllowListFailsClosed() {
        assertFalse(HalloweenWorldIdPolicy.isConfiguredWorldAllowed("minecraft:overworld", List.of()));
        assertFalse(HalloweenWorldIdPolicy.isConfiguredWorldAllowed("minecraft:overworld", null));
    }

    @Test
    void onlyExactNonReservedDimensionIdsAreAllowed() {
        assertTrue(HalloweenWorldIdPolicy.isConfiguredWorldAllowed(
                "minecraft:overworld", List.of("minecraft:overworld")));
        assertFalse(HalloweenWorldIdPolicy.isConfiguredWorldAllowed(
                "minecraft:the_nether", List.of("minecraft:overworld")));
        assertFalse(HalloweenWorldIdPolicy.isConfiguredWorldAllowed(
                "minecraft:spawn", List.of("minecraft:spawn")));
        assertFalse(HalloweenWorldIdPolicy.isConfiguredWorldAllowed(
                "minecraft:overworld", List.of("Spawn", "minecraft:spawn")));
        assertTrue(HalloweenWorldIdPolicy.isConfiguredWorldAllowed(
                "minecraft:overworld", List.of(" minecraft:overworld ")));
    }

    @Test
    void fragmentRewardsArePredictableAndBounded() {
        assertTrue(HalloweenProgressRewards.mobKillFragments(false) == 1);
        assertTrue(HalloweenProgressRewards.mobKillFragments(true) == 25);
        assertTrue(HalloweenProgressRewards.oreFragments(4) == 1);
        assertTrue(HalloweenProgressRewards.oreFragments(5) == 0);
        assertTrue(HalloweenProgressRewards.cropFragments(8) == 1);
        assertTrue(HalloweenProgressRewards.cropFragments(7) == 0);
        assertTrue(HalloweenProgressRewards.cropFragments(0) == 0);
    }

}
