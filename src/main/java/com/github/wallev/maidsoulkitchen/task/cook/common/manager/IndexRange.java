package com.github.wallev.maidsoulkitchen.task.cook.common.manager;

/** Source: MSK 1.20.1-1.0-dev (58ec08ec), same relative class. Holder/RecipeInput and defensive component value copies are the only platform changes; beta consumers migrate in P2. */
public class IndexRange {
    private int start;
    private int end;

    public IndexRange() {
    }

    public void set(int start, int size) {
        this.start = start;
        this.end = start + size;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public void reset() {
        this.start = 0;
        this.end = 0;
    }
}
