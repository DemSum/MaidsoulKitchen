package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Pure validation rules shared by serialized cooking data and server packet handling. */
public final class CookDataRules {
    public static final int MAX_FILTER_ENTRIES = 2048;
    public static final String WHITELIST = "whitelist";
    public static final String BLACKLIST = "blacklist";

    private CookDataRules() {
    }

    public static boolean isValidMode(String mode) {
        return WHITELIST.equals(mode) || BLACKLIST.equals(mode);
    }

    public static String normalizeMode(String mode) {
        return isValidMode(mode) ? mode : BLACKLIST;
    }

    public static List<String> normalizeRecipes(List<String> recipes) {
        if (recipes == null || recipes.isEmpty()) return new ArrayList<>();
        List<String> normalized = new ArrayList<>();
        for (String recipe : new LinkedHashSet<>(recipes)) {
            if (recipe == null || recipe.isBlank()) continue;
            normalized.add(recipe);
            if (normalized.size() == MAX_FILTER_ENTRIES) break;
        }
        return normalized;
    }
}
