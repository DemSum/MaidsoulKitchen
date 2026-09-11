package com.github.wallev.maidsoulkitchen.datagen;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskModClazzManager;
import com.github.wallev.maidsoulkitchen.util.DevUtil;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.nio.file.Path;

@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) throws Exception {
        if (!DevUtil.isDevEnv())
            return;

        // damages_burn.json is shipped as a main resource. Generating the same path
        // here makes the next processResources invocation fail on a duplicate entry.
        Path rootOutputFolder = event.getGenerator().rootOutputFolder;
        TaskModClazzManager.writeModTaskClazzFile(rootOutputFolder);
    }
}
