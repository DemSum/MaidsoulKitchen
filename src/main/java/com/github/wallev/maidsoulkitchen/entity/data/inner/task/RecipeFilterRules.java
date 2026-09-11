package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure collection rules shared by persistent data and network validation. */
public final class RecipeFilterRules {
    private RecipeFilterRules() {
    }

    public static <T> boolean allows(boolean whitelistMode, List<T> selected, T value) {
        return whitelistMode == selected.contains(value);
    }

    public static <T> List<T> toggle(List<T> selected, T value, Comparator<? super T> comparator) {
        List<T> changed = new ArrayList<>(selected);
        if (!changed.remove(value)) {
            changed.add(value);
        }
        changed.sort(comparator);
        return List.copyOf(changed);
    }

    public static <T> List<T> sanitize(
            List<T> values,
            Set<T> knownValues,
            Comparator<? super T> comparator
    ) {
        List<T> sanitized = new ArrayList<>(new LinkedHashSet<>(values));
        sanitized.removeIf(value -> !knownValues.contains(value));
        sanitized.sort(comparator);
        return List.copyOf(sanitized);
    }
}
