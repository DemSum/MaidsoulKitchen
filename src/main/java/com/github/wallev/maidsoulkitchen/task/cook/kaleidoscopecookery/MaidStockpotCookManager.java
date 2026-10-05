package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import java.util.*;

/** Source: 58ec08ec MaidConditionCookManager/MaidFurnaceCookManager (MIT). KC had no source
 * device. Half-pot component contents and proportional native recipe groups require device-bound
 * descriptors rather than RecipeType-only conditions. Retains the source bounded condition scan
 * and extra retry; all work lives in the inherited MaidCookManager queue and tick states. No Plan,
 * Storage, index queue or additional inventory owner survives the beta KC implementation. */
public final class MaidStockpotCookManager extends MaidCookManager<Recipe<StockpotInput>> {
    private final StockpotRecSerializerManager rsm = StockpotRecSerializerManager.INSTANCE;
    private boolean allowFlex;
    private int extraTryTime;
    public MaidStockpotCookManager(EntityMaid maid, TaskKcStockpot task) { super(task.getRecSerializerManager(), maid, task); }
    @Override protected com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.IMaidCookInventory createCookInventory(ItemStack hub) {
        return hub.isEmpty() ? new com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidInventory(maid, false)
                : new com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidCookBagInventory(maid, hub, true);
    }
    @Override public boolean checkAndInit() {
        if (!super.checkAndInit()) return false;
        boolean current = TaskKcStockpot.settings(maid).allowFlexRecipes();
        if (allowFlex != current) {
            allowFlex = current; invalidate(); requestPlanningRefresh();
            recsGenerate.setRecs(getValidRecipesFor(rsm.getRecipes(level)));
        }
        return true;
    }
    @Override protected List<MKRecipe<Recipe<StockpotInput>>> getValidRecipesFor(List<MKRecipe<Recipe<StockpotInput>>> recipes) {
        var settings = TaskKcStockpot.settings(maid);
        return recipes.stream().filter(recipe -> rsm.enabled(recipe.id(), level, settings)).toList();
    }
    @Override public boolean isRecipeEnabled(net.minecraft.resources.ResourceLocation id) { return rsm.enabled(id, level, TaskKcStockpot.settings(maid)); }
    @Override protected boolean retainInput(ItemStack stack) { return rsm.retain(stack, level, TaskKcStockpot.settings(maid)); }
    /** Verified beta StockpotWorkStorage.prepare/available allowed replacement carriers and lids
     * from ingredient bindings after cooking. Reuse the sole manager's checked physical lookup. */
    @Override public com.github.wallev.maidsoulkitchen.task.cook.common.manager.GatherResult getItem(java.util.function.Predicate<ItemStack> predicate) {
        var item = super.getItem(predicate);
        return item.isFail() ? getBoundItem(com.github.wallev.maidsoulkitchen.inventory.container.item.BagType.INGREDIENT, predicate) : item;
    }
    @Override protected List<MKRecipe<Recipe<StockpotInput>>> getRecs() {
        List<MKRecipe<Recipe<StockpotInput>>> result = new ArrayList<>();
        var settings = TaskKcStockpot.settings(maid);
        for (var device : collectValidConditions()) {
            // Source: upstream device condition filtering. KC cannot accept a second batch
            // while cooking/finished; exclude those descriptors rather than call it a shortage.
            int status = device.getStatus();
            if (status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.PUT_SOUP_BASE
                    || status == com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot.PUT_INGREDIENT)
                result.addAll(rsm.forDevice(device, level, settings, this::reportPlanningRequirement));
        }
        return result;
    }
    /** Direct upstream collectConditions coordinate traversal. Common Move alone checks reachability. */
    private List<StockpotBlockEntity> collectValidConditions() {
        List<StockpotBlockEntity> conditions = new ArrayList<>();
        BlockPos center = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition().below();
        int range = (int) maid.getRestrictRadius(); var pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y <= ICookTask.VERTICAL_SEARCH_RANGE; y = y > 0 ? -y : 1 - y)
            for (int i = 0; i < range; ++i)
                for (int x = 0; x <= i; x = x > 0 ? -x : 1 - x)
                    for (int z = x < i && x > -i ? i : 0; z <= i; z = z > 0 ? -z : 1 - z) {
                        pos.setWithOffset(center, x, y + 1, z);
                        if (!maid.isWithinRestriction(pos) || !level.isLoaded(pos)
                                || !com.github.wallev.maidsoulkitchen.api.task.cook.ICookTargetTask.isWithinOwnerRange(maid, pos)) continue;
                        if (level.getBlockEntity(pos) instanceof StockpotBlockEntity pot
                                && com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks.isAvailable(level, pos, maid)) conditions.add(pot);
                    }
        return conditions;
    }
    /** Same ten-descriptor tick limit. Add accepted work immediately to the inherited queue so a
     * later group cannot plan a second simultaneous batch for the same physical pot. */
    @Override public void tickGenerateRecs() {
        if (!checkAndInit() || runState != 2) return;
        for (var descriptor : recsGenerate.tickRun()) {
            var deviceRecipe = (StockpotRecSerializerManager.DeviceRecipe) descriptor;
            if (maidRecs.stream().anyMatch(work -> StockpotBe.isBoundTo(work, deviceRecipe.device))) continue;
            maidRecs.addAll(rsm.createMaidRecs(List.of(descriptor), recsGenerate.getAvailable(), (recipe, range) -> {},
                    recipe -> true, use -> !hasCulinaryHub || hubItemDown.read(use),
                    done -> { if (done) recsGenerate.markDone(); }, task.getUid(), getGeneration()));
        }
    }
    @Override public MaidRec peekMaidRec(CookBeBase<?> device) {
        if (getRunState() != 0) return null;
        var work = peekMaidRec(rec -> StockpotBe.isBoundTo(rec, device.getBe()));
        if (work != null && !((StockpotBe) device).matchesWork(work)) { invalidate(); return null; }
        if (work != null) extraTryTime = 0;
        else if (extraTryTime++ > 20) { extraTryTime = 0; invalidate(); }
        return work;
    }
}
