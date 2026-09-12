package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

/** Low-frequency idle movement anchored to the most recent cooker approach. */
public final class MaidCookIdleStrollTask extends MaidCheckRateTask {
    private static final int HORIZONTAL_RANGE = 3;
    private static final int VERTICAL_RANGE = 1;
    private static final int CANDIDATE_ATTEMPTS = 8;
    private static final int MIN_CHECK_INTERVAL_TICKS = 60;
    private static final float MOVEMENT_SPEED = 0.25F;

    private final BlockPos initialAnchor;

    public MaidCookIdleStrollTask(EntityMaid maid) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MkMemories.WORK_POS.get(), MemoryStatus.VALUE_ABSENT,
                MkMemories.COOK_WALK_POS.get(), MemoryStatus.REGISTERED
        ));
        this.initialAnchor = maid.blockPosition().immutable();
        setMaxCheckRate(MIN_CHECK_INTERVAL_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return super.checkExtraStartConditions(level, maid)
                && !maid.getSwimManager().isGoingToBreath();
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        BlockPos anchor = maid.getBrain().getMemory(MkMemories.COOK_WALK_POS.get())
                .map(PositionTracker::currentBlockPosition)
                .orElse(initialAnchor);

        if (!isInsideAnchor(maid.blockPosition(), anchor)) {
            if (maid.isWithinRestriction(anchor)) {
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(anchor, MOVEMENT_SPEED, 0)
                );
            }
            return;
        }

        Vec3 anchorCenter = Vec3.atBottomCenterOf(anchor);
        for (int attempt = 0; attempt < CANDIDATE_ATTEMPTS; attempt++) {
            Vec3 candidate = LandRandomPos.getPosTowards(
                    maid, HORIZONTAL_RANGE, VERTICAL_RANGE, anchorCenter);
            if (candidate == null) {
                continue;
            }
            BlockPos candidatePos = BlockPos.containing(candidate);
            if (!isInsideAnchor(candidatePos, anchor)
                    || !maid.isWithinRestriction(candidatePos)) {
                continue;
            }
            maid.getBrain().setMemory(
                    MemoryModuleType.WALK_TARGET,
                    new WalkTarget(candidate, MOVEMENT_SPEED, 0)
            );
            return;
        }
    }

    private static boolean isInsideAnchor(BlockPos pos, BlockPos anchor) {
        return CookTargetGeometry.isInsideIdleAnchor(
                pos.getX() - anchor.getX(),
                pos.getY() - anchor.getY(),
                pos.getZ() - anchor.getZ(),
                HORIZONTAL_RANGE,
                VERTICAL_RANGE
        );
    }
}
