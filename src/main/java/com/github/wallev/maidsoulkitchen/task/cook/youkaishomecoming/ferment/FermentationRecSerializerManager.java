package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.FluidRecSerializerManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import dev.xkmc.youkaishomecoming.content.item.fluid.IYHSake;
import dev.xkmc.youkaishomecoming.content.item.fluid.SakeBottleItem;
import dev.xkmc.youkaishomecoming.content.item.fluid.SakeFluid;
import dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationRecipe;
import dev.xkmc.youkaishomecoming.content.pot.ferment.SimpleFermentationRecipe;
import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.*;

@TaskClassAnalyzer(TaskInfo.YHC_FERMENTATION_TANK)
/**
 * Source: 58ec08ec task/cook/youkaishomecoming/ferment/FermentationRecSerializerManager.java (MIT).
 * Native registry scan and descriptor are ported; NeoForge item capabilities and Holder retain 1.21 identity.
 * Current SimpleFermentationRecipe.matches requires exactly outputFluid.amount (or 1000), rather
 * than the representative FluidIngredient stack amount. Reserve exact container multiples only;
 * rounding up produces a native nonmatching tank and rounding down underfills it.
 * P2 replaces the beta fermentation ingredient map/planner when MaidCookManager is wired.
 */
public class FermentationRecSerializerManager extends FluidRecSerializerManager<FermentationRecipe<?>> {
    private static final FermentationRecSerializerManager INSTANCE = new FermentationRecSerializerManager();

    protected FermentationRecSerializerManager() {
        super(YHBlocks.FERMENT_RT.get());
    }

    public static FermentationRecSerializerManager getInstance() {
        return INSTANCE;
    }

    @Override
    protected FluidRecipeInfoProvider<FermentationRecipe<?>> createRecipeInfoProvider() {
        return new FermentationRecipeMKRecipe();
    }

    @Override
    public String getRecipeTypeId() {
        return BuiltInRegistries.RECIPE_TYPE.getKey(YHBlocks.FERMENT_RT.get()).toString();
    }

    @Override
    protected void initFluidRecs(Level level, List<RecipeHolder<FermentationRecipe<?>>> holders) {


        Map<Fluid, List<Pair<ItemStack, Integer>>> fluidItems1 = new HashMap<>();
        Map<Fluid, List<ItemStack>> fluidContainers1 = new HashMap<>();
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid instanceof EmptyFluid) continue;

            ItemStack container = fluid.getBucket().getDefaultInstance().getCraftingRemainingItem();
            if (!container.isEmpty()) {
                if (fluidContainers1.containsKey(fluid)) {
                    List<ItemStack> itemStacks = fluidContainers1.getOrDefault(fluid, Collections.emptyList());
                    if (itemStacks.stream().noneMatch(itemStack1 -> itemStack1.is(container.getItem()))) {
                        itemStacks.add(container);
                    }
                } else {
                    fluidContainers1.put(fluid, Lists.newArrayList(container));
                }
            }
        }
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof SakeBottleItem sakeBottleItem) {
                SakeFluid fluid = sakeBottleItem.getFluid();

                IYHSake iyhSake = fluid.type;
                Fluid rawFluid = fluid.getSource();

                if (fluidItems1.containsKey(rawFluid)) {
                    List<Pair<ItemStack, Integer>> fluidItems2 = fluidItems1.getOrDefault(rawFluid, Collections.emptyList());
                    if (fluidItems2.stream().noneMatch(pair1 -> pair1.getFirst().is(item))) {
                        fluidItems1.get(rawFluid).add(Pair.of(item.getDefaultInstance(), iyhSake.amount()));
                    }
                } else {
                    fluidItems1.put(rawFluid, Lists.newArrayList(Pair.of(item.getDefaultInstance(), iyhSake.amount())));
                }

                ItemStack container = iyhSake.getContainer().getDefaultInstance();
                if (!container.isEmpty()) {
                    if (fluidContainers1.containsKey(rawFluid)) {
                        List<ItemStack> itemStacks = fluidContainers1.getOrDefault(rawFluid, Collections.emptyList());
                        if (itemStacks.stream().noneMatch(itemStack1 -> itemStack1.is(container.getItem()))) {
                            itemStacks.add(container);
                        }
                    } else {
                        fluidContainers1.put(rawFluid, Lists.newArrayList(container));
                    }
                }
                continue;
            }

            ItemStack defaultInstance = item.getDefaultInstance().copy();
            IFluidHandlerItem iFluidHandlerItem = defaultInstance.getCapability(Capabilities.FluidHandler.ITEM);
            if (iFluidHandlerItem != null && iFluidHandlerItem instanceof FluidBucketWrapper fluidBucketWrapper) {
                FluidStack fluidStack = fluidBucketWrapper.getFluid();
                Fluid rawFluid = fluidStack.getFluid();

                if (!fluidStack.isEmpty() && !(rawFluid instanceof EmptyFluid)) {

                    if (fluidItems1.containsKey(rawFluid)) {
                        List<Pair<ItemStack, Integer>> fluidItems2 = fluidItems1.getOrDefault(rawFluid, Collections.emptyList());
                        if (fluidItems2.stream().noneMatch(pair1 -> pair1.getFirst().is(defaultInstance.getItem()))) {
                            fluidItems1.get(rawFluid).add(Pair.of(defaultInstance, fluidStack.getAmount()));
                        }
                    } else {
                        fluidItems1.put(rawFluid, Lists.newArrayList(Pair.of(defaultInstance, fluidStack.getAmount())));
                    }

                    ItemStack container = fluidBucketWrapper.getContainer().getCraftingRemainingItem();
                    if (!container.isEmpty()) {
                        if (fluidContainers1.containsKey(rawFluid)) {
                            List<ItemStack> itemStacks = fluidContainers1.getOrDefault(rawFluid, Collections.emptyList());
                            if (itemStacks.stream().noneMatch(itemStack1 -> itemStack1.is(container.getItem()))) {
                                itemStacks.add(container);
                            }
                        } else {
                            fluidContainers1.put(rawFluid, Lists.newArrayList(container));
                        }
                    }
                }
            }
        }
        this.fluidContainers = fluidContainers1;

        List<MKRecipe<FermentationRecipe<?>>> mkRecipes = new ArrayList<>();
        for (RecipeHolder<FermentationRecipe<?>> holder : holders) {
            FermentationRecipe<?> recipe = holder.value();
            if (!(recipe instanceof SimpleFermentationRecipe fermentationRecipe)) continue;
            // YHC 3.0.6's native tank constructor has a fixed 1000mB capacity. Its matcher
            // requires exactly outputFluid.amount for fluid recipes, so larger recipes cannot
            // execute on this registered device and must not reserve/partially consume inputs.
            if (!fermentationRecipe.inputFluid.isEmpty() && fermentationRecipe.outputFluid.getAmount() > 1000) continue;

            // 输入的流体
            FluidIngredient fluidIn = fermentationRecipe.inputFluid;
            if (fluidIn != null && !fluidIn.isEmpty()) {
                List<ItemStack> fluidItems = new ArrayList<>();
                // 1.21 YHC accepts a fluid ingredient/tag rather than a single FluidStack.
                for (FluidStack fluidStack : fluidIn.getStacks()) {
                    fluidItems1.forEach((fluid, itemStacks) -> {
                        if (fluid.isSame(fluidStack.getFluid())) {
                            for (Pair<ItemStack, Integer> fluidStackPair : itemStacks) {
                                ItemStack outputFluidItem = fluidStackPair.getFirst().copy();
                                int amount = fluidStackPair.getSecond();
                                int amountTotal = fermentationRecipe.outputFluid.getAmount() > 0 ? fermentationRecipe.outputFluid.getAmount() : 1000;
                                if (amountTotal % amount != 0) continue;
                                outputFluidItem.setCount(amountTotal / amount);

                                if (fluidItems.stream().noneMatch(itemStack -> itemStack.is(outputFluidItem.getItem()) && itemStack.getCount() == outputFluidItem.getCount())) {
                                    fluidItems.add(outputFluidItem);
                                }

                            }
                        }
                    });
                }

                mkRecipes.add(this.createMKRecipe(holder, fluidItems));
            } else {
                mkRecipes.add(this.createMKRecipe(holder));
            }
        }
        this.recipes = mkRecipes;
    }

    public static class FermentationRecipeMKRecipe extends FluidRecipeInfoProvider<FermentationRecipe<?>> {

        @Override
        public List<RecIngredient> getIngredients(RecSerializerManager<FermentationRecipe<?>> rsm, FermentationRecipe<?> rec) {
            SimpleFermentationRecipe sFermentationRecipe = (SimpleFermentationRecipe) rec;
            return RecIngredient.from(sFermentationRecipe.ingredients);
        }

        @Override
        public ItemStack getOutput(RecSerializerManager<FermentationRecipe<?>> rsm, FermentationRecipe<?> rec) {
            SimpleFermentationRecipe sFermentationRecipe = (SimpleFermentationRecipe) rec;
            FluidStack outputFluid = sFermentationRecipe.outputFluid;
            Fluid fluid = outputFluid.getFluid();
            if (fluid instanceof SakeFluid sakeFluid) {
                return sakeFluid.type.asStack(1);
            } else {
                for (ItemStack result : sFermentationRecipe.results) {
                    if (!result.isEmpty()) {
                        return result;
                    }
                }
                return rec.getResultItem(((FermentationRecSerializerManager) rsm).registryAccess);
            }
        }

        @Override
        public Fluid getOutputFluid(RecSerializerManager<FermentationRecipe<?>> rsm, FermentationRecipe<?> rec) {
            SimpleFermentationRecipe sFermentationRecipe = (SimpleFermentationRecipe) rec;
            FluidStack outputFluid = sFermentationRecipe.outputFluid;
            return outputFluid.getFluid();
        }
    }
}
