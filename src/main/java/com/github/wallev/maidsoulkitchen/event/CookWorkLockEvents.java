package com.github.wallev.maidsoulkitchen.event;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class CookWorkLockEvents {
    private CookWorkLockEvents() {
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && event.getLevel() instanceof ServerLevel level) {
            CookWorkLocks.releaseAll(level, maid);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CookWorkLocks.clear(event.getServer());
    }
}
