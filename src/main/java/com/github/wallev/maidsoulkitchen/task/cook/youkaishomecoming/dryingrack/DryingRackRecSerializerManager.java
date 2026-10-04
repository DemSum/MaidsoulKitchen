package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.dryingrack;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackRecipe;
import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;

/** Source: 58ec08ec DryingRackRecSerializerManager.java (MIT), same recipe catalog and converter.
 * Registry-key access adapts the 1.21 YHC Val API; the four-position cap fixes the confirmed Be
 * excess-reservation defect. Replaces beta task-side live recipe/material lookup. */
@TaskClassAnalyzer(TaskInfo.YHC_DRYING_RACK)
public class DryingRackRecSerializerManager extends RecSerializerManager<DryingRackRecipe> {
    private static final DryingRackRecSerializerManager INSTANCE = new DryingRackRecSerializerManager();

    protected DryingRackRecSerializerManager() {
        super(YHBlocks.RACK_RT.get());
    }

    public static DryingRackRecSerializerManager getInstance() {
        return INSTANCE;
    }

    @Override
    public String getRecipeTypeId() {
        return net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(getRecipeType()).toString();
    }
    /** Source Be only has four native single-item positions; cap work before reservation, fixing early consumption of excess materials. */
    @Override protected int getMaxAmount(java.util.Map<com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition, Long> available, boolean[] single, java.util.Map<com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition, com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.ItemAmount> itemTimes, net.minecraft.resources.ResourceLocation taskId, long generation) {
        return Math.min(4, super.getMaxAmount(available, single, itemTimes, taskId, generation));
    }
}
