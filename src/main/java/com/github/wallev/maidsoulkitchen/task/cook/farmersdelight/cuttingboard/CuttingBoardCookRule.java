package com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cuttingboard;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.TickCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.crafting.RecipeHolder;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import java.util.Optional;
/** Source: 58ec08ec CuttingBoardCookRule.java (MIT). Keeps per-maid start, five-tick alternating
 * place/process actions, stored-item continuation and stop cleanup. Native tool/Handler APIs are
 * retained; the manager lends tools and commits work only after actual board insertion.
 * Fixes upstream early polling, discarded insertion remainders and stale process state.
 * Replaces beta TaskFdCuttingBoard and MaidCuttingMakeTask execution. */
public class CuttingBoardCookRule extends TickCookRule<CuttingBoardBlockEntity, CuttingBoardRecipe> {
    private static final CuttingBoardCookRule INSTANCE = new CuttingBoardCookRule();
    private boolean maidHand;
    private ItemDefinition processItem;
    public static CuttingBoardCookRule getInstance() { return INSTANCE; }
    @Override public boolean canMoveTo(CookBeBase<CuttingBoardBlockEntity> cookBe, MaidCookManager<CuttingBoardRecipe> cm) {
        return !cookBe.getBe().getStoredItem().isEmpty() ? getBoardStackRecipe(cookBe.getBe(), cm).isPresent() : cm.hasMaidRecs();
    }
    @Override public void cookMake(CookBeBase<CuttingBoardBlockEntity> cookBe, MaidCookManager<CuttingBoardRecipe> cm) {
        init(cookBe, cm);
        if (!be.getStoredItem().isEmpty()) {
            var stored = getBoardStackRecipe(be, cm);
            if (stored.isEmpty() || !cm.equipTool(stored.get().value().getTool()::test)) { stop(); return; }
            processItem = ItemDefinition.of(be.getStoredItem()); maidHand = true;
        } else {
            var rec = cm.peekMaidRec();
            if (rec == null) { stop(); return; }
            var tool = rec.maidItems().stream().filter(item -> item.role() == MaidItem.Role.TOOL).findFirst();
            if (tool.isEmpty() || !cm.equipTool(tool.get().item()::is)) { stop(); return; }
            processItem = rec.maidItems().stream().filter(item -> item.role() == MaidItem.Role.INGREDIENT).findFirst().orElseThrow().item();
            maidHand = false;
        }
    }
    @Override public boolean tickCan(CookBeBase<CuttingBoardBlockEntity> cookBe, MaidCookManager<CuttingBoardRecipe> cm) {
        return super.tickCan(cookBe, cm) && !maid.getMainHandItem().isEmpty() && processItem != null
                && (!be.getStoredItem().isEmpty() || cm.hasMaidRecs());
    }
    @Override public void tickCookMake(CookBeBase<CuttingBoardBlockEntity> cookBe, MaidCookManager<CuttingBoardRecipe> cm) {
        if (tick++ % 5 != 0) return;
        if (maidHand) {
            var current = getBoardStackRecipe(be, cm);
            if (current.isEmpty() || !current.get().value().getTool().test(maid.getMainHandItem())) { stop(); return; }
            be.processStoredItemUsingTool(maid.getMainHandItem(), null); maid.swing(InteractionHand.MAIN_HAND);
            if (be.getStoredItem().isEmpty()) maidHand = false;
        } else {
            var rec = cm.peekMaidRec();
            if (rec == null || !be.getStoredItem().isEmpty()) { stop(); return; }
            var tool = rec.maidItems().stream().filter(item -> item.role() == MaidItem.Role.TOOL).findFirst();
            if (tool.isEmpty() || !tool.get().item().is(maid.getMainHandItem())) { stop(); return; }
            if (cookBe.insertInputs(rec, cm) && cm.commitMaidRec(rec)) {
                processItem = ItemDefinition.of(be.getStoredItem()); maidHand = true; maid.swing(InteractionHand.OFF_HAND);
            } else stop();
        }
        be.setChanged();
    }
    private Optional<RecipeHolder<CuttingBoardRecipe>> getBoardStackRecipe(CuttingBoardBlockEntity board, MaidCookManager<CuttingBoardRecipe> cm) {
        return board.getLevel().getRecipeManager().getAllRecipesFor(cm.getRecSerializerManager().getRecipeType()).stream()
                .filter(holder -> cm.isRecipeEnabled(holder.id()) && holder.value().getIngredients().getFirst().test(board.getStoredItem())
                        && (holder.value().getTool().test(cm.getMaid().getMainHandItem()) || !cm.getItem(holder.value().getTool()::test).isFail()))
                .findFirst();
    }
    @Override public void tickStop(CookBeBase<CuttingBoardBlockEntity> cookBe, MaidCookManager<CuttingBoardRecipe> cm) {
        try { cm.backpackTool(); } finally { super.tickStop(cookBe, cm); processItem = null; maidHand = false; }
    }
    @Override protected TickCookRule<CuttingBoardBlockEntity, CuttingBoardRecipe> create() { return new CuttingBoardCookRule(); }
}
