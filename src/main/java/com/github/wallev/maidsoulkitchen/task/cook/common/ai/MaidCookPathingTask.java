package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Predicate;

/** Keeps an assigned maid moving toward the cached reachable cooker approach. */
public final class MaidCookPathingTask extends Behavior<EntityMaid> {
    private static final int MAX_REPATH_ATTEMPTS = 3;
    private final Predicate<BlockEntity> isCookBlockEntity;
    private final double closeEnoughDistance;
    private final float movementSpeed;
    private BlockPos trackedWorkPos;
    private int repathAttempts;

    public <B extends BlockEntity, R extends Recipe<? extends RecipeInput>> MaidCookPathingTask(
            ICookTask<B, R> task
    ) {
        this(task::isCookBE, task.getCloseEnoughDist(), 0.5F);
    }

    public MaidCookPathingTask(
            Predicate<BlockEntity> isCookBlockEntity,
            double closeEnoughDistance,
            float movementSpeed
    ) {
        super(ImmutableMap.of(
                MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT,
                MkMemories.COOK_WALK_POS.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
        ));
        this.isCookBlockEntity = isCookBlockEntity;
        this.closeEnoughDistance = closeEnoughDistance;
        this.movementSpeed = movementSpeed;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        boolean validWorkTarget = CookTargetMemory.hasValidWorkTarget(level, maid, isCookBlockEntity);
        if (!validWorkTarget) {
            resetAttempts();
            CookTargetMemory.clear(maid);
            return false;
        }

        BlockPos workPos = CookTargetMemory.getWorkPos(maid)
                .orElseThrow()
                .currentBlockPosition();
        if (!workPos.equals(trackedWorkPos)) {
            trackedWorkPos = workPos;
            repathAttempts = 0;
        }

        boolean withinRange = CookTargetMemory.isWithinWorkRange(maid, closeEnoughDistance);
        if (withinRange) {
            resetAttempts();
            return false;
        }
        boolean walkTargetMatches = CookTargetMemory.hasMatchingWalkTarget(maid);
        return CookTargetState.shouldRestoreWalkTarget(
                true, withinRange, walkTargetMatches);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (CookTargetState.shouldAbandonAfterRepaths(
                repathAttempts, MAX_REPATH_ATTEMPTS)) {
            resetAttempts();
            CookTargetMemory.clear(maid);
            return;
        }
        if (!CookTargetMemory.restoreWalkTarget(maid, movementSpeed)) {
            resetAttempts();
            CookTargetMemory.clear(maid);
            return;
        }
        repathAttempts++;
    }

    private void resetAttempts() {
        trackedWorkPos = null;
        repathAttempts = 0;
    }
}
