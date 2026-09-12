package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookTargetCycleTest {
    @Test
    void avoidsPreviouslySelectedDevicesUntilTheCycleWraps() {
        CookTargetCycle cycle = new CookTargetCycle();

        assertTrue(cycle.prefers(11L));
        cycle.recordSelection(11L);
        assertFalse(cycle.prefers(11L));
        assertTrue(cycle.prefers(12L));

        cycle.recordSelection(12L);
        cycle.recordSelection(11L);
        assertEquals(1, cycle.rememberedTargetCount());
        assertFalse(cycle.prefers(11L));
        assertTrue(cycle.prefers(12L));
    }

    @Test
    void keepsHistoryBoundedForDenseDeviceAreas() {
        CookTargetCycle cycle = new CookTargetCycle();
        for (long position = 0; position < 80; position++) cycle.recordSelection(position);

        assertEquals(64, cycle.rememberedTargetCount());
        assertTrue(cycle.prefers(0L));
        assertFalse(cycle.prefers(79L));
    }
}
