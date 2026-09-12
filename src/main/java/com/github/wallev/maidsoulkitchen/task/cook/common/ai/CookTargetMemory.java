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

        boolean withinRange = isWithinWorkRange(maid, closeEnoughDistance);
        return CookTargetState.decideStart(true, withinRange);
    }

    private static boolean hasMatchingDeviceMemories(Brain<EntityMaid> brain, BlockPos workPos) {
        return memoryMatches(brain, InitEntities.TARGET_POS.get(), workPos)
                && memoryMatches(brain, MkMemories.DESTROY_POS.get(), workPos)
                && brain.hasMemoryValue(MkMemories.COOK_WALK_POS.get());
    }

    public static boolean isWithinWorkRange(EntityMaid maid, double closeEnoughDistance) {
        return getWorkPos(maid)
                .map(PositionTracker::currentPosition)
                .map(pos -> maid.distanceToSqr(pos) <= closeEnoughDistance * closeEnoughDistance)
                .orElse(false);
    }

    public static boolean hasMatchingWalkTarget(EntityMaid maid) {
        Brain<EntityMaid> brain = maid.getBrain();
        Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
        Optional<PositionTracker> rememberedWalkPos = brain.getMemory(MkMemories.COOK_WALK_POS.get());
        return walkTarget.isPresent()
                && rememberedWalkPos.isPresent()
                && walkTarget.get().getTarget().currentBlockPosition()
                .equals(rememberedWalkPos.get().currentBlockPosition());
    }

    public static boolean restoreWalkTarget(EntityMaid maid, float speed) {
        Optional<PositionTracker> walkPos = maid.getBrain().getMemory(MkMemories.COOK_WALK_POS.get());
        Optional<PositionTracker> workPos = getWorkPos(maid);
        if (walkPos.isEmpty() || workPos.isEmpty()) {
            return false;
        }

        maid.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(walkPos.get().currentBlockPosition(), speed, 0)
        );
        maid.getBrain().setMemory(
                MemoryModuleType.LOOK_TARGET,
                new BlockPosTracker(workPos.get().currentBlockPosition())
        );
        return true;
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
        clear(maid, true);
    }

    /** Clears the active assignment while retaining its safe side as an idle anchor. */
    public static void complete(EntityMaid maid) {
        clear(maid, false);
    }

    private static void clear(EntityMaid maid, boolean eraseWalkAnchor) {
        Brain<EntityMaid> brain = maid.getBrain();
        if (maid.level() instanceof ServerLevel level) {
            brain.getMemory(MkMemories.WORK_POS.get()).ifPresent(
                    tracker -> CookWorkLocks.release(level, tracker.currentBlockPosition(), maid));
        }
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(InitEntities.TARGET_POS.get());
        brain.eraseMemory(MkMemories.DESTROY_POS.get());
        brain.eraseMemory(MkMemories.WORK_POS.get());
        if (eraseWalkAnchor) {
            brain.eraseMemory(MkMemories.COOK_WALK_POS.get());
        }
    }
}
