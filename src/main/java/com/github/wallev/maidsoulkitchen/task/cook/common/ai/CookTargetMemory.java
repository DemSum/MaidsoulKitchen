package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
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
        brain.setMemory(MkMemories.COOK_TASK_UID.get(), maid.getTask().getUid());
    }

    public static Optional<PositionTracker> getWorkPos(EntityMaid maid) {
        return maid.getBrain().getMemory(MkMemories.WORK_POS.get());
    }

    /**
     * Bridges schedule changes where TLM's spherical restriction considers an
     * adjacent floor "inside" the work area even though its cooking graph is
     * disconnected from the appliances below or above.
     */
    public static void guideBackToWorkArea(
            EntityMaid maid,
            BlockPos searchCenter,
            float speed
    ) {
        if (!maid.hasRestriction()
                || !maid.canBrainMoving()
                || !CookTargetGeometry.isDifferentFloorOffset(
                maid.blockPosition().getY() - searchCenter.getY())) {
            return;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(maid, searchCenter, speed, 3);
    }

    public static boolean hasValidWorkTarget(
            ServerLevel level,
            EntityMaid maid,
            Predicate<BlockEntity> isCookBlockEntity
    ) {
        Brain<EntityMaid> brain = maid.getBrain();
        Optional<PositionTracker> workTracker = brain.getMemory(MkMemories.WORK_POS.get());
        if (workTracker.isEmpty() || !isOwnedByCurrentTask(maid)) {
            return false;
        }

        BlockPos workPos = workTracker.get().currentBlockPosition();
        if (!hasMatchingDeviceMemories(brain, workPos) || !level.isLoaded(workPos)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(workPos);
        return blockEntity != null
                && isCookBlockEntity.test(blockEntity)
                && CookWorkLocks.tryClaim(level, workPos, maid);
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

    public static boolean hasCookingAssignment(EntityMaid maid) {
        Brain<EntityMaid> brain = maid.getBrain();
        return brain.hasMemoryValue(MkMemories.WORK_POS.get())
                || brain.hasMemoryValue(MkMemories.COOK_WALK_POS.get())
                || brain.hasMemoryValue(MkMemories.COOK_TASK_UID.get());
    }

    public static boolean isOwnedByCurrentTask(EntityMaid maid) {
        ResourceLocation currentUid = maid.getTask().getUid();
        return maid.getBrain().getMemory(MkMemories.COOK_TASK_UID.get())
                .filter(currentUid::equals)
                .isPresent();
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
        if (maid.level() instanceof ServerLevel level) {
            brain.getMemory(MkMemories.WORK_POS.get()).ifPresent(
                    tracker -> CookWorkLocks.release(level, tracker.currentBlockPosition(), maid));
        }
        if (hasMatchingWalkTarget(brain)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        brain.getMemory(MkMemories.WORK_POS.get()).ifPresent(workPos ->
                brain.getMemory(MemoryModuleType.LOOK_TARGET)
                        .filter(look -> look.currentBlockPosition().equals(workPos.currentBlockPosition()))
                        .ifPresent(ignored -> brain.eraseMemory(MemoryModuleType.LOOK_TARGET)));
        brain.eraseMemory(InitEntities.TARGET_POS.get());
        brain.eraseMemory(MkMemories.DESTROY_POS.get());
        brain.eraseMemory(MkMemories.WORK_POS.get());
        brain.eraseMemory(MkMemories.COOK_WALK_POS.get());
        brain.eraseMemory(MkMemories.COOK_TASK_UID.get());
    }
}
