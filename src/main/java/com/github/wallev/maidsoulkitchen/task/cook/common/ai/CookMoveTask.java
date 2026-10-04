package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;

/** Source: 58ec08ec CookMoveTask.java (MIT). Be/Rule eligibility replaces beta task execution. Existing c9273ce5 single-BFS side search, round-robin devices, locks and floor recovery are retained verbatim. */
public class CookMoveTask<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 120;
    private static final int WORK_AREA_RECHECK_TICKS = 20;
    private final float movementSpeed;
    private final int verticalSearchRange;
    private final ICookTask<B, R> task;
    private final MaidCookManager<R> maidRecipesManager;
    private final com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase<B> cookBe;
    private final com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule<B, R> rule;
    private final CookTargetCycle targetCycle = new CookTargetCycle();
    private BlockPos pendingWorkAreaFloorAnchor;
    protected int verticalSearchStart;

    public CookMoveTask(ICookTask<B, R> task, MaidCookManager<R> maidRecipesManager, com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule<B, R> rule, com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase<B> cookBe) {
        this(task, 0.5f, cookBe.getVerticalSearchRange(), maidRecipesManager, rule, cookBe);
    }

    public CookMoveTask(ICookTask<B, R> task, float movementSpeed, int verticalSearchRange, MaidCookManager<R> maidRecipesManager, com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule<B, R> rule, com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase<B> cookBe) {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_ABSENT));
        this.task = task; this.cookBe = cookBe; this.rule = rule;
        this.movementSpeed = movementSpeed;
        this.verticalSearchRange = verticalSearchRange;
        this.setMaxCheckRate(MAX_DELAY_TIME);
        this.maidRecipesManager = maidRecipesManager;
        CookTargetMemory.clear(maidRecipesManager.getMaid());
    }

    private static BlockPos getSearchPos(EntityMaid maid) {
        return maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition().below();
    }

    public MaidCookManager<R> getMaidCookManager() {
        return maidRecipesManager;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        if (maid != this.maidRecipesManager.getMaid()) {
            return;
        }
        this.searchForDestination(worldIn, maid);
    }

    private boolean processRecipeManager() {
        if (!this.maidRecipesManager.checkAndInit()) return false;
        this.maidRecipesManager.checkAndCreateRecipes();
        return true;
    }

    @SuppressWarnings("unchecked")
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid maid, BlockPos blockPos) {
        BlockEntity blockEntity = worldIn.getBlockEntity(blockPos);
        if (blockEntity == null) {
            return false;
        }
        if (this.cookBe.isCookBe(blockEntity)) {
            cookBe.setBlockEntity(blockEntity);
            return rule.canMoveTo(cookBe, maidRecipesManager);
        }
        return false;
    }

    protected final void searchForDestination(ServerLevel worldIn, EntityMaid maid) {
        BlockPos centrePos = getSearchPos(maid);
        if (this.pendingWorkAreaFloorAnchor != null) {
            if (this.guideBackToWorkArea(
                    worldIn, maid, centrePos, this.pendingWorkAreaFloorAnchor)) return;
            this.pendingWorkAreaFloorAnchor = null;
        }
        if (!this.processRecipeManager()) {
            return;
        }
        int searchRange = (int) maid.getRestrictRadius();
        Optional<ReachableCookDeviceSearch.Result> result = ReachableCookDeviceSearch.find(
                worldIn,
                maid,
                centrePos,
                searchRange,
                this.verticalSearchStart,
                this.verticalSearchRange,
                pos -> CookWorkLocks.isAvailable(worldIn, pos, maid)
                        && shouldMoveTo(worldIn, maid, pos),
                targetCycle,
                cookBe.getInteractionHeightOffsets()
        );
        if (result.isEmpty()) {
            this.guideBackToWorkArea(worldIn, maid, centrePos, centrePos);
            return;
        }

        ReachableCookDeviceSearch.Result target = result.get();
        cookBe.setBlockEntity(worldIn.getBlockEntity(target.workPos()));
        if (this.guideBackToWorkArea(worldIn, maid, centrePos, cookBe.getWorkAreaFloorAnchor(target.walkPos()))) return;
        if (!CookWorkLocks.tryClaim(worldIn, target.workPos(), maid)) return;
        CookTargetMemory.remember(
                maid,
                target.walkPos(),
                target.workPos(),
                this.movementSpeed,
                0
        );
        targetCycle.recordSelection(target.workPos().asLong());
        this.setNextCheckTickCount(5);
    }

    private boolean guideBackToWorkArea(
            ServerLevel level,
            EntityMaid maid,
            BlockPos searchCenter,
            BlockPos floorAnchor
    ) {
        if (!CookTargetMemory.guideBackToWorkArea(
                level, maid, searchCenter, floorAnchor, this.movementSpeed)) return false;
        this.pendingWorkAreaFloorAnchor = floorAnchor.immutable();
        this.setNextCheckTickCount(WORK_AREA_RECHECK_TICKS);
        return true;
    }
}
