package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookPlanInvalidationTest {
    @Test
    void invalidatesForModeFilterOrRecipeReloadChanges() {
        assertTrue(CookPlanInvalidation.recipePlanChanged(
                "blacklist", List.of("a"), 1, "whitelist", List.of("a"), 1));
        assertTrue(CookPlanInvalidation.recipePlanChanged(
                "blacklist", List.of("a"), 1, "blacklist", List.of("b"), 1));
        assertTrue(CookPlanInvalidation.recipePlanChanged(
                "blacklist", List.of("a"), 1, "blacklist", List.of("a"), 2));
        assertFalse(CookPlanInvalidation.recipePlanChanged(
                "blacklist", List.of("a"), 1, "blacklist", List.of("a"), 1));
    }

    @Test
    void invalidatesWhenBoundInventorySummaryChanges() {
        assertTrue(CookPlanInvalidation.inventoryChanged(10, 11));
        assertFalse(CookPlanInvalidation.inventoryChanged(10, 10));
    }
}
