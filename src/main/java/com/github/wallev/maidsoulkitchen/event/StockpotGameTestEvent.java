package com.github.wallev.maidsoulkitchen.event;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.StockpotGameTests;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

/** Development-only test registration remains gated when KC is absent or incompatible. */
@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class StockpotGameTestEvent {
    private StockpotGameTestEvent() { }
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        if (TaskInfo.KC_STOCKPOT.canLoad()) event.register(StockpotGameTests.class);
    }
}
