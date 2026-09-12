package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookDataValidationTest {
    @Test
    void normalizesUnknownModesAndDuplicateRecipeIds() {
        assertEquals(CookDataRules.BLACKLIST, CookDataRules.normalizeMode("invalid"));
        assertEquals(List.of("a", "b"), CookDataRules.normalizeRecipes(List.of("a", "a", "b")));
        assertTrue(CookDataRules.isValidMode(CookDataRules.WHITELIST));
        assertFalse(CookDataRules.isValidMode("invalid"));
    }

    @Test
    void boundsNetworkControlledFilterLists() {
        List<String> recipes = new ArrayList<>();
        for (int index = 0; index < CookDataRules.MAX_FILTER_ENTRIES + 20; index++) {
            recipes.add("test:recipe_" + index);
        }
        List<String> normalized = CookDataRules.normalizeRecipes(recipes);

        assertEquals(CookDataRules.MAX_FILTER_ENTRIES, normalized.size());
    }
}
