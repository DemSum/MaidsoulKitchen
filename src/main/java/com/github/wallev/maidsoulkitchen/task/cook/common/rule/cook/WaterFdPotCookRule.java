package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.kettle.KettleBe;
import dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity;
import dev.xkmc.youkaishomecoming.content.pot.kettle.KettleRecipe;
import net.minecraft.world.item.ItemStack;
/** Source: 58ec08ec WaterFdPotCookRule.java (MIT), meal/container -> output -> input ->
 * native water supply -> cleanup lifecycle. The current kettle-specific water API is kept at
 * KettleBe instead of importing empty optional fluid hooks. Handler receipts and real water checks
 * fix default-zero extraction, poll-before-insertion and action success without device acceptance.
 * Replaces beta TaskYhcTeaKettle execution. No separate work/refill queue exists. */
public class WaterFdPotCookRule extends AbstractCookRule<KettleBlockEntity, KettleRecipe> {
    private static final WaterFdPotCookRule INSTANCE = new WaterFdPotCookRule();
    public static WaterFdPotCookRule getInstance() { return INSTANCE; }
    @Override public boolean canMoveTo(CookBeBase<KettleBlockEntity> cookBe, MaidCookManager<KettleRecipe> cm) {
        KettleBe pot = (KettleBe) cookBe;
        if (pot.hasResult() && cm.canTakeResult(pot.getResult())) return true;
        ItemStack needed = pot.getNeedContainer(), current = pot.getNowContainer();
        if (pot.hasMeal() && cm.getCookInv().hasOutputAvailableSlot() && !needed.isEmpty()
                && (current.isEmpty() || !current.is(needed.getItem())) && !cm.getItem(stack -> stack.is(needed.getItem())).isFail()) return true;
        if (!current.isEmpty() && !pot.hasInputs() && !pot.hasMeal() && cm.getCookInv().hasInputAvailableSlot()) return true;
        if (pot.hasInputs() && !pot.recMatch() && cm.getCookInv().hasInputAvailableSlot()) return true;
        if (!pot.cookStateMatch()) return false;
        if (pot.recMatch() && !pot.hasFluid() && pot.canSupplyFluid(cm)) return true;
        return !pot.hasInputs() && !pot.hasMeal() && cm.hasMaidRecs() && (pot.hasFluid() || pot.canSupplyFluid(cm));
    }
    @Override public void cookMake(CookBeBase<KettleBlockEntity> cookBe, MaidCookManager<KettleRecipe> cm) {
        KettleBe pot = (KettleBe) cookBe; boolean changed = false;
        if (pot.hasMeal() && cm.getCookInv().hasOutputAvailableSlot()) {
            ItemStack needed = pot.getNeedContainer(), current = pot.getNowContainer();
            if (!current.isEmpty() && !current.is(needed.getItem())) changed |= cm.takeItem(pot.getInv(), pot.getContainerSlot(), cm.getInputInv(), stack -> true) > 0;
            if (!needed.isEmpty() && pot.getNowContainer().isEmpty()) changed |= cm.insertItem(cm.getItem(stack -> stack.is(needed.getItem())), pot.getInv(), pot.getContainerSlot(), pot.getMeal().getCount()) > 0;
        }
        if (pot.hasResult()) {
            boolean taken = pot.extractResult(cm); if (taken) pot.awardExp(); changed |= taken;
        }
        if (pot.cookStateMatch() && !pot.hasInputs() && !pot.hasMeal() && (pot.hasFluid() || pot.canSupplyFluid(cm))) {
            var rec = cm.peekMaidRec();
            if (rec != null && pot.insertInputs(rec, cm)) changed |= cm.commitMaidRec(rec);
        }
        if (pot.recMatch() && pot.cookStateMatch() && !pot.hasFluid()) changed |= pot.replenishFluid(cm);
        if (!pot.getNowContainer().isEmpty() && !pot.hasMeal() && !pot.hasInputs()) changed |= cm.takeItem(pot.getInv(), pot.getContainerSlot(), cm.getInputInv(), stack -> true) > 0;
        if (pot.hasInputs() && !pot.recMatch()) changed |= pot.takeInputs(cm);
        if (changed) { pot.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
