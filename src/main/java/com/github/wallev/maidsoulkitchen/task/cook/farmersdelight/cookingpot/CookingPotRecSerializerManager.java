package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

@TaskClassAnalyzer(TaskInfo.FD_COOK_POT)
/**
 * Source: 58ec08ec task/cook/farmersdelight/cookingpot/CookingPotRecSerializerManager.java (MIT).
 * Direct port of the recipe descriptor/converter; Holder/RecipeInput retain 1.21 recipe identity.
 * P2 removes the corresponding beta getAmountIngredient path when the manager is switched.
 */
public class CookingPotRecSerializerManager extends RecSerializerManager<CookingPotRecipe> {
    private static final CookingPotRecSerializerManager INSTANCE = new CookingPotRecSerializerManager();

    protected CookingPotRecSerializerManager() {
        super(ModRecipeTypes.COOKING.get());
    }

    public static CookingPotRecSerializerManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected RecipeInfoProvider<CookingPotRecipe> createRecipeInfoProvider() {
        return new CookingPotRecipeInfo();
    }

    public static class CookingPotRecipeInfo extends RecipeInfoProvider<CookingPotRecipe> {

        @Override
        public ItemStack getContainer(RecSerializerManager<CookingPotRecipe> rsm, CookingPotRecipe rec) {
            return rec.getOutputContainer();
        }
    }
}
