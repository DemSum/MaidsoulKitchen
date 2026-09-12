package com.github.wallev.maidsoulkitchen.api.task.v1.cook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.IMaidsoulKitchenTask;

/** Marker for tasks that own the shared cooking walk/work coordinate memories. */
public interface ICookTargetTask extends IMaidsoulKitchenTask {
    /**
     * Cooking tasks search the whole work-point area themselves, so TLM's
     * generic idle stroll is neither needed for discovery nor safe around
     * machines. In particular, a large kappa-compass radius must not make a
     * maid leave the cooker while it is processing or between scan attempts.
     */
    @Override
    default boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }
}
