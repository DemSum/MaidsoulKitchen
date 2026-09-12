package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookTargetStateTest {
    @Test
    void readyWhenValidAndWithinInteractionRange() {
        assertEquals(
                CookTargetState.StartState.READY,
                CookTargetState.decideStart(true, true)
        );
    }

    @Test
    void waitsWhileMatchingWalkTargetIsStillActive() {
        assertEquals(
                CookTargetState.StartState.WAITING_FOR_PATH,
                CookTargetState.decideStart(true, false)
        );
    }

    @Test
    void waitsForRepathWhenPathTargetDisappearsOrChanges() {
        assertEquals(
                CookTargetState.StartState.WAITING_FOR_PATH,
                CookTargetState.decideStart(true, false)
        );
    }

    @Test
    void clearsInvalidOrDestroyedDeviceBeforeConsideringPath() {
        assertEquals(
                CookTargetState.StartState.CLEAR_INVALID,
                CookTargetState.decideStart(false, true)
        );
    }

    @Test
    void restoresMissingOrHijackedWalkTargetForValidDistantCooker() {
        assertTrue(CookTargetState.shouldRestoreWalkTarget(true, false, false));
    }

    @Test
    void doesNotOverwriteActivePathOrMovementAtInteractionRange() {
        assertFalse(CookTargetState.shouldRestoreWalkTarget(true, false, true));
        assertFalse(CookTargetState.shouldRestoreWalkTarget(true, true, false));
    }

    @Test
    void abandonsCachedApproachOnlyAfterBoundedRepathAttempts() {
        assertFalse(CookTargetState.shouldAbandonAfterRepaths(2, 3));
        assertTrue(CookTargetState.shouldAbandonAfterRepaths(3, 3));
    }

    @Test
    void clearsCookTargetWhenSwitchingToANonCookingTask() {
        assertTrue(CookTargetState.shouldClearForTask(true, false));
    }

    @Test
    void preservesCookTargetWhileTheCookingTaskRemainsSelected() {
        assertFalse(CookTargetState.shouldClearForTask(true, true));
    }
}
