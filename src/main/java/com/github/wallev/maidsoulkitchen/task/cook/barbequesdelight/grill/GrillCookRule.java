package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.grill;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.TickCookRule;
import com.mao.barbequesdelight.content.block.GrillBlockEntity;
import com.mao.barbequesdelight.content.recipe.GrillingRecipe;
import net.minecraft.world.InteractionHand;

/** Source: 58ec08ec GrillCookRule (MIT). Same burned/cooked extraction and native flip loop.
 * Source grillStack and beta grillStacks copied live materials and polled early. Each native
 * single entry accepts the manager's work before commit; waiting/finished entries remain native
 * state. Full outputs do not prevent flipping. Replaces beta dedicated Grill Make behavior. */
public class GrillCookRule extends TickCookRule<GrillBlockEntity, GrillingRecipe<?>> {
    private static final GrillCookRule INSTANCE = new GrillCookRule();
    public static GrillCookRule getInstance() { return INSTANCE; }
    @Override protected GrillCookRule create() { return new GrillCookRule(); }
    @Override public boolean canMoveTo(CookBeBase<GrillBlockEntity> base, MaidCookManager<GrillingRecipe<?>> cm) {
        var cookBe = (GrillBe) base;
        for (int i = 0; i < cookBe.getIngredientSize(); i++) {
            var entry = cookBe.getBe().entries[i];
            if (entry.canFlip() || cookBe.isResult(i) && cm.canTakeResult(entry.stack)) return true;
        }
        return cookBe.cookStateMatch() && cm.hasMaidRecs() && java.util.Arrays.stream(cookBe.getBe().entries).anyMatch(entry -> entry.stack.isEmpty());
    }
    @Override public void cookMake(CookBeBase<GrillBlockEntity> base, MaidCookManager<GrillingRecipe<?>> cm) {
        init(base, cm); var cookBe = (GrillBe) base;
        var work = cm.peekMaidRec();
        if (work != null && cookBe.insertInputs(work, cm)) cm.commitMaidRec(work);
    }
    @Override public void tickCookMake(CookBeBase<GrillBlockEntity> base, MaidCookManager<GrillingRecipe<?>> cm) {
        var cookBe = (GrillBe) base;
        boolean nothing = true;
        for (int i = 0; i < be.entries.length; i++) {
            var entry = be.entries[i];
            if (!entry.stack.isEmpty()) {
                if (entry.canFlip()) { entry.flip(be); maid.swing(InteractionHand.MAIN_HAND); }
                if (cookBe.isResult(i)) cm.takeItem(cookBe.getInv(), i, cm.getOutputInv(), stack -> true);
                if (!entry.stack.isEmpty()) nothing = false;
            } else {
                var work = cm.peekMaidRec();
                if (work != null && cookBe.insertInputs(work, cm)) { cm.commitMaidRec(work); maid.swing(InteractionHand.MAIN_HAND); nothing = false; }
            }
        }
        if (nothing) stop();
    }
}
