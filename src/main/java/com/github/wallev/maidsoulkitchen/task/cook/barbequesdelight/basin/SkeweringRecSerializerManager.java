package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.basin;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.mao.barbequesdelight.content.recipe.SimpleSkeweringRecipe;
import com.mao.barbequesdelight.content.recipe.SkeweringRecipe;
import com.mao.barbequesdelight.init.registrate.BBQDRecipes;
import java.util.ArrayList;
import java.util.List;

/** Source: 58ec08ec SkeweringRecSerializerManager (MIT), same stick/ingredient/side order.
 * Native SimpleSkeweringRecipe consumes one stick plus ingredientCount and sideCount; source
 * omitted those counts. Retain native predicates/counts and single-serving units so assembly
 * cannot consume an unfulfilled batch. Replaces beta task descriptors and Pair materials. */
public class SkeweringRecSerializerManager extends RecSerializerManager<SkeweringRecipe<?>> {
    private static final SkeweringRecSerializerManager INSTANCE = new SkeweringRecSerializerManager();
    protected SkeweringRecSerializerManager() { super(BBQDRecipes.RT_SKR.get()); }
    public static SkeweringRecSerializerManager getInstance() { return INSTANCE; }
    @Override protected RecipeInfoProvider<SkeweringRecipe<?>> createRecipeInfoProvider() { return new SkeweringRecipeInfoProvider(); }
    public static class SkeweringRecipeInfoProvider extends RecipeInfoProvider<SkeweringRecipe<?>> {
        @Override public List<RecIngredient> getIngredients(RecSerializerManager<SkeweringRecipe<?>> rsm, SkeweringRecipe<?> rec) {
            var recipe = (SimpleSkeweringRecipe) rec;
            List<RecIngredient> list = new ArrayList<>(); list.add(RecIngredient.of(recipe.tool));
            list.add(RecIngredient.ofCount(recipe.ingredient, recipe.ingredientCount));
            if (!recipe.side.isEmpty()) list.add(RecIngredient.ofCount(recipe.side, recipe.sideCount));
            return list;
        }
        @Override public boolean isSingle(RecSerializerManager<SkeweringRecipe<?>> rsm, SkeweringRecipe<?> rec) { return true; }
    }
}
