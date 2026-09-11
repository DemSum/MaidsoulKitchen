package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

final class SteamerSearchGeometry {
    private SteamerSearchGeometry() {
    }

    static boolean insideBounds(
            int xOffset,
            int yOffset,
            int zOffset,
            int horizontalRange,
            int verticalRange
    ) {
        return Math.abs(xOffset) <= horizontalRange
                && Math.abs(zOffset) <= horizontalRange
                && Math.abs(yOffset) <= verticalRange;
    }
}
