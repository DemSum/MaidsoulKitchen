package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.inventory.tooltip.AmountTooltip;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
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
    public MaidRecipesManager<BrewingRecipe> getRecipesManager(EntityMaid maid) {
        return new MaidRecipesManager<>(maid, this, false){
            @Override
            protected Pair<List<Integer>, List<Item>> getAmountIngredient(BrewingRecipe recipe, Map<Item, Integer> available) {
                String debugAvailableBefore = DrinkBeerDiagnostics.available(available);
                List<Ingredient> ingredients = recipe.getIngredients();
                List<Item> invIngredient = new ArrayList<>();
                Map<Item, Integer> itemTimes = new HashMap<>();
                boolean[] canMake = {true};
                boolean[] single = {false};

                for (Ingredient ingredient : ingredients) {
                    boolean hasIngredient = false;
                    for (Item item : available.keySet()) {
                        ItemStack stack = item.getDefaultInstance();
                        if (ingredient.test(stack)) {
                            invIngredient.add(item);
                            hasIngredient = true;

                            if (stack.getMaxStackSize() == 1) {
                                single[0] = true;
                                itemTimes.put(item, 1);
                            } else {
                                itemTimes.merge(item, 1, Integer::sum);
                            }

                            break;
                        }
                    }

                    if (!hasIngredient) {
                        canMake[0] = false;
                        itemTimes.clear();
                        invIngredient.clear();
                        break;
                    }
                }

                ItemStack beerCup = recipe.getBeerCup();
                {
                    boolean hasIngredient = false;
                    for (Item item : available.keySet()) {
                        ItemStack stack = item.getDefaultInstance();
                        if (beerCup.is(stack.getItem()) && available.getOrDefault(item, 0) >= beerCup.getCount()) {
                            invIngredient.add(item);
                            hasIngredient = true;

                            if (stack.getMaxStackSize() == 1) {
                                single[0] = true;
                                itemTimes.put(item, 1);
                            } else {
                                itemTimes.merge(item, beerCup.getCount(), Integer::sum);
                            }

                            break;
                        }
                    }

                    if (!hasIngredient) {
                        canMake[0] = false;
                        itemTimes.clear();
                        invIngredient.clear();
                    }
                }


                if (!canMake[0] || invIngredient.stream().anyMatch(item -> available.get(item) <= 0)) {
                    DrinkBeerDiagnostics.recipePlan(
                            beerCup, ingredients.size(), false, 0,
                            Collections.emptyList(), debugAvailableBefore, available);
                    return Pair.of(Collections.emptyList(), Collections.emptyList());
                }

                int maxCount = 64;
                if (single[0] || this.isSingle()) {
                    maxCount = 1;
                } else {
                    for (Item item : itemTimes.keySet()) {
                        maxCount = Math.min(maxCount, item.getDefaultInstance().getMaxStackSize());
                        maxCount = Math.min(maxCount, available.get(item) / itemTimes.get(item));
                    }
                }

                List<Integer> countList = new ArrayList<>();
                for (int i = 0; i < invIngredient.size() - 1; i++) {
                    countList.add(maxCount);
                    Item item = invIngredient.get(i);
                    available.put(item, available.get(item) - maxCount);
                }
                {
                    countList.add(beerCup.getCount());
                    Item item = invIngredient.get(invIngredient.size() - 1);
                    available.put(item, available.get(item) - maxCount);
                }

                DrinkBeerDiagnostics.recipePlan(
                        beerCup, ingredients.size(), true, maxCount,
                        countList, debugAvailableBefore, available);
                return Pair.of(countList, invIngredient);
            }

            @Override
            protected List<Pair<List<Integer>, List<List<ItemStack>>>> transform(List<Pair<List<Integer>, List<Item>>> oriList, Map<Item, Integer> available ) {
//                repeat(oriList, available);
                return super.transform(oriList, available);
            }
        };
    }

    @Override
    public boolean maidShouldMoveTo(ServerLevel serverLevel, EntityMaid entityMaid, BeerBarrelBlockEntity blockEntity, MaidRecipesManager<BrewingRecipe> maidRecipesManager) {
        Container inventory = getContainer(blockEntity);
        IItemHandlerModifiable inputInventory = maidRecipesManager.getInputInv();
        boolean canModify = DrinkBeerBarrelAdapter.canModifyInputs(blockEntity);
        boolean brewing = DrinkBeerBarrelAdapter.isBrewing(blockEntity);
        boolean outputReady = canTakeOutput(inventory, blockEntity);
        boolean needsCups = DrinkBeerBarrelInventory.needsCups(inventory);
        boolean hasMugs = inputInventory != null
                && DrinkBeerBarrelInventory.hasEmptyBeerMug(inputInventory);
        List<Pair<List<Integer>, List<List<ItemStack>>>> recipesIngredients = maidRecipesManager.getRecipesIngredients();
        boolean returnedBucket = DrinkBeerBarrelInventory.hasReturnedBucket(inventory);

        boolean actionable;
        String reason;
        if (outputReady) {
            actionable = true;
            reason = "output_ready";
        } else if (canModify && needsCups && hasMugs) {
            actionable = true;
            reason = "refill_cups";
        } else if (!brewing && !recipesIngredients.isEmpty()) {
            actionable = true;
            reason = "recipe_plan";
        } else if (returnedBucket) {
            actionable = true;
            reason = "returned_bucket";
        } else {
            actionable = false;
            reason = brewing ? "brewing" : recipesIngredients.isEmpty()
                    ? "no_recipe_plan" : "no_action";
        }

        DrinkBeerDiagnostics.evaluation(
                entityMaid, blockEntity, maidRecipesManager, actionable, reason,
                canModify, brewing, DrinkBeerBarrelAdapter.isOutputReady(blockEntity),
                needsCups, hasMugs, returnedBucket);
        return actionable;
    }

    @Override
    public void maidCookMake(ServerLevel serverLevel, EntityMaid entityMaid, BeerBarrelBlockEntity blockEntity, MaidRecipesManager<BrewingRecipe> maidRecipesManager) {
        DrinkBeerDiagnostics.Snapshot before = DrinkBeerDiagnostics.snapshot(
                entityMaid, blockEntity, maidRecipesManager);
        extractOutputStack(getContainer(blockEntity), maidRecipesManager.getOutputInv(), blockEntity);
        extractInputStack(getContainer(blockEntity), maidRecipesManager.getInputInv(), blockEntity);
        tryInsertItem(serverLevel, entityMaid, blockEntity, maidRecipesManager);

        maidRecipesManager.syncInv();
        DrinkBeerDiagnostics.action(
                entityMaid, blockEntity, before,
                DrinkBeerDiagnostics.snapshot(entityMaid, blockEntity, maidRecipesManager));
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
    public void tryInsertItem(ServerLevel serverLevel, EntityMaid entityMaid, BeerBarrelBlockEntity blockEntity, MaidRecipesManager<BrewingRecipe> maidRecipesManager) {
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
