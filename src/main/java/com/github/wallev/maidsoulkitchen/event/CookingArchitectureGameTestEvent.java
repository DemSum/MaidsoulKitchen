package com.github.wallev.maidsoulkitchen.event;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.task.CookingArchitectureGameTests;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CookingArchitectureGameTestEvent {
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        // Reuse the existing appliance compatibility boundary: NeoForge reflects all helper
        // signatures during ordinary server startup, even with GameTests disabled. This native
        // integration suite needs every referenced addon; 1.20.1 had no such registration hook.
        if (java.util.stream.Stream.of(TaskInfo.FD_COOK_POT, TaskInfo.DB_BEER,
                TaskInfo.YHC_MOKA, TaskInfo.YHC_TEA_KETTLE, TaskInfo.YHC_DRYING_RACK,
                TaskInfo.YHC_FERMENTATION_TANK, TaskInfo.CD_CUISINE_SKILLET,
                TaskInfo.BD_BASIN, TaskInfo.BD_GRILL, TaskInfo.KC_STEAMER)
                .allMatch(TaskInfo::canLoad)) event.register(CookingArchitectureGameTests.class);
    }
}
