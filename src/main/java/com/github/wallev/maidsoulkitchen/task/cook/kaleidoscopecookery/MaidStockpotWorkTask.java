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

final class MaidStockpotWorkTask extends Behavior<EntityMaid> {
    MaidStockpotWorkTask() { super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT)); }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        BlockPos work = CookTargetMemory.getWorkPos(maid).map(tracker -> tracker.currentBlockPosition()).orElse(null);
        if (work == null || !CookWorkLocks.tryClaim(level, work, maid)) {
            CookTargetMemory.clear(maid);
            return false;
        }
        var state = CookTargetMemory.evaluateStart(level, maid, StockpotAdapter::supports, 3.2);
        if (state == CookTargetState.StartState.CLEAR_INVALID) {
            CookWorkLocks.release(level, work, maid);
            CookTargetMemory.clear(maid);
        }
        return state == CookTargetState.StartState.READY;
    }
    @Override protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        CookTargetMemory.getWorkPos(maid).ifPresent(tracker -> {
            BlockPos work = tracker.currentBlockPosition();
            try { TaskKcStockpot.workAt(maid, work); }
            finally {
                CookWorkLocks.release(level, work, maid);
                CookTargetMemory.clear(maid);
            }
        });
    }
}
