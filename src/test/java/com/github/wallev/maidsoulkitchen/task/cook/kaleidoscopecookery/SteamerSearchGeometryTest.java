package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamerSearchGeometryTest {
    @Test
    void acceptsCardinalAndDiagonalSideOffsetsButNotTheWalkNode() {
        assertTrue(SteamerSearchGeometry.isSideOffset(1, 0));
        assertTrue(SteamerSearchGeometry.isSideOffset(-1, 1));
        assertFalse(SteamerSearchGeometry.isSideOffset(0, 0));
        assertFalse(SteamerSearchGeometry.isSideOffset(2, 0));
    }
}
