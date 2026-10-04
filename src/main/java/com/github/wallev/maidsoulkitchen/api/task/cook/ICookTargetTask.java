package com.github.wallev.maidsoulkitchen.api.task.cook;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.IMaidsoulKitchenTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

/** Marker for tasks that own the shared cooking walk/work coordinate memories. */
public interface ICookTargetTask extends IMaidsoulKitchenTask {
    static boolean isWithinOwnerRange(EntityMaid maid, BlockPos pos) {
        if (maid.isHomeModeEnable()) {
            return true;
        }
        LivingEntity owner = maid.getOwner();
        return owner != null && pos.closerToCenterThan(owner.position(), 8.0);
    }

    /**
     * Keep TLM's idle look/random-walk behavior out of an active cooking
     * assignment. This is the official 1.20.1 task-level contract, shared by
     * both generic cookers and the native steamer task.
     */
    @Override
    default boolean enableLookAndRandomWalk(EntityMaid maid) {
        return !maid.getBrain().hasMemoryValue(MkMemories.WORK_POS.get());
    }

    @Override
    default boolean enableEating(EntityMaid maid) {
        return !maid.getBrain().hasMemoryValue(MkMemories.WORK_POS.get());
    }
}
