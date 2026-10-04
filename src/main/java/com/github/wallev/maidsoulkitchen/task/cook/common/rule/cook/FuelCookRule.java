package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace.FurnaceCookBe;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
/** Source: 58ec08ec FuelCookRule.java (MIT). Retains output/input/fuel/cleanup ordering.
 * Actual Handler receipts replace live-stack mutation and poll-before-acceptance. Returned fuel
 * containers are reclaimed even when valid ingredients remain (upstream bucket-blocking defect).
 * Replaces the beta furnace's independent per-slot recipe execution. */
public class FuelCookRule extends AbstractCookRule<AbstractFurnaceBlockEntity, AbstractCookingRecipe> {
    private static final FuelCookRule INSTANCE = new FuelCookRule();
    public static FuelCookRule getInstance() { return INSTANCE; }
    @Override public boolean canMoveTo(CookBeBase<AbstractFurnaceBlockEntity> cookBe, MaidCookManager<AbstractCookingRecipe> cm) {
        FurnaceCookBe furnace = (FurnaceCookBe) cookBe;
        if (furnace.hasResult() && cm.canTakeResult(furnace.getResult())) return true;
        var fuel = furnace.getInv().getStackInSlot(furnace.activeItemSlot());
        if (!fuel.isEmpty() && !AbstractFurnaceBlockEntity.isFuel(fuel) && cm.getCookInv().hasInputAvailableSlot()) return true;
        boolean canFuel = furnace.cookStateMatch() || !cm.getItem(AbstractFurnaceBlockEntity::isFuel).isFail();
        if (!furnace.hasInputs() && canFuel && cm.hasMaidRecs(furnace)) return true;
        if (furnace.recMatch() && !furnace.cookStateMatch() && canFuel) return true;
        return furnace.hasInputs() && !furnace.recMatch() && cm.getCookInv().hasInputAvailableSlot();
    }
    @Override public void cookMake(CookBeBase<AbstractFurnaceBlockEntity> cookBe, MaidCookManager<AbstractCookingRecipe> cm) {
        FurnaceCookBe furnace = (FurnaceCookBe) cookBe;
        boolean changed = false;
        if (furnace.hasResult()) { changed = furnace.extractResult(cm); if (changed) furnace.awardExp(); }
        var fuel = furnace.getInv().getStackInSlot(furnace.activeItemSlot());
        if (!fuel.isEmpty() && !AbstractFurnaceBlockEntity.isFuel(fuel))
            changed |= cm.takeItem(furnace.getInv(), furnace.activeItemSlot(), cm.getInputInv(), stack -> true) > 0;
        var source = cm.getItem(AbstractFurnaceBlockEntity::isFuel);
        if (!furnace.hasInputs() && (furnace.cookStateMatch() || !source.isFail())) {
            var rec = cm.peekMaidRec(furnace);
            if (rec != null && furnace.insertInputs(rec, cm)) changed |= cm.commitMaidRec(rec);
        }
        if (furnace.recMatch() && !furnace.cookStateMatch())
            changed |= cm.insertItem(source, furnace.getInv(), furnace.activeItemSlot(), 64) > 0;
        if (furnace.hasInputs() && !furnace.recMatch()) changed |= furnace.takeInputs(cm);
        if (!furnace.hasInputs() && !furnace.cookStateMatch())
            changed |= cm.takeItem(furnace.getInv(), furnace.activeItemSlot(), cm.getInputInv(), stack -> true) > 0;
        if (changed) { furnace.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
