package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.grill;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.mao.barbequesdelight.content.recipe.GrillingRecipe;
import com.mao.barbequesdelight.content.recipe.SimpleGrillingRecipe;
import com.mao.barbequesdelight.init.registrate.BBQDRecipes;

import java.util.List;

/** Source: 58ec08ec GrillingRecSerializerManager (MIT). Single native entry plans fix premature batch polling; Holder/RecipeInput adapted by the shared converter. Deletes beta task ingredient descriptors. */
@TaskClassAnalyzer(TaskInfo.BD_GRILL)
public class GrillingRecSerializerManager extends RecSerializerManager<GrillingRecipe<?>> {
    private static final GrillingRecSerializerManager INSTANCE = new GrillingRecSerializerManager();

    protected GrillingRecSerializerManager() {
        super(BBQDRecipes.RT_BBQ.get());
    }

    public static GrillingRecSerializerManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected RecipeInfoProvider<GrillingRecipe<?>> createRecipeInfoProvider() {
        return new GrillingRecipeInfoProvider();
    }

    public static class GrillingRecipeInfoProvider extends RecipeInfoProvider<GrillingRecipe<?>> {
        @Override public boolean isSingle(RecSerializerManager<GrillingRecipe<?>> rsm, GrillingRecipe<?> rec) { return true; }
        @Override
        public List<RecIngredient> getIngredients(RecSerializerManager<GrillingRecipe<?>> rsm, GrillingRecipe<?> rec) {
            return RecIngredient.from(List.of(((SimpleGrillingRecipe) rec).ingredient));
        }
    }
}
