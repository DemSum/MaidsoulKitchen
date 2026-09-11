package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DrinkBeerBarrelInventoryTest {
    @Test
    void emptyCupSlotNeedsCups() {
        assertTrue(DrinkBeerBarrelRules.needsCups(0, true, false));
    }

    @Test
    void partialEmptyMugStackNeedsCups() {
        assertTrue(DrinkBeerBarrelRules.needsCups(3, false, true));
    }

    @Test
    void fullOrForeignCupSlotDoesNotNeedCups() {
        assertFalse(DrinkBeerBarrelRules.needsCups(4, false, true));
        assertFalse(DrinkBeerBarrelRules.needsCups(1, false, false));
    }

    @Test
    void ingredientSlotsAreCappedButCupSlotKeepsRecipeAmount() {
        assertEquals(1, DrinkBeerBarrelRules.targetAmount(0, 12));
        assertEquals(1, DrinkBeerBarrelRules.targetAmount(3, 4));
        assertEquals(4, DrinkBeerBarrelRules.targetAmount(4, 4));
    }
}
