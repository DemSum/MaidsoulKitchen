package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.DrinkBeerBarrelAdapter;
import com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.DrinkBeerBarrelRules;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity;
import lekavar.lma.drinkbeer.registries.ItemRegistry;
import lekavar.lma.drinkbeer.registries.RecipeRegistry;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Source: 58ec08ec BeerBarrelBe.java (MIT). Native 1.4.1 public state API replaces private
 * status mixins. The validated local four-cup/returned-bucket rules are retained here; actual
 * movement of items belongs to MaidCookManager. Replaces beta TaskDbBeerBarrel inventory execution. */
@TaskClassAnalyzer(TaskInfo.DB_BEER)
public class BeerBarrelBe extends CookBeBase<BeerBarrelBlockEntity> {
    public BeerBarrelBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof BeerBarrelBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return new InvWrapper(be.getBrewingInventory()); }
    @Override public int getIngredientSize() { return 5; }
    @Override public int getResultSlot() { return 5; }
    @Override public boolean recMatch() {
        if (DrinkBeerBarrelAdapter.isBrewing(be)) return true;
        return serverLevel.getRecipeManager().getRecipeFor(RecipeRegistry.RECIPE_TYPE_BREWING.get(),
                be.getBrewingInventory(), serverLevel).isPresent();
    }
    @Override public boolean cookStateMatch() { return DrinkBeerBarrelAdapter.canModifyInputs(be); }
    @Override public boolean canTakeResult() { return DrinkBeerBarrelAdapter.isOutputReady(be); }
    @Override public void markChanged() { DrinkBeerBarrelAdapter.markChanged(be); }
    @Override public boolean hasInputs() {
        for (int slot = 0; slot < DrinkBeerBarrelRules.INGREDIENT_SLOTS; slot++)
            if (!getInv().getStackInSlot(slot).isEmpty()) return true;
        return false;
    }
    public boolean needsCups() {
        var cups = getInv().getStackInSlot(DrinkBeerBarrelRules.CUP_SLOT);
        return cookStateMatch() && DrinkBeerBarrelRules.needsCups(cups.getCount(), cups.isEmpty(), cups.is(ItemRegistry.EMPTY_BEER_MUG.get()));
    }
    public boolean fillCups(MaidCookManager<?> cm) {
        if (!needsCups()) return false;
        int missing = DrinkBeerBarrelRules.REQUIRED_CUPS - getInv().getStackInSlot(DrinkBeerBarrelRules.CUP_SLOT).getCount();
        var source = cm.getItem(stack -> stack.is(ItemRegistry.EMPTY_BEER_MUG.get()));
        return cm.insertItem(source, getInv(), DrinkBeerBarrelRules.CUP_SLOT, missing) > 0;
    }
    public boolean hasReturnedBucket() {
        for (int slot = 0; slot < DrinkBeerBarrelRules.INGREDIENT_SLOTS; slot++)
            if (getInv().getStackInSlot(slot).is(Items.BUCKET)) return true;
        return false;
    }
    public boolean takeReturnedBuckets(MaidCookManager<?> cm) {
        boolean changed = false;
        for (int slot = 0; slot < DrinkBeerBarrelRules.INGREDIENT_SLOTS; slot++)
            changed |= cm.takeItem(getInv(), slot, cm.getInputInv(), stack -> stack.is(Items.BUCKET)) > 0;
        return changed;
    }
}
