package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CulinaryHubRangeTest {
    @Test
    void rangeIsAnchoredToTheWorkCenter() {
        assertTrue(CulinaryHubRange.contains(
                10, 64, 10, 14, 64, 10, 5.0));
        assertFalse(CulinaryHubRange.contains(
                10, 64, 10, 16, 64, 10, 5.0));
    }

    @Test
    void rejectsEmptyWorkRange() {
        assertFalse(CulinaryHubRange.contains(
                0, 0, 0, 0, 0, 0, 0.0));
    }
}
