package com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import java.util.*;

/**
 * Source: 58ec08ec task/cook/common/rule/rec/FluidRecSerializerManager.java (MIT).
 * Retains fluid selection, ingredient selection and conversion boundaries. Holder/RecipeInput and
 * MaidItem.Role adapt recipe identity and typed materials to 1.21. Upstream deducted only one plan's
 * inputs while repeating it, and repeated an already batched plan; deduct every generated batch once.
 * Empty-fluid recipes now use the ordinary converter. The unused registry utilities/empty mod hook
 * are omitted; FermentationRecSerializerManager retains its upstream native registry scan.
 * P2 replaces beta fermentation getAmountIngredient when the unified manager is wired.
 */
public abstract class FluidRecSerializerManager<R extends Recipe<? extends RecipeInput>> extends RecSerializerManager<R> {
    protected Map<Fluid, List<ItemStack>> fluidContainers = new HashMap<>();

    protected FluidRecSerializerManager(RecipeType<R> recipeType) { super(recipeType); }
    public List<ItemStack> fluidContainer(Fluid fluid) {
        return fluidContainers.getOrDefault(fluid, List.of()).stream().map(ItemStack::copy).toList();
    }
    @Override protected abstract FluidRecipeInfoProvider<R> createRecipeInfoProvider();
    @Override protected void initRecs(Level level, List<RecipeHolder<R>> holders) { this.initFluidRecs(level, holders); }
    protected abstract void initFluidRecs(Level level, List<RecipeHolder<R>> holders);

    /** Upstream createMKRecipe fluid overload, preserving the Holder at the 1.21 boundary. */
    protected MKRecipe<R> createMKRecipe(RecipeHolder<R> holder, List<ItemStack> inFluids) {
        R r = holder.value();
        return new MKRecipe<>(holder, recipeInfoProvider.isSingle(this, r), inFluids,
                recipeInfoProvider.getIngredients(this, r), recipeInfoProvider.getOutput(this, r),
                recipeInfoProvider.getContainer(this, r));
    }

    @Override
    protected List<MaidRec> recProcess(MKRecipe<R> r, Map<ItemDefinition, Long> available,
                                       List<ItemDefinition> invIngredient, boolean[] single,
                                       Map<ItemDefinition, ItemAmount> itemTimes, ResourceLocation taskId, long generation) {
        if (r.inFluids().isEmpty()) return super.recProcess(r, available, invIngredient, single, itemTimes, taskId, generation);
        ItemDefinition fluidItem = processRecFluids(available, invIngredient, single, itemTimes, r.inFluids());
        if (fluidItem == null || !super.processRecIngres(r, available, invIngredient, single, itemTimes, taskId, generation)) return List.of();
        int canCookAmount = getMaxAmount(available, single, itemTimes, taskId, generation);
        if (canCookAmount <= 0) return List.of();
        boolean isSingle = single[0] || r.isSingle();
        int amount = isSingle ? 1 : canCookAmount;
        int repeats = isSingle ? canCookAmount : 1;
        List<MaidItem> maidItems = new ArrayList<>();
        for (int index = 0; index < invIngredient.size(); index++) {
            ItemDefinition definition = invIngredient.get(index);
            int perBatch = index == 0 ? r.inFluids().stream().filter(stack -> stack.is(definition.item())).findFirst().orElseThrow().getCount()
                    : r.inItems().get(index - 1).test(definition.toStack(Integer.MAX_VALUE));
            int count = amount * perBatch;
            itemTimes.get(definition).setRecAmount(amount);
            maidItems.add(new MaidItem(definition, count, index == 0 ? MaidItem.Role.FLUID : MaidItem.Role.INGREDIENT));
            available.put(definition, available.get(definition) - (long) count * repeats);
        }
        MaidRec maidRec = new MaidRec(r.holder(), taskId, generation, 0, amount, List.of(r.output()), maidItems, Map.of());
        return generateRecs(maidRec, repeats);
    }

    protected ItemDefinition processRecFluids(Map<ItemDefinition, Long> available, List<ItemDefinition> invIngredient,
                                               boolean[] single, Map<ItemDefinition, ItemAmount> itemTimes, List<ItemStack> inFluids) {
        for (ItemStack inFluid : inFluids) {
            for (Map.Entry<ItemDefinition, Long> entry : available.entrySet()) {
                ItemDefinition definition = entry.getKey();
                if (definition.is(inFluid.getItem()) && entry.getValue() >= inFluid.getCount()) {
                    invIngredient.add(definition);
                    if (!definition.isStackable()) single[0] = true;
                    itemTimes.put(definition, new ItemAmount(1, inFluid.getCount()));
                    return definition;
                }
            }
        }
        return null;
    }

    public abstract static class FluidRecipeInfoProvider<R extends Recipe<? extends RecipeInput>> extends RecipeInfoProvider<R> {
        @Override public ItemStack getContainer(RecSerializerManager<R> rsm, R rec) {
            FluidRecSerializerManager<R> fluids = (FluidRecSerializerManager<R>) rsm;
            List<ItemStack> stacks = fluids.fluidContainer(getOutputFluid(rsm, rec));
            return stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst();
        }
        public abstract Fluid getOutputFluid(RecSerializerManager<R> rsm, R rec);
        @Override public boolean isSingle(RecSerializerManager<R> rsm, R rec) { return true; }
    }
}
