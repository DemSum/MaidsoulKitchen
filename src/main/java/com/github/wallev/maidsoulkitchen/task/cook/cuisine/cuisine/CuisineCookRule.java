package com.github.wallev.maidsoulkitchen.task.cook.cuisine.cuisine;

import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.TickCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import dev.xkmc.cuisinedelight.content.block.CuisineSkilletBlockEntity;
import dev.xkmc.cuisinedelight.content.logic.IngredientConfig;
import dev.xkmc.cuisinedelight.content.recipe.BaseCuisineRecipe;
import dev.xkmc.cuisinedelight.init.registrate.CDItems;
import net.minecraft.world.InteractionHand;

/** Source: 58ec08ec CuisineCookRule (MIT), same ingredient timing, stirring and final plate
 * lifecycle. Source/beta's processTickStacks copied live inputs and polled before acceptance.
 * Derive due inputs directly from the sole manager-owned MaidRec; Rule holds only a work
 * reference and lifecycle clocks. The plate stays reserved until actual native serving succeeds.
 * Source equality missed ingredients due within its initial ten-tick lead; include those at
 * start. Native PlateItem handles actual food and skillet reset. Deletes beta dedicated Make. */
public class CuisineCookRule extends TickCookRule<CuisineSkilletBlockEntity, BaseCuisineRecipe<?>> {
    private static final CuisineCookRule INSTANCE = new CuisineCookRule();
    private MaidRec work;
    private int tickAll, tickMax, tickSpace;
    public static CuisineCookRule getInstance() { return INSTANCE; }
    @Override protected CuisineCookRule create() { return new CuisineCookRule(); }
    private static boolean tool(net.minecraft.world.item.ItemStack stack) { return stack.is(CDItems.SPATULA.get()); }
    @Override public boolean canMoveTo(CookBeBase<CuisineSkilletBlockEntity> base, MaidCookManager<BaseCuisineRecipe<?>> cm) {
        var cookBe = (CuisineBe) base;
        return cookBe.hasResult() && cm.canTakeResult(cookBe.getResult()) && !cm.getItem(stack -> stack.is(CDItems.PLATE.get())).isFail()
                || !cookBe.hasInputs() && cookBe.cookStateMatch() && cm.hasMaidRecs() && !cm.getItem(CuisineCookRule::tool).isFail();
    }
    private int deadline(MaidItem material) {
        var entry = IngredientConfig.get().getEntry(material.item().stack());
        return entry.min_time == 0 ? 0 : Math.max(0, tickMax - entry.min_time - 10);
    }
    private boolean insertDue(CuisineBe cookBe, MaidCookManager<BaseCuisineRecipe<?>> cm, int due) {
        for (MaidItem material : work.maidItems()) if (material.role() == MaidItem.Role.INGREDIENT && deadline(material) == due
                && !cookBe.insertIngredient(material, work, cm)) return false;
        return true;
    }
    @Override public void cookMake(CookBeBase<CuisineSkilletBlockEntity> base, MaidCookManager<BaseCuisineRecipe<?>> cm) {
        init(base, cm); var cookBe = (CuisineBe) base;
        if (cookBe.hasResult()) { cookBe.extractResult(cm); stop(); return; }
        work = cm.peekMaidRec();
        if (work == null || !cm.equipTool(CuisineCookRule::tool)) { stop(); return; }
        if (work.maidItems().stream().filter(material -> material.role() == MaidItem.Role.INGREDIENT).mapToInt(MaidItem::count).sum() > cookBe.getIngredientSize()) { cm.clear(); stop(); return; }
        tickAll = 0; tickMax = 0; tickSpace = Integer.MAX_VALUE;
        for (MaidItem material : work.maidItems()) if (material.role() == MaidItem.Role.INGREDIENT) {
            var entry = IngredientConfig.get().getEntry(material.item().stack());
            if (entry == null) { cm.clear(); stop(); return; }
            tickMax = Math.max(tickMax, entry.min_time); tickSpace = Math.min(tickSpace, Math.max(1, entry.stir_time));
        }
        if (!insertDue(cookBe, cm, 0)) stop();
    }
    @Override public void tickCookMake(CookBeBase<CuisineSkilletBlockEntity> base, MaidCookManager<BaseCuisineRecipe<?>> cm) {
        var cookBe = (CuisineBe) base;
        if (work == null || cm.peekMaidRec() != work || !cookBe.cookStateMatch() || !tool(maid.getMainHandItem())) { stop(); return; }
        tickAll++;
        if (!insertDue(cookBe, cm, tickAll)) { stop(); return; }
        if ((tickAll + 10) % tickSpace == 0) {
            var silk = maid.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH);
            be.stir(maid.level().getGameTime(), maid.getMainHandItem().getEnchantmentLevel(silk) > 0 ? 20 : 0);
            maid.swing(InteractionHand.MAIN_HAND);
        }
        if (tickAll - 10 >= tickMax && cookBe.serve(work, cm)) { cm.commitMaidRec(work); stop(); }
    }
    @Override public void tickStop(CookBeBase<CuisineSkilletBlockEntity> base, MaidCookManager<BaseCuisineRecipe<?>> cm) {
        cm.backpackTool(); work = null; tickAll = tickMax = tickSpace = 0; super.tickStop(base, cm);
    }
}
