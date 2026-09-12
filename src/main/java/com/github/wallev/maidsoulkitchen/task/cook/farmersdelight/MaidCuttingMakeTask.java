package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetState;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;

import java.util.Optional;

@TaskClassAnalyzer(TaskInfo.FD_CUTTING_BOARD)
public class MaidCuttingMakeTask extends Behavior<EntityMaid> {
    private final TaskFdCuttingBoard task;
    private final MaidRecipesManager<CuttingBoardRecipe> maidRecipesManager;
    private boolean maidHand = false;
    private int tick = 0;
    private Item processItem = null;

    public MaidCuttingMakeTask(TaskFdCuttingBoard task, MaidRecipesManager<CuttingBoardRecipe> maidRecipesManager) {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_PRESENT), 1200);
        this.task = task;
        this.maidRecipesManager = maidRecipesManager;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid maid) {
        CookTargetState.StartState state = CookTargetMemory.evaluateStart(
                worldIn, maid, task::isCookBE, task.getCloseEnoughDist());
        if (state == CookTargetState.StartState.CLEAR_INVALID) {
            CookTargetMemory.clear(maid);
        }
        return state == CookTargetState.StartState.READY;
    }

    @Override
    protected boolean canStillUse(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        return CookTargetMemory.hasValidWorkTarget(worldIn, maid, task::isCookBE)
                && ((!maid.getOffhandItem().isEmpty() && !maid.getMainHandItem().isEmpty())
                || isProcessItem(worldIn, maid));
    }

    private boolean isProcessItem(ServerLevel worldIn, EntityMaid maid) {
        Optional<PositionTracker> tracker = CookTargetMemory.getWorkPos(maid);

        if (tracker.isPresent()) {
            BlockEntity blockEntity = worldIn.getBlockEntity(tracker.get().currentBlockPosition());
            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoardBlockEntity) {
                return cuttingBoardBlockEntity.getStoredItem().is(this.processItem);
            }
        }

        return false;
    }

    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        super.start(worldIn, maid, pGameTime);
        CookTargetMemory.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockEntity blockEntity = worldIn.getBlockEntity(posWrapper.currentBlockPosition());
            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoardBlockEntity) {
                boolean continuingStoredItem = !cuttingBoardBlockEntity.getStoredItem().isEmpty();
                task.processCookMake(worldIn, maid, cuttingBoardBlockEntity, this.maidRecipesManager, (item) -> {
                    this.processItem = item;
                });
                if (continuingStoredItem && this.processItem != null) this.maidHand = true;
                this.maidRecipesManager.getCookInv().syncInv();
            }
        });
    }

    @Override
    protected void tick(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        if (tick++ % 5 != 0) return;
        CookTargetMemory.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockEntity blockEntity = worldIn.getBlockEntity(posWrapper.currentBlockPosition());
            if (blockEntity instanceof CuttingBoardBlockEntity cuttingBoardBlockEntity) {
                if (maidHand) {
                    ItemStack tool = maid.getMainHandItem();
                    cuttingBoardBlockEntity.processStoredItemUsingTool(tool, null);
                    maid.swing(InteractionHand.MAIN_HAND);
                } else {
                    ItemStack split = maid.getOffhandItem().split(1);
                    cuttingBoardBlockEntity.getInventory().insertItem(0, split, false);
                    maid.swing(InteractionHand.OFF_HAND);
                }

                maidHand = !maidHand;
            }
        });
    }

    @Override
    protected void stop(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        super.stop(worldIn, maid, pGameTime);
        CookTargetMemory.clear(maid);
        this.processItem = null;
        this.maidHand = false;
        this.tick = 0;
    }
}
