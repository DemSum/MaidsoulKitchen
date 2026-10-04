package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import java.util.List;

public record RecipeOption(ResourceLocation id, ItemStack result, List<ResourceLocation> recipeIds) {
    public RecipeOption(ResourceLocation id, ItemStack result) { this(id, result, List.of(id)); }
    public RecipeOption {
        result = result.copy();
        recipeIds = List.copyOf(recipeIds);
    }

    public boolean selected(RecipeFilterData filter) { return recipeIds.stream().anyMatch(filter::contains); }
    public boolean allowed(RecipeFilterData filter) {
        return filter.mode() == RecipeFilterData.Mode.WHITELIST ? selected(filter) : !selected(filter);
    }
    public RecipeFilterData toggle(RecipeFilterData filter) {
        boolean wasSelected = selected(filter);
        for (ResourceLocation recipeId : recipeIds) {
            if (filter.contains(recipeId) == wasSelected) filter = filter.toggle(recipeId);
        }
        return filter;
    }

    @Override
    public ItemStack result() {
        return result.copy();
    }
}
