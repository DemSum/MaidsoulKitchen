package com.github.wallev.maidsoulkitchen.init.registry;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.CompatibilityRegistry;

public final class CommonRegistry {
    private static boolean initialized;

    private CommonRegistry() {
    }

    public static synchronized void mccInit() {
        if (initialized) {
            return;
        }
        CompatibilityRegistry.initialize();
        initialized = true;
    }
}
