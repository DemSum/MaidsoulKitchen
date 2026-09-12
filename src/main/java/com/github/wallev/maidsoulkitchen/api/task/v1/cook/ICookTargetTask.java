package com.github.wallev.maidsoulkitchen.api.task.v1.cook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.IMaidsoulKitchenTask;

/** Marker for tasks that own the shared cooking walk/work coordinate memories. */
public interface ICookTargetTask extends IMaidsoulKitchenTask {
    /**
     * Cooking tasks search the whole work-point area themselves, so TLM's
     * unanchored idle stroll is disabled. Cooking schedules install their own
     * low-frequency stroll around the most recent safe cooker approach.
     */
    @Override
    default boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }
}
