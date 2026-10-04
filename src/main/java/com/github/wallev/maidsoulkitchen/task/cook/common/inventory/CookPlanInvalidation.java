package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import java.util.List;
import java.util.Objects;

/** Pure change detection for cached recipe plans. */
public final class CookPlanInvalidation {
    private CookPlanInvalidation() {
    }

    public static boolean recipePlanChanged(
            String previousMode,
            List<String> previousRecipeIds,
            long previousRecipeFingerprint,
            String currentMode,
            List<String> currentRecipeIds,
            long currentRecipeFingerprint
    ) {
        return !Objects.equals(previousMode, currentMode)
                || !Objects.equals(previousRecipeIds, currentRecipeIds)
                || previousRecipeFingerprint != currentRecipeFingerprint;
    }

    public static boolean inventoryChanged(long previousFingerprint, long currentFingerprint) {
        return previousFingerprint != currentFingerprint;
    }
}
