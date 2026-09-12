package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import org.junit.jupiter.api.Test;

import static com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.SteamerStackHeat.LayerState.DIRECTLY_HEATED;
import static com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.SteamerStackHeat.LayerState.NOT_STEAMER;
import static com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.SteamerStackHeat.LayerState.UNHEATED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SteamerStackHeatTest {
    @Test
    void reachesEveryLayerSupportedByKaleidoscopeCookery() {
        for (int steamerLayersBelow = 0; steamerLayersBelow < 4; steamerLayersBelow++) {
            int heatedDepth = steamerLayersBelow;
            assertTrue(SteamerStackHeat.reaches(4,
                    depth -> depth == heatedDepth ? DIRECTLY_HEATED : UNHEATED));
        }
    }

    @Test
    void rejectsStacksBeyondThePropagationLimit() {
        assertFalse(SteamerStackHeat.reaches(4,
                depth -> depth == 4 ? DIRECTLY_HEATED : UNHEATED));
    }

    @Test
    void rejectsBrokenOrUnheatedStacks() {
        assertFalse(SteamerStackHeat.reaches(4,
                depth -> depth == 1 ? NOT_STEAMER : depth == 2 ? DIRECTLY_HEATED : UNHEATED));
        assertFalse(SteamerStackHeat.reaches(4, depth -> UNHEATED));
    }

    @Test
    void searchesAllFourSupportedLayersFromAStandingNode() {
        assertArrayEquals(new int[]{0, 1, -1, 2, -2, 3, -3},
                SteamerStackHeat.interactionHeightOffsets(4));
    }
}
