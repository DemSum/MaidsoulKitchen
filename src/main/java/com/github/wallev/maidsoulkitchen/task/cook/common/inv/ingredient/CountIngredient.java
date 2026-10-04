package com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;


/** Source: MSK 1.20.1-1.0-dev (58ec08ec), same relative class. Holder/RecipeInput and defensive component value copies are the only platform changes; beta consumers migrate in P2. */
public class CountIngredient extends RecIngredient {
    private final ItemStack stack;

    protected CountIngredient(ItemStack stack) {
        super(Ingredient.of(stack));
        this.stack = stack.copy();
    }

    @Override
    public int test(ItemStack itemStack) {
        return ingredient.test(itemStack) && itemStack.getCount() >= stack.getCount() ? stack.getCount() : 0;
    }
}
