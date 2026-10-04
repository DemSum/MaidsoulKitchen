package com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.stream.Collectors;


/** Source: MSK 1.20.1-1.0-dev (58ec08ec), same relative class. Holder/RecipeInput and defensive component value copies are the only platform changes; beta consumers migrate in P2. */
public class RecIngredient {
    public static final RecIngredient EMPTY = of(Ingredient.EMPTY);
    public final Ingredient ingredient;
    protected RecIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

    public int test(ItemStack stack) {
        return ingredient.test(stack) ? 1 : 0;
    }

    public static List<RecIngredient> from(List<Ingredient> ingredients) {
        return ingredients.stream().map(RecIngredient::of).collect(Collectors.toList());
    }

    public static CountIngredient ofCount(ItemStack itemStack) {
        return new CountIngredient(itemStack);
    }

    public static RecIngredient of(Ingredient ingredient) {
        return new RecIngredient(ingredient);
    }

    public boolean isEmpty() {
        return ingredient.isEmpty();
    }
}
