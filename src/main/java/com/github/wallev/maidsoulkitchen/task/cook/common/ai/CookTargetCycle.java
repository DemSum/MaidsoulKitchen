package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded per-maid rotation that prevents one actionable appliance monopolising a task. */
public final class CookTargetCycle {
    private static final int MAX_REMEMBERED_TARGETS = 64;
    private final Set<Long> visited = new LinkedHashSet<>();

    public boolean prefers(long packedWorkPos) {
        return !visited.contains(packedWorkPos);
    }

    public void recordSelection(long packedWorkPos) {
        if (visited.contains(packedWorkPos)) visited.clear();
        visited.add(packedWorkPos);
        while (visited.size() > MAX_REMEMBERED_TARGETS) {
            visited.remove(visited.iterator().next());
        }
    }

    int rememberedTargetCount() {
        return visited.size();
    }
}
