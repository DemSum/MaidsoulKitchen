package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.crafting.Recipe;

/** Source: 58ec08ec task/cook/common/ai/GenerateRecsTask.java (MIT). Direct Minecraft Behavior replaces the version shim; manager state replaces duplicated memory flags. */
public class GenerateRecsTask<R extends Recipe<? extends RecipeInput>> extends Behavior<EntityMaid> {
    private final MaidCookManager<R> cm;

    public GenerateRecsTask(MaidCookManager<R> cm) {
        super(ImmutableMap.of());
        this.cm = cm;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel pLevel, EntityMaid pOwner) {
        return pOwner == cm.getMaid() && cm.getRunState() == 2;
    }
    @Override
    protected void start(ServerLevel pLevel, EntityMaid pEntity, long pGameTime) {
        cm.makeCollectIngredientsBubble();
    }

    @Override
    protected boolean canStillUse(ServerLevel pLevel, EntityMaid pEntity, long pGameTime) {
        return cm.getRunState() == 2 && !cm.recsGenerateDone();
    }
    @Override
    protected void tick(ServerLevel pLevel, EntityMaid pOwner, long pGameTime) {
        cm.tickGenerateRecs();
    }

    @Override
    protected void stop(ServerLevel pLevel, EntityMaid pEntity, long pGameTime) {

        if (cm.getRunState() == 2 && cm.recsGenerateDone()) cm.recsGenDoneAndUpdate();
    }

    @Override
    protected boolean timedOut(long pGameTime) {
        return false;
    }
}
