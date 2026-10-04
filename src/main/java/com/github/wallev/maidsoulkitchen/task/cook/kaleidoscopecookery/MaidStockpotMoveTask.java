package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetCycle;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.ReachableCookDeviceSearch;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/** Uses the existing single-device side search; never duplicates the steamer's layer search. */
final class MaidStockpotMoveTask extends MaidCheckRateTask {
    private static final float SPEED = 0.6F;
    private final CookTargetCycle cycle = new CookTargetCycle();
    MaidStockpotMoveTask() {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_ABSENT));
        setMaxCheckRate(120);
    }
    @Override protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        var storage = new StockpotWorkStorage(maid);
        try {
            storage.flush();
            var planner = new StockpotRecipePlanner(level, TaskKcStockpot.settings(maid), storage.available());
            BlockPos center = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition().below();
            var result = ReachableCookDeviceSearch.find(level, maid, center, (int) maid.searchRadius(), 0, 7,
                    pos -> CookWorkLocks.isAvailable(level, pos, maid)
                            && TaskKcStockpot.useful(level.getBlockEntity(pos), level, maid, storage, planner), cycle);
            if (result.isEmpty()) {
                CookTargetMemory.guideBackToWorkArea(level, maid, center, center, SPEED);
                return;
            }
            var target = result.get();
            if (CookTargetMemory.guideBackToWorkArea(level, maid, center, target.walkPos(), SPEED)) return;
            if (!CookWorkLocks.tryClaim(level, target.workPos(), maid)) return;
            CookTargetMemory.remember(maid, target.walkPos(), target.workPos(), SPEED, 0);
            cycle.recordSelection(target.workPos().asLong());
            setNextCheckTickCount(5);
        } finally { storage.sync(); }
    }
}
