package com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.dryingrack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackBlockEntity;
import dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
/** Source: 58ec08ec DryingRackBe.java (MIT), native placeFood and sky/day/rain conditions.
 * The upstream loop could insert up to four items per inventory stack while consuming a larger
 * plan. Bounded work and manager extraction fix that defect; native placeFood still starts timing.
 * Native output is spawned into the world, so this Be never exposes a fake result slot.
 * Replaces beta TaskYhcDryingRack device execution. */
public class DryingRackBe extends CookBeBase<DryingRackBlockEntity> {
    public DryingRackBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity be) { return be instanceof DryingRackBlockEntity && canDryingRack(be); }
    public boolean canDryingRack() { return canDryingRack(be); }
    public boolean canDryingRack(BlockEntity device) {
        var level = device.getLevel(); var pos = device.getBlockPos();
        return level != null && level.canSeeSky(pos) && level.isDay() && !level.isRainingAt(pos);
    }
    @Override public IItemHandlerModifiable getInv() { return be.getInventory(); }
    @Override public int getIngredientSize() { return 4; }
    @Override public int getResultSlot() { throw new UnsupportedOperationException("Native drying spawns output entities"); }
    @Override public boolean hasResult() { return false; }
    @Override public boolean recMatch() { return hasInputs(); }
    @Override public boolean cookStateMatch() { return canDryingRack(); }
    @Override public boolean insertInputs(MaidRec rec, MaidCookManager<?> cm) {
        if (!canDryingRack() || hasInputs() || !(rec.recipe().value() instanceof DryingRackRecipe recipe)) return false;
        return cm.insertInputs(rec, stack -> be.placeFood(stack, recipe.getCookingTime()));
    }
    @Override public void markChanged() { defaultChanged(); }
}
