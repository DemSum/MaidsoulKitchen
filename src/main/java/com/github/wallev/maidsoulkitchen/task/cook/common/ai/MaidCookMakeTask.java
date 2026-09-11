package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.github.wallev.maidsoulkitchen.util.MemoryUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

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
        Brain<EntityMaid> brain = maid.getBrain();
        return MemoryUtil.getWorkPos(maid).map(targetPos -> {
            Vec3 targetV3d = targetPos.currentPosition();
            if (maid.distanceToSqr(targetV3d) > Math.pow(task.getCloseEnoughDist(), 2)) {
                Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
                if (walkTarget.isEmpty()) {
                    MemoryUtil.eraseWorkPos(maid);
                }
                return false;
            }
            return true;
        }).orElse(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void start(ServerLevel worldIn, EntityMaid maid, long pGameTime) {
        if (maid != this.maidRecipesManager.getMaid()) {
            MemoryUtil.eraseWorkPos(maid);
            return;
        }
        MemoryUtil.getWorkPos(maid).ifPresent(posWrapper -> {
            BlockPos basePos = posWrapper.currentBlockPosition();
            BlockEntity blockEntity = worldIn.getBlockEntity(basePos);
            if (blockEntity != null && task.isCookBE(blockEntity)) {
                this.task.processCookMake(worldIn, maid, (B) blockEntity, this.maidRecipesManager);
                this.maidRecipesManager.syncInv();
                this.maidRecipesManager.tranOutput2Chest();
                this.maidRecipesManager.syncInv();
            }
            MemoryUtil.eraseWorkPos(maid);
        });
    }
}
