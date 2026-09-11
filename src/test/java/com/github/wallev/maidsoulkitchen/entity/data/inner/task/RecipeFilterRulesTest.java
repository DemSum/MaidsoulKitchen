package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFilterRulesTest {
    @Test
    void blacklistAllowsEverythingExceptSelectedValues() {
        assertFalse(RecipeFilterRules.allows(false, List.of("apple"), "apple"));
        assertTrue(RecipeFilterRules.allows(false, List.of("apple"), "bread"));
    }

    @Test
    void whitelistAllowsOnlySelectedValues() {
        assertTrue(RecipeFilterRules.allows(true, List.of("apple"), "apple"));
        assertFalse(RecipeFilterRules.allows(true, List.of("apple"), "bread"));
    }

    @Test
    void toggleSortsAndReturnsAnImmutableCopy() {
        List<String> changed = RecipeFilterRules.toggle(List.of("bread"), "apple", String::compareTo);

        assertEquals(List.of("apple", "bread"), changed);
        assertThrows(UnsupportedOperationException.class, () -> changed.add("carrot"));
        assertEquals(List.of("bread"), RecipeFilterRules.toggle(changed, "apple", String::compareTo));
    }

    @Test
    void sanitizeDropsUnknownAndDuplicateValuesAndSortsTheRest() {
        assertEquals(
                List.of("apple", "bread"),
                RecipeFilterRules.sanitize(
                        List.of("bread", "unknown", "apple", "bread"),
                        Set.of("apple", "bread"),
                        String::compareTo
                )
        );
    }
}
