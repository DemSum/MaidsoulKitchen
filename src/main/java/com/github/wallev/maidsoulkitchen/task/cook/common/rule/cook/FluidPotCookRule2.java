package com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationCookBe;
import dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationRecipe;
import dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlockEntity;
import dev.xkmc.youkaishomecoming.content.item.fluid.SakeFluid;
import net.minecraft.world.item.ItemStack;
/** Source: 58ec08ec FluidPotCookRule2.java (MIT), cleanup -> fluid/item insertion -> container
 * extraction. Current native fermentation API stays in its Be; no optional empty fluid hooks,
 * duplicate queue or beta item-copy fallback is retained. Work commits only after both native inputs.
 * Solid-output handling fixes the source's output-to-input cleanup, without a second result cache. */
public class FluidPotCookRule2 extends AbstractCookRule<FermentationTankBlockEntity, FermentationRecipe<?>> {
    private static final FluidPotCookRule2 INSTANCE = new FluidPotCookRule2();
    public static FluidPotCookRule2 getInstance() { return INSTANCE; }
    @Override public boolean canMoveTo(CookBeBase<FermentationTankBlockEntity> cookBe, MaidCookManager<FermentationRecipe<?>> cm) {
        FermentationCookBe pot = (FermentationCookBe) cookBe;
        if (!pot.cookStateMatch()) return false;
        if (pot.hasResult() && cm.canTakeResult(pot.getResult())) return true;
        if (!pot.recMatch() && pot.hasFluid() && canExtractFluid(pot, cm)) return true;
        if (pot.hasInputs() && !pot.recMatch() && !pot.hasResult() && cm.getCookInv().hasInputAvailableSlot()) return true;
        return !pot.hasInputs() && !pot.hasFluid() && cm.hasMaidRecs();
    }
    private boolean canExtractFluid(FermentationCookBe pot, MaidCookManager<FermentationRecipe<?>> cm) {
        var fluid = pot.getFluidStack().getFluid();
        if (!hasFluidContainers(fluid, cm)) return false;
        if (fluid instanceof SakeFluid sake) return pot.getFluidStack().getAmount() >= sake.type.amount() && cm.canTakeResult(sake.type.asStack(1));
        return cm.getCookInv().hasOutputAvailableSlot();
    }
    @Override public void cookMake(CookBeBase<FermentationTankBlockEntity> cookBe, MaidCookManager<FermentationRecipe<?>> cm) {
        FermentationCookBe pot = (FermentationCookBe) cookBe;
        if (!pot.cookStateMatch()) return;
        boolean changed = false;
        for (int count = 0; count < pot.getIngredientSize() && pot.hasResult(); count++) {
            if (!pot.extractResult(cm)) break; changed = true;
        }
        if (!pot.recMatch() && pot.hasInputs()) changed |= pot.takeInputs(cm);
        if (!pot.hasInputs() && !pot.hasFluid()) {
            var rec = cm.peekMaidRec(); if (rec != null && pot.insertInputs(rec, cm)) changed |= cm.commitMaidRec(rec);
        }
        if (!pot.recMatch() && pot.hasFluid() && canExtractFluid(pot, cm))
            changed |= pot.extractFluid(cm, getFluidContainers(pot.getFluidStack().getFluid(), cm));
        if (changed) { pot.markChanged(); cm.getMaid().swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
    }
}
