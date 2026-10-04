package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.AbstractCookRule;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Source: 58ec08ec TaskYhcDryingRack Be/Rule/RSM dispatch (MIT). KC had no 1.20 task;
 * its verified native adapter and stacked-device geometry adapt that same main chain.
 * Replaces the independent MaidSteamerMove/Work/SteamerWorkStorage execution entirely. */
@com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.KC_STEAMER)
public final class TaskKcSteamer extends ICookTask<SteamerBlockEntity, SteamerRecipe> {
    public TaskKcSteamer() { SteamerAdapter.verifyApi(); }
    @Override protected CookBeBase<SteamerBlockEntity> createCookBe(EntityMaid maid) { return new SteamerBe(maid); }
    @Override protected AbstractCookRule<SteamerBlockEntity, SteamerRecipe> createCookRule() { return SteamerCookRule.INSTANCE; }
    @Override protected RecSerializerManager<SteamerRecipe> createRecSerializerManager() { return SteamerRecSerializerManager.INSTANCE; }
    /** Preserve the old native KC backpack view: takeFood temporarily owns the main hand.
     * Including that hand in its output view would overwrite an inserted result on restoration. */
    @Override protected com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<SteamerRecipe> createRecipesManager(EntityMaid maid, CookBeBase<SteamerBlockEntity> be) {
        return new com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<>(recSerializerManager, maid, this) {
            @Override protected com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.IMaidCookInventory createCookInventory(ItemStack hub) {
                return hub.isEmpty() ? new com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidInventory(maid, false)
                        : new com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidCookBagInventory(maid, hub);
            }
        };
    }
    @Override public ResourceLocation getUid() { return com.github.wallev.maidsoulkitchen.task.TaskInfo.KC_STEAMER.uid; }
    @Override public ItemStack getIcon() { return ModItems.STEAMER.get().getDefaultInstance(); }
    @Override public double getCloseEnoughDist() { return SteamerAdapter.STACK_INTERACTION_DISTANCE; }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData> getCookDataKey() {
        throw new UnsupportedOperationException("Legacy KC key stores a filter; KitchenData performs its one-way migration");
    }
    @Override public net.minecraft.world.MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        return new net.minecraft.world.SimpleMenuProvider((id, inventory, player) ->
                new com.github.wallev.maidsoulkitchen.inventory.container.maid.SteamerRecipeFilterContainer(id, inventory, maid.getId()), getName());
    }
}
