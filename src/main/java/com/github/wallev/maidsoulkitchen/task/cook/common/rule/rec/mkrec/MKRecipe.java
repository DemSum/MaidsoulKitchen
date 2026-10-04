package com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Source: MSK 1.20.1-1.0-dev (58ec08ec), same relative class. Holder/RecipeInput and defensive component value copies are the only platform changes; beta consumers migrate in P2. */
public class MKRecipe<R extends Recipe<? extends RecipeInput>> {
    protected final R rec;
    protected final RecipeHolder<R> holder;
    protected final boolean single;

    private final RecIngredient tool;

    protected final List<ItemStack> inFluids;
    protected final List<RecIngredient> inItems;
    protected final ItemStack output;
    protected final ItemStack container;
    protected final ResourceLocation id;

    protected Set<Item> validInItems;
    protected Set<Item> validInFluids;

    protected Set<ItemDefinition> validInItemDefinitions;
    protected Set<ItemDefinition> validInFluidDefinitions;

    public MKRecipe(RecipeHolder<R> holder, boolean single, RecIngredient tool, List<ItemStack> inFluids, List<RecIngredient> inItems, ItemStack output, ItemStack container, Set<Item> validInItems, Set<Item> validInFluids) {
        this.holder = holder;
        this.rec = holder.value();
        this.single = single;
        this.tool = tool;
        this.inFluids = inFluids.stream().map(ItemStack::copy).toList();
        this.inItems = List.copyOf(inItems);
        this.output = output.copy();
        this.container = container.copy();
        this.id = holder.id();
        this.validInItems = validInItems;
        this.validInFluids = validInFluids;
        this.validInItemDefinitions = createValidItemDefinitionsFromItems(validInItems);
        this.validInFluidDefinitions = createValidItemDefinitionsFromItems(validInFluids);
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, RecIngredient tool, List<ItemStack> inFluids, List<RecIngredient> inItems, ItemStack output, ItemStack container) {
        this.holder = holder;
        this.rec = holder.value();
        this.single = single;
        this.tool = tool;
        this.inFluids = inFluids.stream().map(ItemStack::copy).toList();
        this.inItems = List.copyOf(inItems);
        this.output = output.copy();
        this.container = container.copy();
        this.id = holder.id();
        this.validInItems = createValidItemsFromIngredients(inItems);
        this.validInFluids = createValidItemsFromItemStacks(inFluids);
        this.validInItemDefinitions = createValidItemDefinitionsFromIngredients(inItems);
        this.validInFluidDefinitions = createValidItemDefinitionsFromItemStacks(inFluids);
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, RecIngredient tool, List<RecIngredient> inItems, ItemStack output, ItemStack container) {
        this(holder, single, tool, List.of(), inItems, output, container);
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, RecIngredient tool, List<RecIngredient> inItems, ItemStack output) {
        this(holder, single, tool, List.of(), inItems, output, ItemStack.EMPTY);
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, List<ItemStack> inFluids, List<RecIngredient> inItems, ItemStack output, ItemStack container) {
        this(holder, single, RecIngredient.EMPTY, inFluids, inItems, output, container, createValidItemsFromIngredients(inItems), createValidItemsFromItemStacks(inFluids));
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, List<ItemStack> inFluids, List<RecIngredient> inItems, ItemStack output) {
        this(holder, single, inFluids, inItems, output, ItemStack.EMPTY);
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, Set<Item> validInItems, List<RecIngredient> inItems, ItemStack output) {
        this(holder, single, RecIngredient.EMPTY, List.of(), inItems, output, ItemStack.EMPTY, validInItems, Set.of());
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, List<RecIngredient> inItems, ItemStack output) {
        this(holder, single, List.of(), inItems, output, ItemStack.EMPTY);
    }

    public MKRecipe(RecipeHolder<R> holder, boolean single, List<RecIngredient> inItems, ItemStack output, ItemStack container) {
        this(holder, single, List.of(), inItems, output, container);
    }

    public static Set<Item> createValidItemsFromIngredients(List<RecIngredient> items) {
        return items.stream()
                .flatMap(RecIngredient -> Arrays.stream(RecIngredient.ingredient.getItems()))
                .map(ItemStack::getItem)
                .collect(Collectors.toSet());
    }

    public static Set<Item> createValidItemsFromItemStacks(List<ItemStack> items) {
        return items.stream()
                .map(ItemStack::getItem)
                .collect(Collectors.toSet());
    }

    public static Set<ItemDefinition> createValidItemDefinitionsFromIngredients(List<RecIngredient> items) {
        return items.stream()
                .flatMap(RecIngredient -> Arrays.stream(RecIngredient.ingredient.getItems()))
                .map(ItemDefinition::of)
                .collect(Collectors.toSet());
    }

    public static Set<ItemDefinition> createValidItemDefinitionsFromItemStacks(List<ItemStack> items) {
        return items.stream()
                .map(ItemDefinition::of)
                .collect(Collectors.toSet());
    }

    public static Set<ItemDefinition> createValidItemDefinitionsFromItems(Set<Item> items) {
        return items.stream()
                .map(ItemDefinition::of)
                .collect(Collectors.toSet());
    }

    public RecIngredient tool() {
        return tool;
    }

    public List<ItemStack> inFluids() {
        return inFluids.stream().map(ItemStack::copy).toList();
    }

    public List<RecIngredient> inItems() {
        return inItems;
    }

    public ItemStack output() {
        return output.copy();
    }

    public ItemStack container() {
        return container.copy();
    }

    public Set<Item> validInItems() {
        return validInItems;
    }

    public Set<Item> validInFluids() {
        return validInFluids;
    }

    public Set<ItemDefinition> validInItemDefinitions() {
        return validInItemDefinitions;
    }

    public Set<ItemDefinition> validInFluidDefinitions() {
        return validInFluidDefinitions;
    }

    public RecipeHolder<R> holder() { return holder; }

    public R rec() {
        return rec;
    }

    public boolean isSingle() {
        return single;
    }

    public ResourceLocation id() {
        return id;
    }

    public String idStr() {
        return id().toString();
    }

    @Override
    public String toString() {
        return id().toString();
    }
}
