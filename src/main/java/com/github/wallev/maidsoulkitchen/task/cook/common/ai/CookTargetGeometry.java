package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

/** Pure coordinate checks shared by the reachable-device search and its tests. */
public final class CookTargetGeometry {
    private static final int SAME_FLOOR_VERTICAL_RANGE = 2;

    private CookTargetGeometry() {
    }

    public static boolean isDifferentFloorOffset(int deltaY) {
        return Math.abs(deltaY) > SAME_FLOOR_VERTICAL_RANGE;
    }

    public static boolean isSideApproachOffset(int deltaX, int deltaY, int deltaZ) {
        return (deltaY == 0 || deltaY == 1)
                && Math.abs(deltaX) + Math.abs(deltaZ) == 1;
    }

    public static boolean isSearchedVerticalOffset(int offset, int start, int range) {
        for (int y = start; y <= range; y = y > 0 ? -y : 1 - y) {
            if (y == offset) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDeviceOffsetWithinSearchBounds(
            int deltaX,
            int relativeY,
            int deltaZ,
            int searchRange,
            int verticalSearchStart,
            int verticalSearchRange
    ) {
        return isSearchedVerticalOffset(relativeY, verticalSearchStart, verticalSearchRange)
                && Math.abs(deltaX) < searchRange
                && Math.abs(deltaZ) < searchRange;
    }

}
