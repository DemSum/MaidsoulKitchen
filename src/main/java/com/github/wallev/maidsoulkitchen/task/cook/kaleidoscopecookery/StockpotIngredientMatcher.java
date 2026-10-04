package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import java.util.HashSet;
import java.util.Set;

/** Bounded nine-slot assignment: each existing ingredient occupies a different recipe requirement. */
final class StockpotIngredientMatcher {
    private StockpotIngredientMatcher() { }

    static Set<Integer> assignments(boolean[][] accepts, int requirementCount) {
        if (requirementCount < 0 || requirementCount > 9 || accepts.length > requirementCount) return Set.of();
        Set<Integer> masks = Set.of(0);
        for (boolean[] row : accepts) {
            if (row.length != requirementCount) throw new IllegalArgumentException("Invalid ingredient matrix");
            Set<Integer> next = new HashSet<>();
            for (int mask : masks) {
                for (int ingredient = 0; ingredient < requirementCount; ingredient++) {
                    int bit = 1 << ingredient;
                    if ((mask & bit) == 0 && row[ingredient]) next.add(mask | bit);
                }
            }
            masks = next;
            if (masks.isEmpty()) break;
        }
        return masks;
    }
}
