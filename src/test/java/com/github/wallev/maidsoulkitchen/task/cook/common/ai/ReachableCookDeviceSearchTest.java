package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachableCookDeviceSearchTest {
    @Test
    void acceptsOnlySameLevelCardinalNeighbors() {
        assertTrue(CookTargetGeometry.isHorizontalNeighbor(1, 0, 0));
        assertTrue(CookTargetGeometry.isHorizontalNeighbor(0, 0, -1));
        assertFalse(CookTargetGeometry.isHorizontalNeighbor(1, 0, 1));
        assertFalse(CookTargetGeometry.isHorizontalNeighbor(0, 1, 0));
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

    @Test
    void navigationBoundsAreCircularAndAllowStairHeightChanges() {
        assertTrue(CookTargetGeometry.isInsideNavigationBounds(
                6, 7, 8, 10, 7));
        assertFalse(CookTargetGeometry.isInsideNavigationBounds(
                7, 0, 8, 10, 7));
        assertFalse(CookTargetGeometry.isInsideNavigationBounds(
                0, 8, 0, 10, 7));
    }
}
