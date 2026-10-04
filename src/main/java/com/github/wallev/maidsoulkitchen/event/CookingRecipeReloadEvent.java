package com.github.wallev.maidsoulkitchen.event;

import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.task.cook.common.task.CookTaskManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * 1.21 NeoForge boundary for upstream MaidCookManager.initTaskData, which cached recipes forever.
 * A server reload invalidates the catalog revision once; tick planning need not rescan the whole
 * catalog to discover reloads. Joining players do not invalidate server work. Replaces beta polling
 * of every Holder on every manager check; owns no work queue, inventory or per-maid execution state.
 */
@EventBusSubscriber(modid = MaidsoulKitchen.MOD_ID)
public final class CookingRecipeReloadEvent {
    @SubscribeEvent
    public static void onSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) CookTaskManager.recipesReloaded();
    }
}
