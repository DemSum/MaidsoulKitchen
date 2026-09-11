package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamerSearchGeometryTest {
    @Test
    void includesHorizontalBoundaryAndStairHeight() {
        assertTrue(SteamerSearchGeometry.insideBounds(8, 7, -8, 8, 7));
    }

    @Test
    void rejectsNodesOutsideHorizontalOrVerticalBounds() {
        assertFalse(SteamerSearchGeometry.insideBounds(9, 0, 0, 8, 7));
        assertFalse(SteamerSearchGeometry.insideBounds(0, 8, 0, 8, 7));
    }

    @Test
    void acceptsCardinalAndDiagonalSideOffsetsButNotTheWalkNode() {
        assertTrue(SteamerSearchGeometry.isSideOffset(1, 0));
        assertTrue(SteamerSearchGeometry.isSideOffset(-1, 1));
        assertFalse(SteamerSearchGeometry.isSideOffset(0, 0));
        assertFalse(SteamerSearchGeometry.isSideOffset(2, 0));
    }
}
