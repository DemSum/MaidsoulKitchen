package com.github.wallev.maidsoulkitchen.task.cook.cuisine.cuisine;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import dev.xkmc.cuisinedelight.content.block.CuisineSkilletBlockEntity;
import dev.xkmc.cuisinedelight.content.logic.CookedFoodData;
import dev.xkmc.cuisinedelight.content.logic.CookingData;
import dev.xkmc.cuisinedelight.content.recipe.BaseCuisineRecipe;
import dev.xkmc.cuisinedelight.init.registrate.CDItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Source: 58ec08ec CuisineBe (MIT), native skillet binding. Its inventory methods were empty
 * stubs because this device stores CookingData, not item slots. Keep actual native state and
 * native ingredient/PlateItem interactions here; never fabricate a Handler or result inventory.
 * Manager owns all physical transfers. Replaces beta MaidCuisineMakeTask device mutations. */
public class CuisineBe extends CookBeBase<CuisineSkilletBlockEntity> {
    public CuisineBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity entity) { return entity instanceof CuisineSkilletBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { throw new UnsupportedOperationException("Cuisine uses native CookingData, not item slots"); }
    @Override public int getIngredientSize() { return dev.xkmc.cuisinedelight.init.data.CDConfig.SERVER.maxIngredient.get(); }
    @Override public int getResultSlot() { throw new UnsupportedOperationException("Cuisine uses native plate interaction"); }
    @Override public boolean hasInputs() { return !be.cookingData.contents.isEmpty(); }
    @Override public boolean recMatch() { return hasInputs(); }
    @Override public boolean cookStateMatch() { return be.canCook(); }
    @Override public boolean hasResult() { return hasInputs() && be.cookingData.contents.stream().allMatch(entry -> entry.getStage(be.cookingData) != dev.xkmc.cuisinedelight.content.logic.transform.Stage.RAW); }
    @Override public ItemStack getResult() {
        if (!hasInputs()) return ItemStack.EMPTY;
        var data = be.cookingData.immutable().mutable(); data.stir(serverLevel.getGameTime(), 0);
        return BaseCuisineRecipe.findBestMatch(serverLevel, CookedFoodData.of(data));
    }
    public boolean insertIngredient(MaidItem material, MaidRec work, MaidCookManager<?> cm) {
        if (!cookStateMatch() || material.role() != MaidItem.Role.INGREDIENT || cm.peekMaidRec() != work) return false;
        for (int i = 0; i < material.count(); i++) {
            int before = be.cookingData.contents.size();
            cm.useItem(cm.getItem(material.item()::is), getPos(), cm.getInputInv(), work);
            if (be.cookingData.contents.size() != before + 1) { cm.clear(); return false; }
        }
        return true;
    }
    public boolean serve(MaidRec work, MaidCookManager<?> cm) {
        if (!hasInputs() || !cm.canTakeResult(getResult())) return false;
        var source = work == null ? cm.getItem(stack -> stack.is(CDItems.PLATE.get()))
                : cm.getItem(work.maidItems().stream().filter(item -> item.role() == MaidItem.Role.CONTAINER).findFirst().orElseThrow().item()::is);
        cm.useItem(source, getPos(), cm.getOutputInv(), work);
        return !hasInputs();
    }
    @Override public boolean extractResult(MaidCookManager<?> cm) { return serve(null, cm); }
    @Override public boolean takeInputs(MaidCookManager<?> cm) { throw new UnsupportedOperationException("Native skillet ingredients require serving or block removal"); }
    @Override public void markChanged() { be.sync(); }
}
