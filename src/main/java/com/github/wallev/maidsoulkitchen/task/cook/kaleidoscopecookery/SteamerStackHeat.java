package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import java.util.Objects;
import java.util.function.IntFunction;

/** Pure rules for Kaleidoscope Cookery's vertically propagated steamer heat. */
final class SteamerStackHeat {
    private SteamerStackHeat() {
    }

    static boolean reaches(
            int maxLitLevel,
            IntFunction<LayerState> layerAtDepth
    ) {
        if (maxLitLevel <= 0) {
            return false;
        }
        for (int depth = 0; depth < maxLitLevel; depth++) {
            LayerState layer = Objects.requireNonNull(layerAtDepth.apply(depth));
            if (layer == LayerState.NOT_STEAMER) {
                return false;
            }
            if (layer == LayerState.DIRECTLY_HEATED) {
                return true;
            }
        }
        return false;
    }

    enum LayerState {
        NOT_STEAMER,
        UNHEATED,
        DIRECTLY_HEATED
    }
}
