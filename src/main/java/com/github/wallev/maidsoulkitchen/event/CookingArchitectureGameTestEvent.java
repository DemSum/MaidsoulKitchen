package com.github.wallev.maidsoulkitchen.event;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.task.cook.common.task.CookingArchitectureGameTests;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CookingArchitectureGameTestEvent {
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        event.register(CookingArchitectureGameTests.class);
    }
}
