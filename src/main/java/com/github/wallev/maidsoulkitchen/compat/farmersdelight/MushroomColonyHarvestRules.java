package com.github.wallev.maidsoulkitchen.compat.farmersdelight;

/** Stable harvest rules kept separate from Minecraft state access for testing. */
final class MushroomColonyHarvestRules {
    private static final int NON_KNIFE_DROPS = 5;

    private MushroomColonyHarvestRules() {
    }

    static boolean isMature(int age, int maximumAge) {
        return age >= maximumAge;
    }

    static int dropCount(int age, boolean usingKnife) {
        return usingKnife ? Math.max(0, age) : NON_KNIFE_DROPS;
    }
}
