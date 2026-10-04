package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

/** Pure slot-count rules shared by the DrinkBeer task and unit tests. */
public final class DrinkBeerBarrelRules {
    public static final int INGREDIENT_SLOTS = 4;
    public static final int CUP_SLOT = 4;
    public static final int REQUIRED_CUPS = 4;

    private DrinkBeerBarrelRules() {
    }

    public static int targetAmount(int targetSlot, int requested) {
        return targetSlot >= 0 && targetSlot < INGREDIENT_SLOTS
                ? Math.min(requested, 1)
                : requested;
    }

    public static boolean needsCups(int cupCount, boolean slotEmpty, boolean containsEmptyBeerMugs) {
        return slotEmpty || containsEmptyBeerMugs && cupCount < REQUIRED_CUPS;
    }
}
