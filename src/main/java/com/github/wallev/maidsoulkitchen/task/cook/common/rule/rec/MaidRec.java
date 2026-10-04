package com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Source: 58ec08ec task/cook/common/rule/rec/MaidRec.java (MIT).
 * Recipe.getId() moved to RecipeHolder in 1.21; task UID and reload generation prevent stale execution.
 * The upstream CODEC decodes a null Recipe, so settings are persisted and runtime plans are rebuilt instead.
 * Value copies fix mutable result/material aliases. Multiple results retain native cutting/stockpot semantics.
 * P2 replaces beta Pair + plannedResults ownership with this work unit; this class owns no queue.
 * Runtime only: persisted settings rebuild plans after load; there is deliberately no null-recipe CODEC.
 */
public final class MaidRec {
    private final RecipeHolder<?> recipe;
    private final ResourceLocation taskId;
    private final long generation;
    private final int time;
    private final int amount;
    private final List<ItemStack> results;
    private final List<MaidItem> maidItems;
    private final Map<String, Integer> parameters;

    public MaidRec(RecipeHolder<?> recipe, ResourceLocation taskId, long generation,
                   int time, int amount, List<ItemStack> results, List<MaidItem> maidItems,
                   Map<String, Integer> parameters) {
        this.recipe = Objects.requireNonNull(recipe);
        this.taskId = Objects.requireNonNull(taskId);
        if (amount <= 0 || time < 0) throw new IllegalArgumentException("Invalid work quantity or time");
        this.generation = generation;
        this.time = time;
        this.amount = amount;
        this.results = results.stream().map(ItemStack::copy).toList();
        this.maidItems = List.copyOf(maidItems);
        this.parameters = Map.copyOf(parameters);
    }

    public ResourceLocation recipeId() { return recipe.id(); }
    public RecipeType<?> recipeType() { return recipe.value().getType(); }
    public ResourceLocation taskId() { return taskId; }
    public RecipeHolder<?> recipe() { return recipe; }
    public long generation() { return generation; }
    public int time() { return time; }
    public int amount() { return amount; }
    public ItemStack result() { return results.isEmpty() ? ItemStack.EMPTY : results.getFirst().copy(); }
    public List<ItemStack> results() { return results.stream().map(ItemStack::copy).toList(); }
    public List<MaidItem> maidItems() { return maidItems; }
    public Map<String, Integer> parameters() { return parameters; }
    public Optional<RecipeHolder<?>> resolve(RecipeManager manager, ResourceLocation task, long currentGeneration) {
        if (!taskId.equals(task) || generation != currentGeneration) return Optional.empty();
        return manager.byKey(recipeId()).filter(holder -> holder.value() == recipe.value()
                && holder.value().getType() == recipeType());
    }
}
