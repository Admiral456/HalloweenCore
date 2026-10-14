package cz.halloween.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HalloweenWorldPolicyTest {
    @Test
    void spawnIsReservedRegardlessOfCaseOrAllowList() {
        assertTrue(HalloweenWorldPolicy.isReservedWorld("Spawn"));
        assertTrue(HalloweenWorldPolicy.isReservedWorld("SPAWN"));
        assertTrue(HalloweenWorldPolicy.isReservedWorld("spawn"));
        assertFalse(HalloweenWorldPolicy.isReservedWorld("world"));
        assertFalse(HalloweenWorldPolicy.isReservedWorld(null));

        assertFalse(HalloweenWorldPolicy.isExplicitlyAllowedForAutomaticGeneration(
                "Spawn", List.of("Spawn", "world"), true));
        assertFalse(HalloweenWorldPolicy.isExplicitlyAllowedForAutomaticGeneration(
                "spawn", List.of("spawn"), true));
    }

    @Test
    void automaticGenerationRequiresAnExplicitGameplayAllowListEntry() {
        assertFalse(HalloweenWorldPolicy.isExplicitlyAllowedForAutomaticGeneration(
                "world", List.of(), true));
        assertFalse(HalloweenWorldPolicy.isExplicitlyAllowedForAutomaticGeneration(
                "world", List.of("nether"), true));
        assertFalse(HalloweenWorldPolicy.isExplicitlyAllowedForAutomaticGeneration(
                "world", List.of("WORLD"), false));
        assertTrue(HalloweenWorldPolicy.isExplicitlyAllowedForAutomaticGeneration(
                "world", List.of("WORLD"), true));
    }
}
