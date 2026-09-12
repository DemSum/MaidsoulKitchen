package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachableCookDeviceSearchTest {
    @Test
    void distinguishesAdjacentFloorsFromNormalStepHeight() {
        assertFalse(CookTargetGeometry.isDifferentFloorOffset(2));
        assertTrue(CookTargetGeometry.isDifferentFloorOffset(3));
        assertTrue(CookTargetGeometry.isDifferentFloorOffset(-3));
    }

    @Test
    void acceptsCardinalDevicesAtFootOrCounterHeight() {
        assertTrue(CookTargetGeometry.isSideApproachOffset(1, 0, 0));
        assertTrue(CookTargetGeometry.isSideApproachOffset(0, 0, -1));
        assertTrue(CookTargetGeometry.isSideApproachOffset(1, 1, 0));
        assertFalse(CookTargetGeometry.isSideApproachOffset(1, -1, 0));
        assertFalse(CookTargetGeometry.isSideApproachOffset(1, 0, 1));
        assertFalse(CookTargetGeometry.isSideApproachOffset(0, 1, 0));
    }

    @Test
    void preservesTheOriginalAlternatingVerticalSearchOffsets() {
        assertTrue(CookTargetGeometry.isSearchedVerticalOffset(0, 0, 2));
        assertTrue(CookTargetGeometry.isSearchedVerticalOffset(1, 0, 2));
        assertTrue(CookTargetGeometry.isSearchedVerticalOffset(-1, 0, 2));
        assertTrue(CookTargetGeometry.isSearchedVerticalOffset(2, 0, 2));
        assertTrue(CookTargetGeometry.isSearchedVerticalOffset(-2, 0, 2));
        assertFalse(CookTargetGeometry.isSearchedVerticalOffset(3, 0, 2));
    }

    @Test
    void keepsDeviceCandidatesInsideOriginalHorizontalAndVerticalBounds() {
        assertTrue(CookTargetGeometry.isDeviceOffsetWithinSearchBounds(
                7, 0, -7, 8, 0, 2));
        assertTrue(CookTargetGeometry.isDeviceOffsetWithinSearchBounds(
                0, -2, 0, 8, 0, 2));
        assertFalse(CookTargetGeometry.isDeviceOffsetWithinSearchBounds(
                8, 0, 0, 8, 0, 2));
        assertFalse(CookTargetGeometry.isDeviceOffsetWithinSearchBounds(
                0, 3, 0, 8, 0, 2));
    }

}
