package com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Source: 58ec08ec task/cook/common/rule/rec/ToolRecSerializerManager.java (MIT).
 * Direct port of the recipe descriptor/converter; Holder/RecipeInput retain 1.21 recipe identity.
 * P2 removes the corresponding beta getAmountIngredient path when the manager is switched.
 */
public abstract class ToolRecSerializerManager<R extends Recipe<? extends RecipeInput>> extends RecSerializerManager<R> {
    protected ToolRecSerializerManager(RecipeType<R> recipeType) {
        super(recipeType);
    }

    @Override
    protected MKRecipe<R> createMKRecipe(RecipeHolder<R> holder) {
        R r = holder.value();
        List<RecIngredient> ingredients = recipeInfoProvider.getIngredients(this, r);
        ItemStack output = recipeInfoProvider.getOutput(this, r);
        ItemStack container = recipeInfoProvider.getContainer(this, r);
        boolean single = recipeInfoProvider.isSingle(this, r);
        RecIngredient tool = ((ToolRecipeInfoProvider<R>) recipeInfoProvider).getTool(this, r);
        return new MKRecipe<>(holder, single, tool, ingredients, output, container);
    }

    @Override
    protected abstract ToolRecipeInfoProvider<R> createRecipeInfoProvider();

    @Override
    protected List<MaidRec> recProcess(MKRecipe<R> r, Map<ItemDefinition, Long> available, List<ItemDefinition> invIngredient, boolean[] single, Map<ItemDefinition, ItemAmount> itemTimes, ResourceLocation taskId, long generation) {
        ItemStack tool = processTool(r, available, invIngredient, single, itemTimes);
        if (tool.isEmpty()) {
            return Collections.emptyList();
        }

        boolean processRecIngres = processRecIngres(r, available, invIngredient, single, itemTimes, taskId, generation);
        if (!processRecIngres) {
            return Collections.emptyList();
        }

        return createCookRec(r, tool, available, single, invIngredient, itemTimes, taskId, generation);
    }

    protected ItemStack processTool(MKRecipe<R> r, Map<ItemDefinition, Long> available, List<ItemDefinition> invIngredient, boolean[] single, Map<ItemDefinition, ItemAmount> itemTimes) {
        RecIngredient tool = r.tool();
        if (tool.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (ItemDefinition item : available.keySet()) {
            if (available.get(item) > 0 && tool.test(item.stack()) > 0) {
                // Upstream lost tool components and omitted it from the hub transfer reservation.
                ItemAmount toolUse = new ItemAmount(1);
                toolUse.setTool(true);
                itemTimes.put(item, toolUse);
                return item.stack();
            }
        }
        return ItemStack.EMPTY;
    }

    public static abstract class ToolRecipeInfoProvider<R extends Recipe<? extends RecipeInput>> extends RecipeInfoProvider<R> {
        public abstract RecIngredient getTool(RecSerializerManager<R> rsm, R rec);
    }
}
