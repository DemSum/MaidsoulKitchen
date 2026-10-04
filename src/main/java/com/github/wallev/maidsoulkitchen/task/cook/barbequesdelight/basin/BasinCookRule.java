package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.basin;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.TickCookRule;
import com.mao.barbequesdelight.content.block.BasinBlockEntity;
import com.mao.barbequesdelight.content.recipe.SkeweringRecipe;
import net.minecraft.world.InteractionHand;

/** Source: 58ec08ec BasinCookRule (MIT), five-tick native skewering lifecycle. Source/beta
 * hand aliases and early polling are replaced by the manager's physical unit transaction.
 * Only actual native assembly commits work; full output preserves inputs and work. Existing
 * leftovers return through manager transfers. Deletes beta dedicated Basin Make behavior. */
public class BasinCookRule extends TickCookRule<BasinBlockEntity, SkeweringRecipe<?>> {
    private static final BasinCookRule INSTANCE = new BasinCookRule();
    public static BasinCookRule getInstance() { return INSTANCE; }
    @Override protected BasinCookRule create() { return new BasinCookRule(); }
    @Override public boolean canMoveTo(CookBeBase<BasinBlockEntity> base, MaidCookManager<SkeweringRecipe<?>> cm) {
        if (base.hasInputs()) return true;
        var work = cm.peekMaidRec(); return work != null && cm.canTakeResult(work.result());
    }
    @Override public void cookMake(CookBeBase<BasinBlockEntity> base, MaidCookManager<SkeweringRecipe<?>> cm) {
        init(base, cm); tick = 0;
        if (base.hasInputs()) { base.takeInputs(cm); cm.syncInv(); stop(); }
    }
    @Override public void tickCookMake(CookBeBase<BasinBlockEntity> base, MaidCookManager<SkeweringRecipe<?>> cm) {
        if (tick++ % 5 != 0) return;
        var work = cm.peekMaidRec();
        if (work == null) { stop(); return; }
        if (!cm.canTakeResult(work.result())) return;
        if (base.insertInputs(work, cm)) { cm.commitMaidRec(work); maid.swing(InteractionHand.MAIN_HAND); }
        stop();
    }
}
