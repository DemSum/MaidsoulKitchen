package com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;


/** Source: MSK 1.20.1-1.0-dev (58ec08ec), same relative class. Holder/RecipeInput and defensive component value copies are the only platform changes; beta consumers migrate in P2. */
public class CountIngredient extends RecIngredient {
    private final int count;

    protected CountIngredient(ItemStack stack) {
        super(Ingredient.of(stack));
        this.count = stack.getCount();
    }

    /** Source CountIngredient quantity test. Native BBQ 1.21 supplies ingredientCount/sideCount
     * separately from an Ingredient/tag. Preserve that predicate instead of choosing one variant;
     * source/beta descriptors omitted these native counts and could reserve insufficient inputs. */
    protected CountIngredient(Ingredient ingredient, int count) {
        super(ingredient);
        if (count <= 0) throw new IllegalArgumentException("Ingredient count must be positive");
        this.count = count;
    }

    @Override
    public int test(ItemStack itemStack) {
        return ingredient.test(itemStack) && itemStack.getCount() >= count ? count : 0;
    }
}
