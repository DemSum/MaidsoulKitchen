package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.grill;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import com.mao.barbequesdelight.content.block.GrillBlockEntity;
import com.mao.barbequesdelight.init.registrate.BBQDItems;
import com.mao.barbequesdelight.init.registrate.BBQDRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Source: 58ec08ec GrillBe and GrillCookRule (MIT), native entry binding/addItem/flip.
 * Source Be was empty; 1.21 getContainers constructs an aliased SimpleContainer snapshot and
 * cannot safely replace actual entry stacks. This stateless Handler maps extraction directly to
 * native entries; insertion uses native addItem to initialize duration. No copied grillStacks
 * or early polling survives from beta MaidGrillMakeTask. */
public class GrillBe extends CookBeBase<GrillBlockEntity> {
    public GrillBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity entity) { return entity instanceof GrillBlockEntity; }
    public boolean isResult(int slot) {
        var entry = be.entries[slot];
        return !entry.stack.isEmpty() && (entry.stack.is(BBQDItems.BURNT_FOOD.asItem()) || entry.flipped && entry.time >= entry.duration);
    }
    @Override public IItemHandlerModifiable getInv() {
        return new IItemHandlerModifiable() {
            @Override public int getSlots() { return be.entries.length; }
            @Override public ItemStack getStackInSlot(int slot) { return be.entries[slot].stack; }
            @Override public void setStackInSlot(int slot, ItemStack stack) { be.entries[slot].stack = stack; markChanged(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (amount <= 0 || !isResult(slot)) return ItemStack.EMPTY;
                var actual = be.entries[slot].stack; var extracted = actual.copyWithCount(Math.min(amount, actual.getCount()));
                if (!simulate) { actual.shrink(extracted.getCount()); markChanged(); }
                return extracted;
            }
        };
    }
    @Override public int getIngredientSize() { return be.entries.length; }
    @Override public boolean hasResult() { for (int i = 0; i < getIngredientSize(); i++) if (isResult(i)) return true; return false; }
    @Override public int getResultSlot() {
        for (int i = 0; i < getIngredientSize(); i++) if (isResult(i)) return i;
        throw new IllegalStateException("Grill has no finished native entry");
    }
    @Override public boolean recMatch() {
        for (var entry : be.entries) if (!entry.stack.isEmpty() && serverLevel.getRecipeManager()
                .getRecipeFor(BBQDRecipes.RT_BBQ.get(), new SingleRecipeInput(entry.stack), serverLevel).isPresent()) return true;
        return false;
    }
    @Override public boolean cookStateMatch() { return be.isHeated(); }
    @Override public boolean insertInputs(MaidRec work, MaidCookManager<?> cm) {
        if (!cookStateMatch()) return false;
        return cm.insertInputs(work, stack -> {
            for (var entry : be.entries) if (entry.stack.isEmpty() && entry.addItem(be, stack.copyWithCount(1))) {
                stack.shrink(1); markChanged(); return true;
            }
            return false;
        });
    }
    @Override public void markChanged() { be.inventoryChanged(); }
}
