package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.List;

/** Source: 58ec08ec DryingRackRecSerializerManager (MIT). KC's native SingleItemRecipe
 * metadata and one-slot acceptance are the only new-device boundary; the upstream converter
 * reserves component-aware MaidRec units in the one manager queue, replacing task-side lookup. */
public final class SteamerRecSerializerManager extends RecSerializerManager<SteamerRecipe> {
    public static final SteamerRecSerializerManager INSTANCE = new SteamerRecSerializerManager();
    private SteamerRecSerializerManager() { super(ModRecipes.STEAMER_RECIPE); }
    @Override protected MKRecipe<SteamerRecipe> createMKRecipe(RecipeHolder<SteamerRecipe> holder) {
        return new MKRecipe<>(holder, true, List.of(RecIngredient.of(holder.value().getIngredient())), holder.value().getResult());
    }
}
