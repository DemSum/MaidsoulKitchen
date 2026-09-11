package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetState;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/** Waits while walking, then always invokes the steamer through its real work coordinate. */
final class MaidSteamerWorkTask extends Behavior<EntityMaid> {
    private static final double CLOSE_ENOUGH_DISTANCE = 2.5;

    MaidSteamerWorkTask() {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        BlockPos workPos = CookTargetMemory.getWorkPos(maid)
                .map(tracker -> tracker.currentBlockPosition())
                .orElse(null);
        if (workPos == null || !CookWorkLocks.tryClaim(level, workPos, maid)) {
            CookTargetMemory.clear(maid);
            return false;
        }

        CookTargetState.StartState state = CookTargetMemory.evaluateStart(
                level, maid, SteamerAdapter::supports, CLOSE_ENOUGH_DISTANCE);
        if (state == CookTargetState.StartState.CLEAR_INVALID) {
            CookWorkLocks.release(level, workPos, maid);
            CookTargetMemory.clear(maid);
        }
        return state == CookTargetState.StartState.READY;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        CookTargetMemory.getWorkPos(maid).ifPresent(tracker -> {
            BlockPos workPos = tracker.currentBlockPosition();
            try {
                TaskKcSteamer.workAt(maid, workPos);
            } finally {
                CookWorkLocks.release(level, workPos, maid);
                CookTargetMemory.clear(maid);
            }
        });
    }
}
