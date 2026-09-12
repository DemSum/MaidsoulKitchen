package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookTargetCycleTest {
    @Test
    void defersVisitedTargetsUntilAnOldTargetStartsTheNextCycle() {
        CookTargetCycle cycle = new CookTargetCycle();
        long first = 1L;
        long second = 2L;

        assertTrue(cycle.prefers(first));
        cycle.recordSelection(first);
        assertFalse(cycle.prefers(first));
        assertTrue(cycle.prefers(second));

        cycle.recordSelection(second);
        assertEquals(2, cycle.rememberedTargetCount());

        cycle.recordSelection(first);
        assertEquals(1, cycle.rememberedTargetCount());
        assertFalse(cycle.prefers(first));
        assertTrue(cycle.prefers(second));
    }
}
