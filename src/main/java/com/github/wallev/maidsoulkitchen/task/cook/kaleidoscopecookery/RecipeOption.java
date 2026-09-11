package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record RecipeOption(ResourceLocation id, ItemStack result) {
    public RecipeOption {
        result = result.copy();
    }

    @Override
    public ItemStack result() {
        return result.copy();
    }
}
