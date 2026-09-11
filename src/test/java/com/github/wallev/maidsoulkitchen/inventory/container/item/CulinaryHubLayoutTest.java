package com.github.wallev.maidsoulkitchen.inventory.container.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CulinaryHubLayoutTest {
    @Test
    void simplifiedUiExposesOnlyInputAndOutputBindings() {
        assertArrayEquals(
                new BagType[]{BagType.INGREDIENT, BagType.OUTPUT},
                BagType.DISPLAY_VALS
        );
    }

    @Test
    void legacyInputSectionsRemainOneLogicalInputInventory() {
        assertArrayEquals(
                new BagType[]{
                        BagType.INGREDIENT,
                        BagType.START_ADDITION,
                        BagType.INGREDIENT_ADDITION,
                        BagType.OUTPUT_ADDITION
                },
                BagType.INPUT_VALS
        );
        assertEquals(54, totalSlots(BagType.INPUT_VALS));
        assertEquals(9, BagType.OUTPUT_VAL.size * 9);
    }

    private static int totalSlots(BagType[] types) {
        int result = 0;
        for (BagType type : types) {
            result += type.size * 9;
        }
        return result;
    }
}
