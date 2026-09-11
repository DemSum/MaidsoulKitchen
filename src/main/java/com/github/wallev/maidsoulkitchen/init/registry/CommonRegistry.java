package com.github.wallev.maidsoulkitchen.init.registry;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.Mods;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskModClazzManager;

import java.io.IOException;

public final class CommonRegistry {
    private static boolean initialized;

    private CommonRegistry() {
    }

    public static synchronized void mccInit() {
        if (initialized) {
            return;
        }
        Mods.init();
        TaskInfo.init();
        try {
            TaskModClazzManager.init();
            initialized = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
