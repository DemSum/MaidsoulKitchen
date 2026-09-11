package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Owns the coordinate contract for an MSK cooking assignment.
 *
 * <p>The work position is always the real cooking block entity. The cook walk
 * position is the reachable block where the maid stands. This follows the
 * dual-coordinate model from MaidsoulKitchen's {@code 1.20.1-1.0-dev} branch,
 * adapted for the reachable-side search used by the 1.21.1 fork.</p>
 */
public final class CookTargetMemory {
    private CookTargetMemory() {
    }

    public static void remember(
            EntityMaid maid,
            BlockPos walkPos,
            BlockPos workPos,
            float speed,
            int closeEnoughDistance
    ) {
        Brain<EntityMaid> brain = maid.getBrain();
        BlockPos immutableWalkPos = walkPos.immutable();
        BlockPos immutableWorkPos = workPos.immutable();

        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(immutableWalkPos, speed, closeEnoughDistance));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(immutableWorkPos));
        brain.setMemory(InitEntities.TARGET_POS.get(), new BlockPosTracker(immutableWorkPos));
        brain.setMemory(MkMemories.DESTROY_POS.get(), new BlockPosTracker(immutableWorkPos));
        brain.setMemory(MkMemories.WORK_POS.get(), new BlockPosTracker(immutableWorkPos));
        brain.setMemory(MkMemories.COOK_WALK_POS.get(), new BlockPosTracker(immutableWalkPos));
    }

    public static Optional<PositionTracker> getWorkPos(EntityMaid maid) {
        return maid.getBrain().getMemory(MkMemories.WORK_POS.get());
    }

    public static boolean hasValidWorkTarget(
            ServerLevel level,
            EntityMaid maid,
            Predicate<BlockEntity> isCookBlockEntity
    ) {
        Brain<EntityMaid> brain = maid.getBrain();
        Optional<PositionTracker> workTracker = brain.getMemory(MkMemories.WORK_POS.get());
        if (workTracker.isEmpty()) {
            return false;
        }

        BlockPos workPos = workTracker.get().currentBlockPosition();
        if (!hasMatchingDeviceMemories(brain, workPos) || !level.isLoaded(workPos)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(workPos);
        return blockEntity != null && isCookBlockEntity.test(blockEntity);
    }

    public static CookTargetState.StartState evaluateStart(
            ServerLevel level,
            EntityMaid maid,
            Predicate<BlockEntity> isCookBlockEntity,
            double closeEnoughDistance
    ) {
        if (!hasValidWorkTarget(level, maid, isCookBlockEntity)) {
            return CookTargetState.StartState.CLEAR_INVALID;
        }

        Brain<EntityMaid> brain = maid.getBrain();
        PositionTracker workTracker = brain.getMemory(MkMemories.WORK_POS.get()).orElseThrow();
        boolean withinRange = maid.distanceToSqr(workTracker.currentPosition())
                <= closeEnoughDistance * closeEnoughDistance;
        boolean walkTargetMatches = hasMatchingWalkTarget(brain);
        return CookTargetState.decideStart(true, withinRange, walkTargetMatches);
    }

    private static boolean hasMatchingDeviceMemories(Brain<EntityMaid> brain, BlockPos workPos) {
        return memoryMatches(brain, InitEntities.TARGET_POS.get(), workPos)
                && memoryMatches(brain, MkMemories.DESTROY_POS.get(), workPos)
                && brain.hasMemoryValue(MkMemories.COOK_WALK_POS.get());
    }

    private static boolean hasMatchingWalkTarget(Brain<EntityMaid> brain) {
        Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
        Optional<PositionTracker> rememberedWalkPos = brain.getMemory(MkMemories.COOK_WALK_POS.get());
        return walkTarget.isPresent()
                && rememberedWalkPos.isPresent()
                && walkTarget.get().getTarget().currentBlockPosition()
                .equals(rememberedWalkPos.get().currentBlockPosition());
    }

    private static boolean memoryMatches(
            Brain<EntityMaid> brain,
            MemoryModuleType<PositionTracker> memoryType,
            BlockPos expected
    ) {
        return brain.getMemory(memoryType)
                .map(PositionTracker::currentBlockPosition)
                .filter(expected::equals)
                .isPresent();
    }

    public static void clear(EntityMaid maid) {
        Brain<EntityMaid> brain = maid.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(InitEntities.TARGET_POS.get());
        brain.eraseMemory(MkMemories.DESTROY_POS.get());
        brain.eraseMemory(MkMemories.WORK_POS.get());
        brain.eraseMemory(MkMemories.COOK_WALK_POS.get());
    }
}
