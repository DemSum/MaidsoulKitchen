package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-maid, per-task rotation state for actionable cooking devices.
 *
 * <p>A target already used in the current cycle is a fallback rather than the
 * first choice. Once every currently actionable target has been seen, choosing
 * a fallback starts a new cycle. The bounded set prevents stale world positions
 * from accumulating when appliances are moved.</p>
 */
public final class CookTargetCycle {
    private static final int MAX_REMEMBERED_TARGETS = 64;
    private final Set<Long> visited = new LinkedHashSet<>();

    public boolean prefers(long packedWorkPos) {
        return !visited.contains(packedWorkPos);
    }

    public void recordSelection(long packedWorkPos) {
        if (visited.contains(packedWorkPos)) {
            visited.clear();
        }
        visited.add(packedWorkPos);
        while (visited.size() > MAX_REMEMBERED_TARGETS) {
            visited.remove(visited.iterator().next());
        }
    }

    int rememberedTargetCount() {
        return visited.size();
    }
}
