package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/** Restores the cached reachable approach after a long-running cook is displaced. */
public final class MaidCookPathingTask<
        B extends BlockEntity,
        R extends Recipe<? extends RecipeInput>
        > extends Behavior<EntityMaid> {
    private static final double REPATH_DISTANCE_SQUARED = 4.0;
    private static final float MOVEMENT_SPEED = 0.5f;
    private final ICookTask<B, R> task;

    public MaidCookPathingTask(ICookTask<B, R> task) {
        super(ImmutableMap.of(
                MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT,
                MkMemories.COOK_WALK_POS.get(), MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ));
        this.task = task;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (!CookTargetMemory.hasValidWorkTarget(level, maid, task::isCookBE)) {
            CookTargetMemory.clear(maid);
            return false;
        }
        return maid.getBrain().getMemory(MkMemories.COOK_WALK_POS.get())
                .map(PositionTracker::currentPosition)
                .map(pos -> maid.distanceToSqr(pos) > REPATH_DISTANCE_SQUARED)
                .orElse(false);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        Optional<PositionTracker> walkPos = maid.getBrain()
                .getMemory(MkMemories.COOK_WALK_POS.get());
        Optional<PositionTracker> workPos = CookTargetMemory.getWorkPos(maid);
        if (walkPos.isEmpty() || workPos.isEmpty()) {
            CookTargetMemory.clear(maid);
            return;
        }

        maid.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(walkPos.get().currentBlockPosition(), MOVEMENT_SPEED, 0)
        );
        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET,
                new BlockPosTracker(workPos.get().currentBlockPosition())
        );
    }
}
