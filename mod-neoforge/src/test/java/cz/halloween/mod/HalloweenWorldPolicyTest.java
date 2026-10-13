package cz.halloween.mod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HalloweenWorldPolicyTest {
    @Test
    void spawnIsAlwaysReservedRegardlessOfCapitalizationOrNamespace() {
        assertTrue(HalloweenWorldPolicy.isReservedWorldIdentifier("Spawn"));
        assertTrue(HalloweenWorldPolicy.isReservedWorldIdentifier("spawn"));
        assertTrue(HalloweenWorldPolicy.isReservedWorldIdentifier("minecraft:spawn"));
        assertTrue(HalloweenWorldPolicy.isReservedWorldIdentifier("multiverse:Spawn"));
    }

    @Test
    void anEmptyAllowListFailsClosed() {
        assertFalse(HalloweenWorldPolicy.isConfiguredWorldAllowed("minecraft:overworld", List.of()));
    }

    @Test
    void onlyExactNonReservedDimensionIdsAreAllowed() {
        assertTrue(HalloweenWorldPolicy.isConfiguredWorldAllowed(
                "minecraft:overworld", List.of("minecraft:overworld")));
        assertFalse(HalloweenWorldPolicy.isConfiguredWorldAllowed(
                "minecraft:the_nether", List.of("minecraft:overworld")));
        assertFalse(HalloweenWorldPolicy.isConfiguredWorldAllowed(
                "minecraft:spawn", List.of("minecraft:spawn")));
        assertFalse(HalloweenWorldPolicy.isConfiguredWorldAllowed(
                "minecraft:overworld", List.of("Spawn", "minecraft:spawn")));
    }
}
