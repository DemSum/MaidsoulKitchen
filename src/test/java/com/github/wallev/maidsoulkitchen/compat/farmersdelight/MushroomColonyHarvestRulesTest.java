package com.github.wallev.maidsoulkitchen.compat.farmersdelight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MushroomColonyHarvestRulesTest {
    @Test
    void requiresMaximumAgeForHarvest() {
        assertFalse(MushroomColonyHarvestRules.isMature(2, 3));
        assertTrue(MushroomColonyHarvestRules.isMature(3, 3));
    }

    @Test
    void knifeKeepsTheColonyAndDropsItsAgeCount() {
        assertEquals(3, MushroomColonyHarvestRules.dropCount(3, true));
    }

    @Test
    void nonKnifeHarvestReturnsFiveMushrooms() {
        assertEquals(5, MushroomColonyHarvestRules.dropCount(3, false));
    }
}
