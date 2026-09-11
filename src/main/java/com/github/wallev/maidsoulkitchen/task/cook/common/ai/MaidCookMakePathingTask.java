package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.util.MemoryUtil;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Restores a lost walk target while a long-running cooking task still owns a
 * valid work position. Adapted from the official MSK 1.20.1 task.
 */
public class MaidCookMakePathingTask<B extends BlockEntity> extends Behavior<EntityMaid> {
    private static final float MOVEMENT_SPEED = 0.5F;
    private final ICookTask<B, ?> task;

    public MaidCookMakePathingTask(ICookTask<B, ?> task) {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.task = task;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return MemoryUtil.getWorkPos(maid)
                .map(pos -> maid.distanceToSqr(pos.currentPosition()) > Math.pow(task.getCloseEnoughDist(), 2))
                .orElse(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        MemoryUtil.getWorkPos(maid).ifPresent(posTracker -> {
            BlockPos workPos = posTracker.currentBlockPosition();
            BlockEntity blockEntity = level.getBlockEntity(workPos);
            if (blockEntity == null || !task.isCookBE(blockEntity)) {
                MemoryUtil.eraseWorkPos(maid);
                return;
            }
            B cookBlockEntity = (B) blockEntity;
            MemoryUtil.rememberWalkPos(maid, task.getWalkPos(cookBlockEntity), workPos, MOVEMENT_SPEED, 0);
        });
    }
}
