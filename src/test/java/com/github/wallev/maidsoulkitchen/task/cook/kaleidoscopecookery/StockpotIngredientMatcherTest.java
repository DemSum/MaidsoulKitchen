package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class StockpotIngredientMatcherTest {
    @Test void existingBroadMatchKeepsBothWaysToComplete() {
        assertEquals(Set.of(1, 2), StockpotIngredientMatcher.assignments(new boolean[][]{{true, true}}, 2));
    }
    @Test void broadAndNarrowIngredientsMustUseDifferentRequirements() {
        assertEquals(Set.of(3), StockpotIngredientMatcher.assignments(
                new boolean[][]{{true, true}, {true, false}}, 2));
    }
    @Test void repeatedRawMaterialsCannotReuseOneRequirement() {
        assertTrue(StockpotIngredientMatcher.assignments(new boolean[][]{{true, false}, {true, false}}, 2).isEmpty());
    }
    @Test void anUnknownRawMaterialMakesThePlanStructurallyImpossible() {
        assertTrue(StockpotIngredientMatcher.assignments(new boolean[][]{{false, false}}, 2).isEmpty());
    }
    @Test void fullNineSlotAmbiguityStaysBoundedAndCoversAllRequirements() {
        boolean[][] matrix = new boolean[9][9];
        for (boolean[] row : matrix) java.util.Arrays.fill(row, true);
        assertEquals(Set.of(511), StockpotIngredientMatcher.assignments(matrix, 9));
    }
    @Test void emptyPotAllowsCompletionWithoutPretendingResourcesExist() {
        assertEquals(Set.of(0), StockpotIngredientMatcher.assignments(new boolean[0][0], 3));
    }
    @Test void moreThanNineRequirementsOrExcessRawMaterialsAreRejected() {
        assertTrue(StockpotIngredientMatcher.assignments(new boolean[0][0], 10).isEmpty());
        assertTrue(StockpotIngredientMatcher.assignments(new boolean[3][2], 2).isEmpty());
    }
}
