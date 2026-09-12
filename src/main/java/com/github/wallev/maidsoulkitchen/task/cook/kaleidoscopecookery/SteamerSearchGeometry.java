package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

final class SteamerSearchGeometry {
    private SteamerSearchGeometry() {
    }

    static boolean isSideOffset(int xOffset, int zOffset) {
        return Math.abs(xOffset) <= 1
                && Math.abs(zOffset) <= 1
                && (xOffset != 0 || zOffset != 0);
    }
}
