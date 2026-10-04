package com.github.wallev.maidsoulkitchen.task.cook.common;

import com.github.wallev.maidsoulkitchen.api.task.v1.cook.IFdPotCook;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class TaskFdPot<B extends BlockEntity, R extends Recipe<? extends RecipeInput>> implements ICookTask<B, R>, IFdPotCook<B, R> {
    @Override
    public boolean shouldMoveTo(ServerLevel level, EntityMaid maid, B be, MaidCookManager<R> manager) {
        return maidShouldMoveTo(level, maid, be, manager);
    }

    @Override
    public void processCookMake(ServerLevel level, EntityMaid maid, B be, MaidCookManager<R> manager) {
        maidCookMake(level, maid, be, manager);
    }
}
