package cz.halloween.mod;

/**
 * Pure reward rules, kept outside Minecraft classes so the economy can be tested without
 * launching a game runtime.
 */
public final class HalloweenProgressRewards {
    private HalloweenProgressRewards() {}

    /** Common special mobs award one fragment; named bosses award a larger, bounded reward. */
    public static int mobKillFragments(boolean boss) {
        return boss ? 25 : 1;
    }

    /** Mining fragments arrive every fourth eligible ore block, avoiding one fragment per block. */
    public static int oreFragments(long totalOresMined) {
        return totalOresMined > 0 && totalOresMined % 4 == 0 ? 1 : 0;
    }

    /** Farming fragments arrive every eighth fully grown crop harvested. */
    public static int cropFragments(long totalMatureCropsHarvested) {
        return totalMatureCropsHarvested > 0 && totalMatureCropsHarvested % 8 == 0 ? 1 : 0;
    }
}
