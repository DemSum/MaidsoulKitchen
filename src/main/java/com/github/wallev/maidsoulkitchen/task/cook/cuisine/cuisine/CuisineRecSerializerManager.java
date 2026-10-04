package com.github.wallev.maidsoulkitchen.task.cook.cuisine.cuisine;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import dev.xkmc.cuisinedelight.content.recipe.BaseCuisineRecipe;
import dev.xkmc.cuisinedelight.content.recipe.CuisineRecipeMatch;
import dev.xkmc.cuisinedelight.init.registrate.CDItems;
import dev.xkmc.cuisinedelight.init.registrate.CDMisc;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import java.util.*;

/** Source: 58ec08ec CuisineRecSerializerManager (MIT), same native catalog, plate-first
 * ingredients and single-serving plans. Source discarded the reserved plate and matched only
 * a default tool definition. Use the existing upstream ToolRSM component-aware selection and
 * retain the plate as CONTAINER until native serving accepts it. Replaces beta task descriptors. */
public class CuisineRecSerializerManager extends ToolRecSerializerManager<BaseCuisineRecipe<?>> {
    private static final CuisineRecSerializerManager INSTANCE = new CuisineRecSerializerManager();
    protected CuisineRecSerializerManager() { super(CDMisc.RT_CUISINE.get()); }
    public static CuisineRecSerializerManager getInstance() { return INSTANCE; }
    @Override public String getRecipeTypeId() { return net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipeType).toString(); }
    @Override protected void initRecs(Level level, List<RecipeHolder<BaseCuisineRecipe<?>>> holders) {
        recipes = holders.stream().filter(holder -> !holder.id().toString().equals("cuisinedelight:suspicious_mix")).map(this::createMKRecipe).toList();
    }
    @Override protected List<MaidRec> createCookRec(MKRecipe<BaseCuisineRecipe<?>> r, ItemStack tool, Map<ItemDefinition, Long> available,
            boolean[] single, List<ItemDefinition> ingredients, Map<ItemDefinition, ItemAmount> uses, ResourceLocation task, long generation) {
        return super.createCookRec(r, tool, available, single, ingredients, uses, task, generation).stream().map(work -> {
            List<MaidItem> materials = new ArrayList<>(work.maidItems());
            MaidItem plate = materials.getFirst();
            materials.set(0, new MaidItem(plate.item(), plate.count(), MaidItem.Role.CONTAINER));
            return new MaidRec(work.recipe(), task, generation, work.time(), work.amount(), work.results(), materials, work.parameters());
        }).toList();
    }
    @Override protected ToolRecipeInfoProvider<BaseCuisineRecipe<?>> createRecipeInfoProvider() { return new CuisineRecipeInfoProvider(); }
    public static class CuisineRecipeInfoProvider extends ToolRecipeInfoProvider<BaseCuisineRecipe<?>> {
        @Override public RecIngredient getTool(RecSerializerManager<BaseCuisineRecipe<?>> rsm, BaseCuisineRecipe<?> recipe) { return RecIngredient.of(Ingredient.of(CDItems.SPATULA.get())); }
        @Override public List<RecIngredient> getIngredients(RecSerializerManager<BaseCuisineRecipe<?>> rsm, BaseCuisineRecipe<?> recipe) {
            List<RecIngredient> list = new ArrayList<>(); list.add(RecIngredient.of(Ingredient.of(CDItems.PLATE.get())));
            list.addAll(recipe.list.stream().map(CuisineRecipeMatch::ingredient).map(RecIngredient::of).toList()); return list;
        }
        @Override public boolean isSingle(RecSerializerManager<BaseCuisineRecipe<?>> rsm, BaseCuisineRecipe<?> recipe) { return true; }
    }
}
