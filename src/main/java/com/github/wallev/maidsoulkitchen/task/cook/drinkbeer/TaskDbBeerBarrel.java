package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.inventory.tooltip.AmountTooltip;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.TaskBaseContainerCook;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity;
import lekavar.lma.drinkbeer.recipes.BrewingRecipe;
import lekavar.lma.drinkbeer.registries.BlockRegistry;
import lekavar.lma.drinkbeer.registries.RecipeRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.*;

@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.DB_BEER)
public class TaskDbBeerBarrel extends TaskBaseContainerCook<BeerBarrelBlockEntity, BrewingRecipe> {
    @Override
    public boolean isCookBE(BlockEntity blockEntity) {
        return blockEntity instanceof BeerBarrelBlockEntity;
    }

    @Override
    public RecipeType<BrewingRecipe> getRecipeType() {
        return RecipeRegistry.RECIPE_TYPE_BREWING.get();
    }

    @Override
    public ResourceLocation getUid() {
        return TaskInfo.DB_BEER.uid;
    }

    @Override
    public ItemStack getIcon() {
        return BlockRegistry.BEER_BARREL.get().asItem().getDefaultInstance();
    }

    @Override
    public boolean isHeated(BeerBarrelBlockEntity be) {
        return true;
    }

    @Override
    public boolean beInnerCanCook(Container inventory, BeerBarrelBlockEntity be) {
        return DrinkBeerBarrelAdapter.isBrewing(be);
    }

    @Override
    public int getOutputSlot() {
        return 5;
    }

    @Override
    public int getInputSize() {
        return 5;
    }

    @Override
    public Container getContainer(BeerBarrelBlockEntity be) {
        return be.getBrewingInventory();
    }

    @Override
    public com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager<BrewingRecipe> getRecSerializerManager() {
        return com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel.BeerBarrelRecSerializerManager.getInstance();
    }

    @Override
    public boolean maidShouldMoveTo(ServerLevel serverLevel, EntityMaid entityMaid, BeerBarrelBlockEntity blockEntity, MaidCookManager<BrewingRecipe> maidRecipesManager) {
        Container inventory = getContainer(blockEntity);
        if (canTakeOutput(inventory, blockEntity)) {
            return true;
        }

        IItemHandlerModifiable inputInventory = maidRecipesManager.getInputInv();
        if (DrinkBeerBarrelAdapter.canModifyInputs(blockEntity)
                && DrinkBeerBarrelInventory.needsCups(inventory)
                && inputInventory != null
                && DrinkBeerBarrelInventory.hasEmptyBeerMug(inputInventory)) {
            return true;
        }

        // 啤酒桶正在酿造时无需重复投料。
        boolean b = DrinkBeerBarrelAdapter.isBrewing(blockEntity);
        var recipesIngredients = maidRecipesManager.getMaidRecs();
        // 空闲或等待取出成品时仍需靠近设备处理库存。
        if (!b && !recipesIngredients.isEmpty()) {
            return true;
        }

        // 有输入
        return DrinkBeerBarrelInventory.hasReturnedBucket(inventory);
    }

    @Override
    public void maidCookMake(ServerLevel serverLevel, EntityMaid entityMaid, BeerBarrelBlockEntity blockEntity, MaidCookManager<BrewingRecipe> maidRecipesManager) {
        extractOutputStack(getContainer(blockEntity), maidRecipesManager.getOutputInv(), blockEntity);
        extractInputStack(getContainer(blockEntity), maidRecipesManager.getInputInv(), blockEntity);
        tryInsertItem(serverLevel, entityMaid, blockEntity, maidRecipesManager);

        maidRecipesManager.syncInv();
    }

    @Override
    public void extractOutputStack(Container inventory, IItemHandlerModifiable availableInv, BlockEntity blockEntity) {
        ItemStack stackInSlot = inventory.getItem(this.getOutputSlot());

        BeerBarrelBlockEntity barrel = (BeerBarrelBlockEntity) blockEntity;
        if (!stackInSlot.isEmpty() && DrinkBeerBarrelAdapter.isOutputReady(barrel)) {
            ItemStack copy = stackInSlot.copy();
            ItemStack leftStack = ItemHandlerHelper.insertItemStacked(availableInv, copy, false);
            inventory.removeItem(this.getOutputSlot(), stackInSlot.getCount() - leftStack.getCount());
            DrinkBeerBarrelAdapter.markChanged(barrel);
        }
    }

    @Override
    public boolean canTakeOutput(Container inventory, BeerBarrelBlockEntity beerBarrelBlockEntity) {
        ItemStack outputStack = inventory.getItem(this.getOutputSlot());

        return !outputStack.isEmpty() && DrinkBeerBarrelAdapter.isOutputReady(beerBarrelBlockEntity);
    }

    @Override
    public void tryInsertItem(ServerLevel serverLevel, EntityMaid entityMaid, BeerBarrelBlockEntity blockEntity, MaidCookManager<BrewingRecipe> maidRecipesManager) {
        if (!DrinkBeerBarrelAdapter.canModifyInputs(blockEntity)) return;

        Container inventory = getContainer(blockEntity);
        IItemHandlerModifiable inputInventory = maidRecipesManager.getInputInv();
        if (inputInventory != null && DrinkBeerBarrelInventory.fillCups(inventory, inputInventory)) {
            DrinkBeerBarrelAdapter.markChanged(blockEntity);
            return;
        }
        super.tryInsertItem(serverLevel, entityMaid, blockEntity, maidRecipesManager);
    }

    @Override
    public boolean inputCanTake(boolean beInnerCanCook, Container inventory) {
        return DrinkBeerBarrelInventory.hasReturnedBucket(inventory);
    }

    @Override
    public boolean hasInput(Container inventory) {
        return DrinkBeerBarrelInventory.hasReturnedBucket(inventory);
    }

    @Override
    public void extractInputStack(Container inventory, IItemHandlerModifiable availableInv, BlockEntity blockEntity) {
        DrinkBeerBarrelInventory.extractReturnedBuckets(inventory, availableInv, blockEntity);
    }

    @Override
    public void insertInputStack(
            Container inventory,
            IItemHandlerModifiable availableInv,
            BlockEntity blockEntity,
            Pair<List<Integer>, List<List<ItemStack>>> ingredientPair
    ) {
        DrinkBeerBarrelInventory.insertRecipeInputs(
                inventory, availableInv, blockEntity, ingredientPair);
    }

    @Override
    public TaskDataKey<CookData> getCookDataKey() {
        return DataRegister.DB_BEER;
    }

    @Override
    public Optional<TooltipComponent> getRecClientAmountTooltip(Recipe<?> recipe, boolean modeRandom, boolean overSize, CookData cookData) {
        BrewingRecipe brewingRecipe = (BrewingRecipe) recipe;
        ItemStack beerCup = brewingRecipe.getBeerCup();
        List<Ingredient> ingres = this.getIngredients(recipe);
        NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(ingres);
        list.add(Ingredient.of(beerCup));
        return ingres.isEmpty() ? Optional.empty() : Optional.of(new AmountTooltip(getRecipeId(recipe), list, modeRandom, overSize, cookData));
    }

}
