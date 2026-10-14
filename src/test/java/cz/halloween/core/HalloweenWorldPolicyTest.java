package cz.halloween.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class HalloweenWorldPolicyTest {
    @Test
    void spawnIsAlwaysDeniedByGameplayPolicy() {
        assertFalse(HalloweenWorldPolicy.isGameplayWorldEligible("Spawn", List.of(), "BLACKLIST"));
        assertFalse(HalloweenWorldPolicy.isGameplayWorldEligible(" spawn ", List.of("Spawn"), "WHITELIST"));
        assertFalse(HalloweenWorldPolicy.isGameplayWorldEligible("SPAWN", List.of(), "WHITELIST"));
    }

    @Test
    void blacklistIsCaseInsensitiveAndAllowsUnlistedWorlds() {
        assertFalse(HalloweenWorldPolicy.isGameplayWorldEligible("survival", List.of("Survival"), "BLACKLIST"));
        assertTrue(HalloweenWorldPolicy.isGameplayWorldEligible("resource_world", List.of("Survival"), "BLACKLIST"));
    }

    @Test
    void whitelistDeniesWorldsNotExplicitlyListed() {
        assertTrue(HalloweenWorldPolicy.isGameplayWorldEligible("Survival", List.of("survival"), "WHITELIST"));
        assertFalse(HalloweenWorldPolicy.isGameplayWorldEligible("nether", List.of("survival"), "WHITELIST"));
    }

    @Test
    void automaticGenerationFailsClosedWithoutAllowList() {
        assertFalse(HalloweenWorldPolicy.isAutomaticWorldEligible("world", List.of()));
        assertFalse(HalloweenWorldPolicy.isAutomaticWorldEligible("world", null));
        assertFalse(HalloweenWorldPolicy.isAutomaticWorldEligible("Spawn", List.of("Spawn", "world")));
    }

    @Test
    void automaticGenerationRequiresExplicitApprovedWorld() {
        assertTrue(HalloweenWorldPolicy.isAutomaticWorldEligible("Survival", List.of("survival", "resource_world")));
        assertFalse(HalloweenWorldPolicy.isAutomaticWorldEligible("survival_nether", List.of("survival")));
        assertFalse(HalloweenWorldPolicy.isAutomaticWorldEligible("", List.of("world")));
    }
}
