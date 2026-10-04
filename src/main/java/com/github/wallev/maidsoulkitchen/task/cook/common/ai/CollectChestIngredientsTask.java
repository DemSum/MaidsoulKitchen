package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.crafting.Recipe;

/** Source: 58ec08ec task/cook/common/ai/CollectChestIngredientsTask.java (MIT). Direct Minecraft Behavior replaces the version shim; manager state replaces duplicated memory flags. */
public class CollectChestIngredientsTask<R extends Recipe<? extends RecipeInput>> extends Behavior<EntityMaid> {
    private final MaidCookManager<R> rm;
    public CollectChestIngredientsTask(MaidCookManager<R> rm) {
        super(ImmutableMap.of());
        this.rm = rm;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel pLevel, EntityMaid pOwner) {
        return pOwner == rm.getMaid() && rm.getRunState() == 1;
    }
    @Override
    protected void start(ServerLevel pLevel, EntityMaid pEntity, long pGameTime) {
        rm.makeCollectIngredientsBubble();
    }

    @Override
    protected boolean canStillUse(ServerLevel pLevel, EntityMaid pEntity, long pGameTime) {
        return rm.getRunState() == 1 && !rm.getChestInputInventory().done();
    }
    @Override
    protected void tick(ServerLevel pLevel, EntityMaid pOwner, long pGameTime) {
        if (rm.checkAndInit() && rm.getRunState() == 1) rm.getChestInputInventory().tickScan();
    }

    @Override
    protected void stop(ServerLevel pLevel, EntityMaid pEntity, long pGameTime) {

        if (rm.getRunState() == 1 && rm.getChestInputInventory().done()) rm.startGenerateRecs();
    }

    @Override
    protected boolean timedOut(long pGameTime) {
        return false;
    }
}
