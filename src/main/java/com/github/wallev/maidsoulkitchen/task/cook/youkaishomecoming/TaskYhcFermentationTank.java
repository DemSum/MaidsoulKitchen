package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming;

import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.util.FakePlayerUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import dev.xkmc.youkaishomecoming.content.item.fluid.IYHSake;
import dev.xkmc.youkaishomecoming.content.item.fluid.SakeBottleItem;
import dev.xkmc.youkaishomecoming.content.item.fluid.SakeFluid;
import dev.xkmc.youkaishomecoming.content.pot.ferment.*;
import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.*;

import static dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlock.OPEN;

/**
 * 2025/03/09
 * 写的什么屎山，还请见谅，应该是半年时间吧，如果不出意外的话。
 * 后面会一起重构的，现在将就着用着先把。
 */
@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.YHC_FERMENTATION_TANK)
public class TaskYhcFermentationTank implements ICookTask<FermentationTankBlockEntity, FermentationRecipe<?>> {
    // 配方所需的流体对应的itemStacks和原材料
    // 流体容器

    @Override
    public TaskDataKey<CookData> getCookDataKey() {
        return DataRegister.YHC_FERMENTATION_TANK;
    }

    @Override
    public boolean isCookBE(BlockEntity blockEntity) {
        return blockEntity instanceof FermentationTankBlockEntity;
    }

    @Override
    public RecipeType<FermentationRecipe<?>> getRecipeType() {
        return YHBlocks.FERMENT_RT.get();
    }

    @Override
    public com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager<FermentationRecipe<?>> getRecSerializerManager() {
        return com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationRecSerializerManager.getInstance();
    }

    @SuppressWarnings("all")
    @Override
    public boolean shouldMoveTo(ServerLevel serverLevel, EntityMaid maid, FermentationTankBlockEntity blockEntity, MaidCookManager<FermentationRecipe<?>> recManager) {
        // 发酵桶是否在发酵
        FermentationDummyContainer cont = new FermentationDummyContainer(blockEntity.items, blockEntity.fluids);
        Optional<FermentationRecipe<?>> beRecipe = maid.level.getRecipeManager().getRecipeFor((RecipeType) YHBlocks.FERMENT_RT.get(), cont, maid.level);

        // 有未取出的流体并且没有在发酵
        FluidStack fluidInTank = blockEntity.fluids.getFluidInTank(0);
        Fluid fluid = fluidInTank.getFluid();
        if (!fluidInTank.isEmpty() && beRecipe.isEmpty()) {
            boolean hasFluidContainer;

            if (fluid instanceof SakeFluid sakeFluid) {
                ItemStack outputFluidContainers = sakeFluid.type.getContainer().getDefaultInstance();
                hasFluidContainer = recManager.hasOutputAdditionItem(itemStack -> itemStack.is(outputFluidContainers.getItem()));
            } else {
                List<ItemStack> outputFluidContainers = com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationRecSerializerManager.getInstance().fluidContainer(fluid);
                hasFluidContainer = recManager.hasOutputAdditionItem(itemStack -> outputFluidContainers.stream().anyMatch(stack -> stack.is(itemStack.getItem())));
            }

            if (hasFluidContainer) {
                return true;
            }
        }

        // 发酵桶有多余的原材料（不可以发酵）以及发酵桶没有在发酵
        if (!blockEntity.items.isEmpty() && beRecipe.isEmpty()) {
            return true;
        }

        // 发酵桶没有在发酵并且有配方原材料
        if (fluidInTank.isEmpty() && blockEntity.items.isEmpty() && beRecipe.isEmpty() && blockEntity.inProgress() == 0) {
            if (!recManager.getMaidRecs().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("all")
    @Override
    public void processCookMake(ServerLevel serverLevel, EntityMaid maid, FermentationTankBlockEntity blockEntity, MaidCookManager<FermentationRecipe<?>> recManager) {
        IItemHandlerModifiable inputInv = recManager.getInputInv();
        IItemHandlerModifiable outputAdditionInv = recManager.getOutputAdditionInv();
        IItemHandlerModifiable outputInv = recManager.getOutputInv();

        boolean extracted = false;

        // 发酵桶是否在发酵
        FermentationDummyContainer cont = new FermentationDummyContainer(blockEntity.items, blockEntity.fluids);
        Optional<FermentationRecipe<?>> beRecipe = maid.level.getRecipeManager().getRecipeFor((RecipeType) YHBlocks.FERMENT_RT.get(), cont, maid.level);

        FluidStack fluidInTank = blockEntity.fluids.getFluidInTank(0);
        Fluid fluid = fluidInTank.getFluid();
        // 有未取出的流体并且没有在发酵
        if (!fluidInTank.isEmpty() && beRecipe.isEmpty()) {
            ItemStack fluidContainer;

            // 获取流体容器
            if (fluid instanceof SakeFluid sakeFluid) {
                ItemStack outputFluidContainers = sakeFluid.type.getContainer().getDefaultInstance();
                fluidContainer = recManager.findOutputAdditionItem(itemStack -> itemStack.is(outputFluidContainers.getItem()));
            } else {
                List<ItemStack> outputFluidContainers = com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationRecSerializerManager.getInstance().fluidContainer(fluid);
                fluidContainer = recManager.findOutputAdditionItem(itemStack -> outputFluidContainers.stream().anyMatch(stack -> stack.is(itemStack.getItem())));
            }

            // 取出流体
            while (!fluidInTank.isEmpty() && !fluidContainer.isEmpty()) {
                ItemStack interactItem = fluidContainer.copyWithCount(1);
                ItemStack interactedItem = FakePlayerUtil.interactUseOnBlock(maid, blockEntity.getBlockPos(), interactItem.copy());
                boolean interactionChangedItem = !ItemStack.isSameItemSameComponents(interactItem, interactedItem)
                        || interactItem.getCount() != interactedItem.getCount();
                boolean interactionSucceeded = false;

                if (!interactedItem.isEmpty()) {
                    if (interactionChangedItem) {
                        CookInventoryTransactions.returnOrDrop(outputInv, interactedItem, maid);
                        fluidContainer.shrink(1);
                        interactionSucceeded = true;
                    }
                } else if (fluid instanceof SakeFluid sakeFluid) {
                    CookInventoryTransactions.returnOrDrop(outputInv, sakeFluid.type.asStack(1), maid);
                    fluidContainer.shrink(1);
                    interactionSucceeded = true;
                }

                if (!interactionSucceeded) break;

                blockEntity.notifyTile();

                extracted = true;
            }

            // 将剩下的容器放回背包
            ItemStack leftItem = ItemHandlerHelper.insertItemStacked(outputAdditionInv, fluidContainer, false);
            if (!leftItem.isEmpty()) {
                maid.spawnAtLocation(leftItem);
            }
        }

        // 发酵桶有多余的原材料（不可以发酵）以及发酵桶没有在发酵
        if (!blockEntity.items.isEmpty() && beRecipe.isEmpty()) {
            FermentationItemContainer items = blockEntity.items;
            for(int i = 0; i < items.getContainerSize(); ++i) {
                ItemStack item = items.getItem(i);
                ItemStack leftItem = ItemHandlerHelper.insertItemStacked(inputInv, item.copy(), false);
                item.shrink(item.getCount() - leftItem.getCount());

                extracted = true;
            }
        }

        if (extracted) {
            IAddonMaid.pickupAction(maid);
        }


        // 发酵桶没有在发酵并且有配方原材料
        if (fluidInTank.isEmpty() && blockEntity.items.isEmpty() && beRecipe.isEmpty() && blockEntity.inProgress() == 0) {
            if (!recManager.getMaidRecs().isEmpty()) {
                Pair<List<Integer>, List<List<ItemStack>>> recipeIngredient = recManager.getRecipeIngredient();

                if (recipeIngredient.getFirst().isEmpty()) {
                    return;
                }

                // 填充流体
                List<ItemStack> fluidItems = recipeIngredient.getSecond().get(0);
                int requiredFluidItems = recipeIngredient.getFirst().get(0);
                for (int times = 0; times < requiredFluidItems; ) {
                    boolean progressed = false;
                    for (ItemStack fluidItem : fluidItems) {
                        if (fluidItem.isEmpty()) continue;
                        ItemStack interactItem = fluidItem.copyWithCount(1);
                        ItemStack interactedStack = FakePlayerUtil.interactUseOnBlock(maid, blockEntity.getBlockPos(), interactItem.copy());
                        boolean interactionChangedItem = !ItemStack.isSameItemSameComponents(interactItem, interactedStack)
                                || interactItem.getCount() != interactedStack.getCount();
                        if (interactionChangedItem) {
                            fluidItem.shrink(1);
                            CookInventoryTransactions.returnOrDrop(inputInv, interactedStack, maid);
                            progressed = true;
                            times++;
                            if (times >= requiredFluidItems) break;
                        }
                    }
                    if (!progressed) break;
                }


                int i = 0;
                for (List<ItemStack> itemStacks : recipeIngredient.getSecond()) {

                    // 过滤掉第一批次的物资，那是用来填充流体的。
                    if (i++ < 1) {
                        continue;
                    }

                    Optional<ItemStack> first = itemStacks.stream().filter(stack -> !stack.isEmpty()).findFirst();
                    first.ifPresent(stack -> {
                        ItemStack copy = stack.copy();
                        copy.setCount(1);
                        if (blockEntity.items.canAddItem(copy)) {
                            ItemStack remain = blockEntity.items.addItem(copy);
                            if (remain.isEmpty()) {
                                stack.shrink(1);
                                blockEntity.notifyTile();
                            }
                        }
                    });
                }

                serverLevel.setBlockAndUpdate(blockEntity.getBlockPos(), blockEntity.getBlockState().setValue(OPEN, false));
                blockEntity.notifyTile();

                IAddonMaid.pickupAction(maid);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return TaskInfo.YHC_FERMENTATION_TANK.uid;
    }

    @Override
    public ItemStack getIcon() {
        return YHBlocks.FERMENT.asStack();
    }

    @Override
    public List<RecipeHolder<FermentationRecipe<?>>> getRecipeHolders(Level level) {
        return com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationRecSerializerManager.getInstance().getRecipes(level).stream().map(description -> description.holder()).toList();
    }

    @Override
    public NonNullList<Ingredient> getIngredients(Recipe<?> recipe) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        getRecSerializerManager().getRecipeDescription(recipe).ifPresent(description -> {
            ingredients.add(description.inFluids().isEmpty() ? Ingredient.EMPTY : Ingredient.of(description.inFluids().stream()));
            description.inItems().forEach(ingredient -> ingredients.add(ingredient.ingredient));
        });
        return ingredients;
    }

    @Override
    public ItemStack getResultItem(Recipe<?> recipe, RegistryAccess pRegistryAccess) {
        SimpleFermentationRecipe fermentationRecipe = (SimpleFermentationRecipe) recipe;
        Fluid fluid = fermentationRecipe.outputFluid.getFluid();
        if (fluid instanceof SakeFluid sakeFluid) {
            return sakeFluid.type.asStack(1);
        }
        return Items.AIR.getDefaultInstance();
    }
}
