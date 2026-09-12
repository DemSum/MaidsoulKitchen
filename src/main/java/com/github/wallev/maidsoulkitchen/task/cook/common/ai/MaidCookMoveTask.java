package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MaidCookMoveTask<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> extends MaidCheckRateTask {
    private static final int MAX_DELAY_TIME = 120;
    private final float movementSpeed;
    private final int verticalSearchRange;
    private final ICookTask<B, R> task;
    private final MaidRecipesManager<R> maidRecipesManager;
    protected int verticalSearchStart;

    public MaidCookMoveTask(ICookTask<B, R> task, MaidRecipesManager<R> maidRecipesManager) {
        this(task, 0.5f, 2, maidRecipesManager);
    }

    public MaidCookMoveTask(ICookTask<B, R> task, float movementSpeed, int verticalSearchRange, MaidRecipesManager<R> maidRecipesManager) {
        // Do not gate the scan on WALK_TARGET. TLM's low-priority idle stroll
        // owns that memory while the maid is between jobs; making it a required
        // absence also freezes MaidCheckRateTask's cooldown and can starve cook
        // discovery indefinitely. The existing 120-239 tick cooldown still
        // limits BFS work, and remember(...) only replaces the walk target after
        // an actionable, reachable appliance has actually been found.
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_ABSENT));
        this.task = task;
        this.movementSpeed = movementSpeed;
        this.verticalSearchRange = verticalSearchRange;
        this.setMaxCheckRate(MAX_DELAY_TIME);
        this.maidRecipesManager = maidRecipesManager;
        CookTargetMemory.clear(maidRecipesManager.getMaid());
    }

    private static BlockPos getSearchPos(EntityMaid maid) {
        return maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition().below();
    }

    public MaidRecipesManager<R> getMaidRecipesManager() {
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
        return this.maidRecipesManager.checkAndCreateRecipesIngredients();
    }

    @SuppressWarnings("unchecked")
    protected boolean shouldMoveTo(ServerLevel worldIn, EntityMaid maid, BlockPos blockPos) {
        BlockEntity blockEntity = worldIn.getBlockEntity(blockPos);
        if (blockEntity == null) {
            return false;
        }
        if (this.task.isCookBE(blockEntity)) {
            boolean processed = this.processRecipeManager();
            if (!processed) return false;
            return this.task.shouldMoveTo(worldIn, this.maidRecipesManager.getMaid(), (B) blockEntity, maidRecipesManager);
        }
        return false;
    }

    protected final void searchForDestination(ServerLevel worldIn, EntityMaid maid) {
        BlockPos centrePos = getSearchPos(maid);
        int searchRange = (int) maid.getRestrictRadius();
        ReachableCookDeviceSearch.find(
                worldIn,
                maid,
                centrePos,
                searchRange,
                this.verticalSearchStart,
                this.verticalSearchRange,
                pos -> shouldMoveTo(worldIn, maid, pos)
        ).ifPresent(result -> {
            CookTargetMemory.remember(
                    maid,
                    result.walkPos(),
                    result.workPos(),
                    this.movementSpeed,
                    0
            );
            this.setNextCheckTickCount(5);
        });
    }
}
