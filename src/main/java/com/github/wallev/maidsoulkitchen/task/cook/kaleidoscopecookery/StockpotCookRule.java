package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotInput;
import net.minecraft.world.item.crafting.Recipe;
import java.util.List;

/** Source: 58ec08ec NormalCookRule output -> invalid-input cleanup -> input acceptance (MIT).
 * KC half pots/real lid recovery adapt the native device boundary only. Cooking clocks remain
 * entirely native; complete cover acknowledgment commits the one MaidRec. Deletes beta Work. */
public final class StockpotCookRule extends AbstractCookRule<StockpotBlockEntity, Recipe<StockpotInput>> {
    public static final StockpotCookRule INSTANCE = new StockpotCookRule();
    @Override public boolean canMoveTo(CookBeBase<StockpotBlockEntity> device, MaidCookManager<Recipe<StockpotInput>> cm) {
        var be = (StockpotBe) device;
        if (be.hasResult()) {
            var carrier = StockpotAdapter.carrier(be.getBe(), be.getBe().getLevel()).orElse(null);
            return carrier != null && be.canReturnLid(cm) && cm.canAcceptNativeResults(List.of(be.getResult()))
                    && (carrier.isEmpty() || !cm.getItem(carrier::test).isFail());
        }
        if (be.snapshot().status() == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.COOKING)
            return !be.snapshot().covered() && be.cookStateMatch() && !cm.getItem(StockpotAdapter::isLid).isFail();
        if (be.canTakeInputs(cm)) return true;
        var work = cm.peekMaidRec(be);
        return work != null && be.cookStateMatch() && be.canReturnLid(cm) && cm.hasMaterials(work)
                && cm.canAcceptNativeResults(List.of(work.result().copyWithCount(1)));
    }
    @Override public void cookMake(CookBeBase<StockpotBlockEntity> device, MaidCookManager<Recipe<StockpotInput>> cm) {
        var be = (StockpotBe) device; boolean changed = false;
        if (be.hasResult()) changed = be.extractResult(cm);
        else if (be.snapshot().status() == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.COOKING) {
            if (!be.snapshot().covered() && be.cookStateMatch()) changed = be.cover(cm, null);
        } else {
            if (be.canTakeInputs(cm)) changed = be.takeInputs(cm);
            var work = cm.peekMaidRec(be);
            if (work != null && be.insertInputs(work, cm)) changed |= cm.commitMaidRec(work);
        }
        if (changed) { be.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
