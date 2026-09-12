package com.github.wallev.maidsoulkitchen.task.cook.minecraft;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.MaidRecipesManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.github.wallev.maidsoulkitchen.task.cook.common.TaskBaseContainerCook;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cbaccessor.IAbstractFurnaceAccessor;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;


@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.FURNACE)
public class TaskFurnace extends TaskBaseContainerCook<AbstractFurnaceBlockEntity, AbstractCookingRecipe> {
    @Override
    public boolean isEnable(EntityMaid maid) {
return true;
    }

    @Override
    public boolean isHeated(AbstractFurnaceBlockEntity be) {
        return true;
    }

    @Override
    public boolean beInnerCanCook(Container inventory, AbstractFurnaceBlockEntity be) {
        return false;
    }

    @Override
    public int getOutputSlot() {
        return 2;
    }

    @Override
    public int getInputSize() {
        return 1;
    }

    @Override
    public Container getContainer(AbstractFurnaceBlockEntity be) {
        return be;
    }

    @Override
    public boolean isCookBE(BlockEntity blockEntity) {
        return blockEntity instanceof AbstractFurnaceBlockEntity;
    }

    @Override
    @SuppressWarnings("unchecked, rawtypes")
    public RecipeType<AbstractCookingRecipe> getRecipeType() {
        return (RecipeType) RecipeType.SMELTING;
    }

    @Override
    public ResourceLocation getUid() {
        return TaskInfo.FURNACE.uid;
    }

    @Override
    public ItemStack getIcon() {
        return Items.FURNACE.getDefaultInstance();
    }

    // 为了兼容熔炉类的（熔炉、烟熏炉、高炉）
    // 就直接实时获取配方原料了，而且也因为就一个输入口（燃料不算），运算起来也还行
    // 之前写的，先这样把，找个时间再看看...
    @Override
    public boolean maidShouldMoveTo(ServerLevel serverLevel, EntityMaid entityMaid, AbstractFurnaceBlockEntity blockEntity, MaidRecipesManager<AbstractCookingRecipe> maidRecipesManager) {
        IItemHandlerModifiable availableInv = maidRecipesManager.getInputInv();
        IItemHandlerModifiable outputInv = maidRecipesManager.getOutputInv();

        int[] resultSlots = blockEntity.getSlotsForFace(Direction.DOWN);

        for (int resultSlot : resultSlots) {
            ItemStack resultStack = blockEntity.getItem(resultSlot);
            if (resultStack.isEmpty()) {
                continue;
            }
            if (!blockEntity.canTakeItemThroughFace(resultSlot, resultStack, Direction.DOWN)) {
                continue;
            }
            if (CookInventoryTransactions.canInsertAll(outputInv, resultStack)) return true;
        }

        boolean hasFuel = hasFuel(blockEntity) || getFuel(availableInv).isPresent();
        if (!hasFuel) {
            return false;
        }

        RecipeType<? extends AbstractCookingRecipe> recipeType = ((IAbstractFurnaceAccessor) blockEntity).tlmk$getRecipeType();
        for (int slot : blockEntity.getSlotsForFace(Direction.UP)) {
            ItemStack stack = blockEntity.getItem(slot);
            if (!stack.isEmpty()) continue;
            if (getAnyCookableItem(entityMaid, availableInv, recipeType, maidRecipesManager,
                    cookable -> blockEntity.canPlaceItemThroughFace(slot, cookable, Direction.UP))
                    .isPresent()) {
                return true;
            }

        }


        return false;
    }

    private boolean hasFuel(AbstractFurnaceBlockEntity furnace) {
        int[] fuelSlots = furnace.getSlotsForFace(Direction.NORTH);
        for (int fuelSlot : fuelSlots) {
            ItemStack fuelSlotStack = furnace.getItem(fuelSlot);
            if (!fuelSlotStack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private Optional<ItemStack> getAnyCookableItem(EntityMaid maid, IItemHandlerModifiable availableInv,
                                                   RecipeType<? extends AbstractCookingRecipe> recipeType,
                                                   MaidRecipesManager<AbstractCookingRecipe> recipeManager,
                                                   Predicate<ItemStack> predicate) {
        for (int i = 0; i < availableInv.getSlots(); ++i) {
            ItemStack slotStack = availableInv.getStackInSlot(i);
            if (!slotStack.isEmpty()
                    && getRecipe(maid, slotStack, recipeType)
                    .filter(holder -> recipeManager.isRecipeEnabled(holder.id())).isPresent()
                    && predicate.test(slotStack)) {
                return Optional.of(slotStack);
            }
        }
        return Optional.empty();
    }

    private Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> getRecipe(
            EntityMaid maid, ItemStack stack, RecipeType<? extends AbstractCookingRecipe> recipeType) {
        return maid.level.getRecipeManager().getRecipeFor(
                recipeType, new SingleRecipeInput(stack), maid.level);
    }

    @Override
    public void maidCookMake(ServerLevel serverLevel, EntityMaid entityMaid, AbstractFurnaceBlockEntity blockEntity, MaidRecipesManager<AbstractCookingRecipe> maidRecipesManager) {
        IItemHandlerModifiable availableInv = maidRecipesManager.getInputInv();
        tryExtractItem(blockEntity, entityMaid, maidRecipesManager.getOutputInv());

        if (!tryInsertFuel(availableInv, blockEntity)){
            return;
        }

        tryInsertCookable(entityMaid, availableInv, blockEntity, maidRecipesManager);

    }

    private void tryExtractItem(AbstractFurnaceBlockEntity furnace, EntityMaid maid, IItemHandlerModifiable availableInv ) {
        int[] resultSlots = furnace.getSlotsForFace(Direction.DOWN);

        for (int resultSlot : resultSlots) {
            ItemStack resultStack = furnace.getItem(resultSlot);
            if (resultStack.isEmpty()) {
                continue;
            }
            if (!furnace.canTakeItemThroughFace(resultSlot, resultStack, Direction.DOWN)) {
                continue;
            }
            ItemStack copy = resultStack.copy();
            if (!CookInventoryTransactions.canInsertAll(availableInv, copy)) continue;
            furnace.setItem(resultSlot, ItemStack.EMPTY);
            CookInventoryTransactions.insertAll(availableInv, copy);
            furnace.setChanged();

            // to-do
            // 给女仆经验
        }

        pickupAction(maid);
    }

    private boolean tryInsertFuel(IItemHandlerModifiable availableInv, AbstractFurnaceBlockEntity furnace) {
        int[] fuelSlots = furnace.getSlotsForFace(Direction.NORTH);
        for (int fuelSlot : fuelSlots) {
            ItemStack fuelSlotStack = furnace.getItem(fuelSlot);
            if (!fuelSlotStack.isEmpty()) {
                continue;
            }
            Optional<ItemStack> fuel = getFuel(availableInv);
            if (fuel.isEmpty()) {
                return false;
            }
            if (!furnace.canPlaceItemThroughFace(fuelSlot, fuel.get(), Direction.NORTH)) {
                continue;
            }
            int moved = Math.min(fuel.get().getCount(), fuel.get().getMaxStackSize());
            furnace.setItem(fuelSlot, fuel.get().copyWithCount(moved));
            fuel.get().shrink(moved);
            furnace.setChanged();
            break;
        }
        return true;
    }

    private void tryInsertCookable(EntityMaid maid, IItemHandlerModifiable availableInv,
                                   AbstractFurnaceBlockEntity furnace,
                                   MaidRecipesManager<AbstractCookingRecipe> recipeManager) {
        int[] materialSlots = furnace.getSlotsForFace(Direction.UP);
        for (int materialSlot : materialSlots) {
            ItemStack materialSlotStack = furnace.getItem(materialSlot);
            if (!materialSlotStack.isEmpty()) {
                continue;
            }
            Optional<ItemStack> material = getCookable(
                    maid, availableInv, ((IAbstractFurnaceAccessor) furnace).tlmk$getRecipeType(), recipeManager);
            if (material.isEmpty()) {
                continue;
            }
            if (!furnace.canPlaceItemThroughFace(materialSlot, material.get(), Direction.UP)) {
                continue;
            }
            int moved = Math.min(material.get().getCount(), material.get().getMaxStackSize());
            furnace.setItem(materialSlot, material.get().copyWithCount(moved));
            material.get().shrink(moved);
            furnace.setChanged();
            break;
        }

        pickupAction(maid);
    }

    private Optional<ItemStack> getCookable(EntityMaid maid, IItemHandlerModifiable availableInv,
                                            RecipeType<? extends AbstractCookingRecipe> recipeType,
                                            MaidRecipesManager<AbstractCookingRecipe> recipeManager) {
        for (int i = 0; i < availableInv.getSlots(); ++i) {
            ItemStack slotStack = availableInv.getStackInSlot(i);
            if (getRecipe(maid, slotStack, recipeType)
                    .filter(holder -> recipeManager.isRecipeEnabled(holder.id())).isPresent()) {
                return Optional.of(slotStack);
            }
        }
        return Optional.empty();
    }

    private Optional<ItemStack> getFuel(IItemHandlerModifiable availableInv ) {
        for(int i = 0; i < availableInv.getSlots(); ++i) {
            ItemStack stack = availableInv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (isFuel(stack)) {
                return Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    private boolean isFuel(ItemStack stack) {
        return AbstractFurnaceBlockEntity.isFuel(stack);
    }

    @Override
    public TaskDataKey<CookData> getCookDataKey() {
        return DataRegister.MC_FURNACE;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<RecipeHolder<AbstractCookingRecipe>> getRecipeHolders(Level level) {
        List<RecipeHolder<AbstractCookingRecipe>> recipes = new ArrayList<>();
        recipes.addAll((List) level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING));
        recipes.addAll((List) level.getRecipeManager().getAllRecipesFor(RecipeType.SMOKING));
        recipes.addAll((List) level.getRecipeManager().getAllRecipesFor(RecipeType.BLASTING));
        return recipes;
    }

}
