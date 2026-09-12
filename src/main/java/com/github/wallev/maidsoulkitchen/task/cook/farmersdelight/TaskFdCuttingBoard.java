package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight;

import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCookMoveTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.MaidCookPathingTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModBlocks;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.*;
import java.util.function.Consumer;

@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.FD_CUTTING_BOARD)
public class TaskFdCuttingBoard implements ICookTask<CuttingBoardBlockEntity, CuttingBoardRecipe> {
    @Override
    public boolean isCookBE(BlockEntity blockEntity) {
        return blockEntity instanceof CuttingBoardBlockEntity;
    }

    @Override
    public RecipeType<CuttingBoardRecipe> getRecipeType() {
        return ModRecipeTypes.CUTTING.get();
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        if (maid.level.isClientSide) {
            return Collections.emptyList();
        }

        MaidRecipesManager<CuttingBoardRecipe> cookingPotRecipeMaidRecipesManager = getRecipesManager(maid);
        MaidCookMoveTask<CuttingBoardBlockEntity, CuttingBoardRecipe> maidCookMoveTask = new MaidCookMoveTask<>(this, cookingPotRecipeMaidRecipesManager);
        MaidCuttingMakeTask maidCookMakeTask = new MaidCuttingMakeTask(this, cookingPotRecipeMaidRecipesManager);
        MaidCookPathingTask<CuttingBoardBlockEntity, CuttingBoardRecipe> maidCookPathingTask = new MaidCookPathingTask<>(this);
        return Lists.newArrayList(Pair.of(5, maidCookMoveTask), Pair.of(6, maidCookMakeTask), Pair.of(7, maidCookPathingTask));
    }

    @Override
    public boolean shouldMoveTo(ServerLevel serverLevel, EntityMaid maid, CuttingBoardBlockEntity blockEntity, MaidRecipesManager<CuttingBoardRecipe> recManager) {
        if (blockEntity.getStoredItem().isEmpty() && !recManager.getRecipesIngredients().isEmpty()) {
            return true;
        }
        return findStoredRecipe(serverLevel, maid, blockEntity, recManager).isPresent();
    }

    @Override
    public void processCookMake(ServerLevel serverLevel, EntityMaid maid, CuttingBoardBlockEntity blockEntity, MaidRecipesManager<CuttingBoardRecipe> recManager) {

    }

    public void processCookMake(ServerLevel serverLevel, EntityMaid maid, CuttingBoardBlockEntity blockEntity, MaidRecipesManager<CuttingBoardRecipe> recManager, Consumer<Item> item) {
        if (!blockEntity.getStoredItem().isEmpty()) {
            findStoredRecipe(serverLevel, maid, blockEntity, recManager).ifPresent(recipe -> {
                if (equipTool(maid, recManager.getInputInv(), recipe.getTool())) {
                    item.accept(blockEntity.getStoredItem().getItem());
                }
            });
            return;
        }
        if (blockEntity.getStoredItem().isEmpty() && !recManager.getRecipesIngredients().isEmpty()) {
            Pair<List<Integer>, List<List<ItemStack>>> recipeIngredient = recManager.getRecipeIngredient();
            if (recipeIngredient.getFirst().isEmpty()) return;

            IItemHandlerModifiable availableInv = recManager.getInputInv();

            List<ItemStack> itemStacks = recipeIngredient.getSecond().get(0);
            for (ItemStack itemStack : itemStacks) {
                if (!itemStack.isEmpty()) {
                    ItemStack offhandItem = maid.getOffhandItem();
                    if (offhandItem != itemStack) {
                        if (!ItemHandlerHelper.insertItemStacked(availableInv, offhandItem, false).isEmpty()) return;
                    }

                    item.accept(itemStack.getItem());
                    maid.setItemInHand(InteractionHand.OFF_HAND, itemStack.copy());
                    itemStack.setCount(0);
                    break;
                }
            }

            List<ItemStack> toolStacks = recipeIngredient.getSecond().get(1);
            for (ItemStack itemStack : toolStacks) {
                if (!itemStack.isEmpty()) {
                    ItemStack maidMainHandItem = maid.getMainHandItem();
                    if (maidMainHandItem != itemStack) {
                        if (!ItemHandlerHelper.insertItemStacked(availableInv, maidMainHandItem, false).isEmpty()) return;
                    }

                    maid.setItemInHand(InteractionHand.MAIN_HAND, itemStack.copy());
                    itemStack.setCount(0);
                    break;
                }
            }

        }
    }

    private Optional<CuttingBoardRecipe> findStoredRecipe(
            ServerLevel level,
            EntityMaid maid,
            CuttingBoardBlockEntity board,
            MaidRecipesManager<CuttingBoardRecipe> recipeManager
    ) {
        return level.getRecipeManager().getAllRecipesFor(getRecipeType()).stream()
                .filter(holder -> recipeManager.isRecipeEnabled(holder.id()))
                .map(RecipeHolder::value)
                .filter(recipe -> !recipe.getIngredients().isEmpty()
                        && recipe.getIngredients().getFirst().test(board.getStoredItem())
                        && hasTool(maid, recipeManager.getInputInv(), recipe.getTool()))
                .findFirst();
    }

    private boolean hasTool(EntityMaid maid, IItemHandlerModifiable inventory, Ingredient tool) {
        if (tool.test(maid.getMainHandItem())) return true;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (tool.test(inventory.getStackInSlot(slot))) return true;
        }
        return false;
    }

    private boolean equipTool(EntityMaid maid, IItemHandlerModifiable inventory, Ingredient tool) {
        if (tool.test(maid.getMainHandItem())) return true;
        int toolSlot = -1;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (tool.test(inventory.getStackInSlot(slot))) {
                toolSlot = slot;
                break;
            }
        }
        if (toolSlot < 0) return false;

        ItemStack previous = maid.getMainHandItem();
        if (!previous.isEmpty() && !ItemHandlerHelper.insertItemStacked(inventory, previous.copy(), true).isEmpty()) {
            return false;
        }
        if (!previous.isEmpty()) {
            ItemHandlerHelper.insertItemStacked(inventory, previous.copy(), false);
            maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }

        ItemStack source = inventory.getStackInSlot(toolSlot);
        ItemStack equipped = source.copyWithCount(1);
        source.shrink(1);
        maid.setItemInHand(InteractionHand.MAIN_HAND, equipped);
        return true;
    }

    @Override
    public ResourceLocation getUid() {
        return TaskInfo.FD_CUTTING_BOARD.uid;
    }

    @Override
    public ItemStack getIcon() {
        return ModBlocks.CUTTING_BOARD.get().asItem().getDefaultInstance();
    }

    @Override
    public TaskDataKey<CookData> getCookDataKey() {
        return DataRegister.FD_CUTTING_BOARD;
    }

    @Override
    public NonNullList<Ingredient> getIngredients(Recipe<?> recipe) {
        CuttingBoardRecipe cuttingBoardRecipe = (CuttingBoardRecipe) recipe;
        NonNullList<Ingredient> ingredients = cuttingBoardRecipe.getIngredients();
        ingredients.add(cuttingBoardRecipe.getTool());
        return ingredients;
    }

}
