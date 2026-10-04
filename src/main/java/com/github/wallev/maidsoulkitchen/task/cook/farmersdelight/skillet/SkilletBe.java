package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.skillet;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.block.SkilletBlock;

/** New-device adaptation: 1.20 had no FD skillet task. Uses upstream DryingRackBe native-action
 * insertion and NormalCookRule lifecycle with existing 1.21 addItemToCook/FakePlayer boundary.
 * Preserve native whole-stack cooking and spawned outputs; manager extracts actual planned
 * inputs and checks full acceptance. Deletes beta TaskFdSkillet/MaidSkilletMakeTask's live split,
 * fallback recipe loop and shadow cookCount. Native device owns all cooking timers and output. */
public class SkilletBe extends CookBeBase<SkilletBlockEntity> {
    public SkilletBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity entity) { return entity instanceof SkilletBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return (IItemHandlerModifiable) be.getInventory(); }
    @Override public int getIngredientSize() { return 1; }
    @Override public int getResultSlot() { throw new UnsupportedOperationException("Native skillet spawns cooked output entities"); }
    @Override public boolean hasResult() { return false; }
    @Override public boolean cookStateMatch() { return be.isHeated() && !be.getBlockState().getValue(SkilletBlock.WATERLOGGED); }
    @Override public boolean recMatch() {
        return hasInputs() && serverLevel.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(be.getStoredStack()), serverLevel).isPresent();
    }
    @Override public boolean insertInputs(MaidRec work, MaidCookManager<?> cm) {
        if (hasInputs() || !cookStateMatch()) return false;
        var player = ((IAddonMaid) maid).tlmk$getFakePlayer();
        if (player == null || player.get() == null) return false;
        return cm.insertInput(work, stack -> {
            int before = be.getStoredStack().getCount();
            int requested = stack.getCount();
            try { return be.addItemToCook(stack, player.get()); }
            finally {
                // Native addItemToCook copies before updating timers/sounds. If a later native
                // notification throws, refund only physical input that never entered the device.
                int accepted = net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, be.getStoredStack())
                        ? Math.max(0, be.getStoredStack().getCount() - before) : 0;
                stack.setCount(Math.max(0, requested - accepted));
            }
        });
    }
    @Override public void markChanged() { defaultChanged(); }
}
