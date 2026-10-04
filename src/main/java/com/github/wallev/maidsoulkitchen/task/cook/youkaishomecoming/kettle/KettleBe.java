package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.kettle;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.KettleBlockAccessor;
import dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
/** Source: 58ec08ec KettleBe.java (MIT). Same meal, container, water and heated device contract.
 * NeoForge Handler/Holder lookup and the verified local native water map/FakePlayer adapt 1.21.
 * Replaces beta TaskYhcTeaKettle operations; no recipe, input or refill queue is owned here. */
public class KettleBe extends CookBeBase<KettleBlockEntity> {
    public KettleBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof KettleBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return be.getInventory(); }
    @Override public int getIngredientSize() { return 4; }
    @Override public int getResultSlot() { return KettleBlockEntity.OUTPUT_SLOT; }
    @Override public ItemStack getMeal() { return be.getMeal().copy(); }
    @Override public ItemStack getNeedContainer() { return be.getContainer().copy(); }
    @Override public int getContainerSlot() { return KettleBlockEntity.CONTAINER_SLOT; }
    public boolean hasFluid() { return be.getWater() >= KettleBlockEntity.WATER_BOTTLE; }
    @Override public boolean recMatch() {
        return serverLevel.getRecipeManager().getRecipeFor(dev.xkmc.youkaishomecoming.init.registrate.YHBlocks.KETTLE_RT.get(), new RecipeWrapper(be.getInventory()), serverLevel).isPresent();
    }
    public boolean canSupplyFluid(MaidCookManager<?> cm) { return !cm.getItem(this::isWaterSource).isFail(); }
    private boolean isWaterSource(ItemStack stack) {
        return ((KettleBlockAccessor) be.getBlockState().getBlock()).tlmk$getMap().get().keySet().stream().anyMatch(ingredient -> ingredient.test(stack));
    }
    /** Source WaterFdPotCookRule.useItem, retaining the existing local native interaction fix.
     * Confirm real water gain; source queryItemStack() defaulted to zero and never supplied water. */
    public boolean replenishFluid(MaidCookManager<?> cm) {
        if (hasFluid()) return false;
        int previous = be.getWater();
        cm.useItem(cm.getItem(this::isWaterSource), getPos());
        return be.getWater() > previous;
    }
    @Override public boolean cookStateMatch() { return be.isHeated(); }
    @Override public void markChanged() { defaultChanged(); }
}
