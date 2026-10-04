package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import dev.xkmc.youkaishomecoming.content.pot.ferment.*;
import dev.xkmc.youkaishomecoming.init.registrate.YHBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import static dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlock.OPEN;
/** Source: 58ec08ec FermentationCookBe.java (MIT). Same item/tank recipe checks and close-after-input
 * responsibility. Current L2Core Container requires InvWrapper; RecipeHolder retains native identity.
 * Native fluid insertion is verified before item slots and close; failed or partial work is revoked
 * without refunding accepted tank contents. The source and beta polled before both inputs accepted.
 * Native solid results were treated as leftover inputs; identify actual results from the catalog,
 * keeping full output in the device. Replaces beta TaskYhcFermentationTank execution. */
public class FermentationCookBe extends CookBeBase<FermentationTankBlockEntity> {
    public FermentationCookBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof FermentationTankBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return new InvWrapper(be.items); }
    @Override public int getIngredientSize() { return be.items.getContainerSize(); }
    public FluidStack getFluidStack() { return be.fluids.getFluidInTank(0).copy(); }
    public boolean hasFluid() { return !getFluidStack().isEmpty(); }
    @Override public boolean cookStateMatch() { return be.inProgress() == 0; }
    @Override public boolean recMatch() {
        return (hasInputs() || hasFluid()) && serverLevel.getRecipeManager().getRecipeFor(YHBlocks.FERMENT_RT.get(), new FermentationDummyContainer(be.items, be.fluids), serverLevel).isPresent();
    }
    private boolean isNativeResult(ItemStack stack) {
        return !recMatch() && be.inProgress() == 0 && FermentationRecSerializerManager.getInstance().getRecipes(serverLevel).stream()
                .anyMatch(description -> description.rec() instanceof SimpleFermentationRecipe recipe && recipe.results.stream()
                        .anyMatch(result -> !result.isEmpty() && ItemStack.isSameItemSameComponents(stack, result)));
    }
    @Override public boolean hasResult() {
        for (int slot = 0; slot < getIngredientSize(); slot++) if (isNativeResult(be.items.getItem(slot))) return true;
        return false;
    }
    @Override public int getResultSlot() {
        for (int slot = 0; slot < getIngredientSize(); slot++) if (isNativeResult(be.items.getItem(slot))) return slot;
        throw new IllegalStateException("Fermentation device has no native solid result");
    }
    @Override public boolean takeInputs(MaidCookManager<?> cm) {
        boolean changed = false;
        for (int slot = 0; slot < getIngredientSize(); slot++) if (!isNativeResult(be.items.getItem(slot)))
            changed |= cm.takeItem(getInv(), slot, cm.getInputInv(), stack -> true) > 0;
        return changed;
    }
    public void setOpen(boolean open) { serverLevel.setBlockAndUpdate(getPos(), be.getBlockState().setValue(OPEN, open)); }
    /** Source insertFluidItems -> insertInputs -> close. Version/native transaction adaptation only;
     * neither interaction success nor a preview may replace actual accepted tank/item state. */
    @Override public boolean insertInputs(MaidRec rec, MaidCookManager<?> cm) {
        if (rec == null || hasInputs() || hasFluid() || !cookStateMatch() || cm.peekMaidRec() != rec) return false;
        if (rec.recipe().value() instanceof SimpleFermentationRecipe recipe && !recipe.inputFluid.isEmpty()
                && (recipe.outputFluid.getAmount() > 0 ? recipe.outputFluid.getAmount() : 1000) > be.fluids.getTankCapacity(0)) return false;
        java.util.Map<com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition, Integer> needed = new java.util.HashMap<>();
        rec.maidItems().forEach(material -> needed.merge(material.item(), material.count(), Integer::sum));
        if (needed.entrySet().stream().anyMatch(entry -> CookInventoryTransactions.count(cm.getInputInv(), entry.getKey()::is) < entry.getValue())) return false;
        setOpen(true);
        for (MaidItem material : rec.maidItems()) {
            if (material.role() != MaidItem.Role.FLUID) continue;
            for (int count = 0; count < material.count(); count++) {
                int before = getFluidStack().getAmount();
                cm.useItem(cm.getItem(material.item()::is), getPos(), cm.getInputInv(), rec);
                if (getFluidStack().getAmount() <= before) { cm.clear(); return false; }
            }
        }
        boolean fluidAccepted = rec.recipe().value() instanceof SimpleFermentationRecipe recipe
                && (recipe.inputFluid == null || recipe.inputFluid.isEmpty() || recipe.inputFluid.test(getFluidStack()));
        if (!fluidAccepted || !cm.insertInputs(rec, getInv(), 0, getIngredientSize(), true) || !recMatch()) { cm.clear(); return false; }
        setOpen(false); markChanged(); return true;
    }
    public boolean extractFluid(MaidCookManager<?> cm, com.github.wallev.maidsoulkitchen.task.cook.common.manager.GatherResult container) {
        setOpen(true); int before = getFluidStack().getAmount();
        cm.useItem(container, getPos(), cm.getOutputInv(), null);
        return getFluidStack().getAmount() < before;
    }
    @Override public void markChanged() { be.notifyTile(); }
}
