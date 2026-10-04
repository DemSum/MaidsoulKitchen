package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cuttingboard;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.ToolRecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

@TaskClassAnalyzer(TaskInfo.FD_CUTTING_BOARD)
/**
 * Source: 58ec08ec task/cook/farmersdelight/cuttingboard/CuttingBoardRecSerializerManager.java (MIT).
 * Direct port of the recipe descriptor/converter; Holder/RecipeInput retain 1.21 recipe identity.
 * P2 removes the corresponding beta getAmountIngredient path when the manager is switched.
 */
public class CuttingBoardRecSerializerManager extends ToolRecSerializerManager<CuttingBoardRecipe> {
    private static final CuttingBoardRecSerializerManager INSTANCE = new CuttingBoardRecSerializerManager();

    protected CuttingBoardRecSerializerManager() {
        super(ModRecipeTypes.CUTTING.get());
    }

    public static CuttingBoardRecSerializerManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected ToolRecipeInfoProvider<CuttingBoardRecipe> createRecipeInfoProvider() {
        return new CuttingBoardRecipeInfoProvider();
    }

    public static class CuttingBoardRecipeInfoProvider extends ToolRecipeInfoProvider<CuttingBoardRecipe> {
        @Override
        public RecIngredient getTool(RecSerializerManager<CuttingBoardRecipe> rsm, CuttingBoardRecipe rec) {
            return RecIngredient.of(rec.getTool());
        }
    }
}
