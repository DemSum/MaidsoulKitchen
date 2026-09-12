package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

/** Pure state transition logic for starting or clearing a cooking assignment. */
public final class CookTargetState {
    private CookTargetState() {
    }

    public enum StartState {
        READY,
        WAITING_FOR_PATH,
        CLEAR_INVALID
    }

    public static StartState decideStart(
            boolean validWorkTarget,
            boolean withinRange
    ) {
        if (!validWorkTarget) {
            return StartState.CLEAR_INVALID;
        }
        if (withinRange) {
            return StartState.READY;
        }
        return StartState.WAITING_FOR_PATH;
    }

    public static boolean shouldRestoreWalkTarget(
            boolean validWorkTarget,
            boolean withinRange,
            boolean walkTargetMatches
    ) {
        return validWorkTarget && !withinRange && !walkTargetMatches;
    }

    public static boolean shouldAbandonAfterRepaths(int completedAttempts, int maxAttempts) {
        return maxAttempts > 0 && completedAttempts >= maxAttempts;
    }

    public static boolean shouldClearForTask(boolean hasCookTarget, boolean currentTaskIsCook) {
        return hasCookTarget && !currentTaskIsCook;
    }
}
