package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MaidCookMakeTask<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> extends Behavior<EntityMaid> {
    private final ICookTask<B, R> task;
    private final MaidRecipesManager<R> maidRecipesManager;

    public MaidCookMakeTask(ICookTask<B, R> task,MaidRecipesManager<R> maidRecipesManager) {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT));
        this.task = task;
        this.maidRecipesManager = maidRecipesManager;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        if (maid != this.maidRecipesManager.getMaid()) {
            CookTargetMemory.clear(maid);
            return false;
        }
        CookTargetState.StartState state = CookTargetMemory.evaluateStart(
                worldIn, maid, task::isCookBE, task.getCloseEnoughDist());
        if (CookSearchDiagnostics.enabled()) {
            BlockPos debugWorkPos = CookTargetMemory.getWorkPos(maid)
                    .map(pos -> pos.currentBlockPosition()).orElse(maid.blockPosition());
            CookSearchDiagnostics.assignmentState(maid, task.getUid(), state, debugWorkPos);
        }
        if (state == CookTargetState.StartState.CLEAR_INVALID) {
            CookTargetMemory.clear(maid);
            CookSearchDiagnostics.assignmentCleared(maid);
        }
        return state == CookTargetState.StartState.READY;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void start(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        if (maid != this.maidRecipesManager.getMaid()) {
            return;
        }
        CookTargetMemory.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockPos basePos = posWrapper.currentBlockPosition();
            BlockEntity blockEntity = worldIn.getBlockEntity(basePos);
            if (blockEntity != null && task.isCookBE(blockEntity)) {
                int inputBefore = CookSearchDiagnostics.enabled()
                        ? CookSearchDiagnostics.totalItems(this.maidRecipesManager.getInputInv()) : -1;
                int outputBefore = CookSearchDiagnostics.enabled()
                        ? CookSearchDiagnostics.totalItems(this.maidRecipesManager.getOutputInv()) : -1;
                CookSearchDiagnostics.makeStarted(
                        maid, task.getUid(), basePos, inputBefore, outputBefore);
                this.task.processCookMake(worldIn, maid, (B) blockEntity, this.maidRecipesManager);
                this.maidRecipesManager.getCookInv().syncInv();
                int inputAfter = CookSearchDiagnostics.enabled()
                        ? CookSearchDiagnostics.totalItems(this.maidRecipesManager.getInputInv()) : -1;
                int outputAfterProcess = CookSearchDiagnostics.enabled()
                        ? CookSearchDiagnostics.totalItems(this.maidRecipesManager.getOutputInv()) : -1;
                this.maidRecipesManager.tranOutput2Chest();
                this.maidRecipesManager.getCookInv().syncInv();
                int outputAfterStorage = CookSearchDiagnostics.enabled()
                        ? CookSearchDiagnostics.totalItems(this.maidRecipesManager.getOutputInv()) : -1;
                CookSearchDiagnostics.makeCompleted(
                        maid, task.getUid(), basePos, inputBefore, inputAfter,
                        outputBefore, outputAfterProcess, outputAfterStorage);
            }
            CookTargetMemory.clear(maid);
            CookSearchDiagnostics.assignmentCleared(maid);
        });
    }
}
