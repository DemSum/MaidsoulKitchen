package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookTargetStateTest {
    @Test
    void readyWhenValidAndWithinInteractionRange() {
        assertEquals(
                CookTargetState.StartState.READY,
                CookTargetState.decideStart(true, true, false)
        );
    }

    @Test
    void waitsWhileMatchingWalkTargetIsStillActive() {
        assertEquals(
                CookTargetState.StartState.WAITING_FOR_PATH,
                CookTargetState.decideStart(true, false, true)
        );
    }

    @Test
    void clearsWhenPathTargetDisappearsOrChanges() {
        assertEquals(
                CookTargetState.StartState.CLEAR_INVALID,
                CookTargetState.decideStart(true, false, false)
        );
    }

    @Test
    void clearsInvalidOrDestroyedDeviceBeforeConsideringPath() {
        assertEquals(
                CookTargetState.StartState.CLEAR_INVALID,
                CookTargetState.decideStart(false, true, true)
        );
    }

}
