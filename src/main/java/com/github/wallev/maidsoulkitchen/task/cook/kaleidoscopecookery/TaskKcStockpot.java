package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.StockpotTaskData;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotInput;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/** Source: 58ec08ec TaskFurnace conditional-manager/Be/Rule dispatch (MIT). KC stockpots had
 * no upstream device; current native API and verified local ordinary/Flex/lid/portion behavior
 * adapt that same chain. Replaces independent Move/Work/Storage/Plan, retaining one inherited queue. */
@com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.KC_STOCKPOT)
public final class TaskKcStockpot extends ICookTask<StockpotBlockEntity, Recipe<StockpotInput>> {
    public TaskKcStockpot() { StockpotAdapter.verifyApi(); }
    /** Source: preserved neo WIP TaskKcStockpot metadata, absent from 1.20.1.
     * Keep silence, the TLM work-point flag and no favour gate in the unified device task. */
    @Override public net.minecraft.sounds.SoundEvent getAmbientSound(EntityMaid maid) { return null; }
    @Override public boolean workPointTask(EntityMaid maid) { return true; }
    @Override public boolean hasEnoughFavor(EntityMaid maid) { return true; }
    @Override public java.util.List<com.mojang.datafixers.util.Pair<String, java.util.function.Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        return java.util.List.of();
    }
    @Override public boolean showRecipeAmountBubbles() { return false; }
    @Override protected CookBeBase<StockpotBlockEntity> createCookBe(EntityMaid maid) { return new StockpotBe(maid); }
    @Override protected AbstractCookRule<StockpotBlockEntity, Recipe<StockpotInput>> createCookRule() { return StockpotCookRule.INSTANCE; }
    @Override protected RecSerializerManager<Recipe<StockpotInput>> createRecSerializerManager() { return StockpotRecSerializerManager.INSTANCE; }
    @Override protected MaidCookManager<Recipe<StockpotInput>> createRecipesManager(EntityMaid maid, CookBeBase<StockpotBlockEntity> be) {
        return new MaidStockpotCookManager(maid, this);
    }
    @Override public ResourceLocation getUid() { return com.github.wallev.maidsoulkitchen.task.TaskInfo.KC_STOCKPOT.uid; }
    @Override public ItemStack getIcon() { return ModItems.STOCKPOT.get().getDefaultInstance(); }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() {
        throw new UnsupportedOperationException("Legacy KC key stores a filter; KitchenData performs its one-way migration");
    }
    @Override public java.util.List<String> getDescription(EntityMaid maid) {
        return java.util.List.of("task.maidsoulkitchen.kaleidoscope_stockpot.desc", "task.maidsoulkitchen.kaleidoscope_stockpot.desc.lid");
    }
    @Override public net.minecraft.world.MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        if (!maid.level().isClientSide) com.github.wallev.maidsoulkitchen.entity.data.inner.task.KitchenData.sync(maid);
        return new net.minecraft.world.SimpleMenuProvider((id, inventory, player) ->
                new com.github.wallev.maidsoulkitchen.inventory.container.maid.StockpotRecipeFilterContainer(id, inventory, maid.getId()), getName());
    }
    static StockpotTaskData settings(EntityMaid maid) {
        var stored = com.github.wallev.maidsoulkitchen.entity.data.inner.task.KitchenData.getStockpotSettings(maid);
        return new StockpotTaskData(stored.filter(), com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES.get());
    }
}
