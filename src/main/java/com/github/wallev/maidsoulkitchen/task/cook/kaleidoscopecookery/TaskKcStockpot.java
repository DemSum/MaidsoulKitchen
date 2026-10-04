package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTargetTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.StockpotTaskData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.inventory.container.maid.StockpotRecipeFilterContainer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.KC_STOCKPOT)
public final class TaskKcStockpot implements ICookTargetTask {
    public TaskKcStockpot() { StockpotAdapter.verifyApi(); }
    @Override public ResourceLocation getUid() { return TaskInfo.KC_STOCKPOT.uid; }
    @Override public ItemStack getIcon() { return ModItems.STOCKPOT.get().getDefaultInstance(); }
    @Override @Nullable public SoundEvent getAmbientSound(EntityMaid maid) { return null; }
    @Override public boolean workPointTask(EntityMaid maid) { return true; }
    @Override public String getMaidActionSummary() { return "Prepare stockpots with real lids and collect finished soup"; }
    @Override public List<String> getDescription(EntityMaid maid) {
        return List.of("task.maidsoulkitchen.kaleidoscope_stockpot.desc",
                "task.maidsoulkitchen.kaleidoscope_stockpot.desc.lid");
    }
    @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(Pair.of(5, new MaidStockpotMoveTask()), Pair.of(6, new MaidStockpotWorkTask())));
    }
    @Override public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        // The persisted flag is only a server snapshot for the menu; the global config owns permission.
        if (!maid.level().isClientSide) maid.setAndSyncData(DataRegister.KC_STOCKPOT, settings(maid));
        return new SimpleMenuProvider((id, inventory, player) ->
                new StockpotRecipeFilterContainer(id, inventory, maid.getId()), getName());
    }

    static StockpotTaskData settings(EntityMaid maid) {
        var stored = maid.getOrCreateData(DataRegister.KC_STOCKPOT, StockpotTaskData.DEFAULT);
        return new StockpotTaskData(stored.filter(),
                com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES.get());
    }

    static boolean useful(BlockEntity entity, ServerLevel level, EntityMaid maid,
                          StockpotWorkStorage storage, StockpotRecipePlanner planner) {
        var snapshot = StockpotAdapter.inspect(entity, level).orElse(null);
        if (snapshot == null) return false;
        if (snapshot.covered() && !storage.canReturn(StockpotAdapter.lidToReturn(snapshot))) return false;
        if (snapshot.status() == IStockpot.FINISHED) {
            var carrier = StockpotAdapter.carrier(entity, level).orElse(null);
            return snapshot.portions() > 0 && !snapshot.result().isEmpty() && carrier != null
                    && storage.canOutput(snapshot.result().copyWithCount(1))
                    && (carrier.isEmpty() || planner.hasSupply(carrier::test));
        }
        if (snapshot.status() == IStockpot.COOKING) {
            return snapshot.heated() && !snapshot.covered()
                    && planner.hasSupply(StockpotAdapter::isLid);
        }
        if (snapshot.status() != IStockpot.PUT_SOUP_BASE && snapshot.status() != IStockpot.PUT_INGREDIENT) return false;
        var decision = planner.plan(snapshot);
        if (decision.outcome() == StockpotRecipePlanner.Outcome.READY) {
            return snapshot.heated() && storage.canOutput(decision.plan().preview());
        }
        if (decision.outcome() != StockpotRecipePlanner.Outcome.NO_ALLOWED_COMPLETION
                || !snapshot.hasIngredients() || !StockpotAdapter.canRetrieveIngredients(entity, maid)) return false;
        ItemStack last = lastIngredient(snapshot);
        ItemStack container = StockpotAdapter.ingredientContainer(last);
        return storage.canReturn(last) && (container.isEmpty()
                || planner.hasSupply(stack -> stack.is(container.getItem())));
    }

    static void workAt(EntityMaid maid, BlockPos pos) {
        if (!(maid.level() instanceof ServerLevel level) || !maid.getTask().getUid().equals(TaskInfo.KC_STOCKPOT.uid)) return;
        BlockEntity entity = level.getBlockEntity(pos);
        var snapshot = StockpotAdapter.inspect(entity, level).orElse(null);
        if (snapshot == null) return;
        StockpotWorkStorage storage = new StockpotWorkStorage(maid);
        ItemStack originalHand = maid.getMainHandItem();
        maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        try {
            storage.flush();
            var planner = new StockpotRecipePlanner(level, settings(maid), storage.available());
            if (snapshot.status() == IStockpot.FINISHED) collect(entity, level, maid, storage, snapshot);
            else if (snapshot.status() == IStockpot.COOKING) {
                if (!snapshot.covered() && snapshot.heated()) {
                    var availableLid = storage.available().stream().filter(StockpotAdapter::isLid).findFirst();
                    if (availableLid.isEmpty() || !storage.prepare(List.of(availableLid.get().copyWithCount(1)))) return;
                    ItemStack lid = storage.take(StockpotAdapter::isLid);
                    try { StockpotAdapter.cover(entity, level, maid, lid); }
                    finally { storage.returnInput(lid); }
                }
            } else if (snapshot.status() == IStockpot.PUT_SOUP_BASE || snapshot.status() == IStockpot.PUT_INGREDIENT) {
                var decision = planner.plan(snapshot);
                if (decision.outcome() == StockpotRecipePlanner.Outcome.READY && snapshot.heated()) {
                    load(entity, level, maid, storage, planner, snapshot, decision.plan());
                } else if (decision.outcome() == StockpotRecipePlanner.Outcome.NO_ALLOWED_COMPLETION && snapshot.hasIngredients()) {
                    clearIngredients(entity, level, maid, storage, snapshot);
                }
            }
            storage.storeUnused(planner::retain);
            maid.swing(InteractionHand.MAIN_HAND);
        } catch (RuntimeException failure) {
            MaidsoulKitchen.LOGGER.warn("Stockpot interaction stopped at {}", pos, failure);
        } finally {
            storage.returnInput(popHand(maid));
            maid.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
            storage.flush();
            storage.sync();
        }
    }

    private static void load(BlockEntity entity, ServerLevel level, EntityMaid maid, StockpotWorkStorage storage,
                             StockpotRecipePlanner planner, StockpotAdapter.Snapshot snapshot,
                             StockpotRecipePlanner.Plan plan) {
        if (!storage.canOutput(plan.preview()) || !storage.prepare(plan.supplies())) return;
        ItemStack lid;
        if (snapshot.covered()) {
            if (!storage.canReturn(StockpotAdapter.lidToReturn(snapshot)) || !StockpotAdapter.uncover(entity, level, maid)) return;
            lid = popHand(maid);
        } else lid = storage.take(plan.lid());
        if (!StockpotAdapter.isLid(lid)) { storage.returnInput(lid); return; }
        try {
            if (!plan.baseItem().isEmpty()) {
                if (!interact(maid, storage, plan.baseItem(), ModItems.STOCKPOT_LID.get().getDefaultInstance(), false,
                        stack -> StockpotAdapter.addSoupBase(entity, level, maid, stack))) return;
                var now = StockpotAdapter.inspect(entity, level).orElse(null);
                if (now == null || !now.soupBase().equals(plan.soupBase())) return;
            }
            for (ItemStack ingredient : plan.additions()) {
                if (!interact(maid, storage, ingredient, StockpotAdapter.ingredientContainer(ingredient), false,
                        stack -> StockpotAdapter.addIngredient(entity, level, maid, stack))) return;
            }
            var now = StockpotAdapter.inspect(entity, level).orElse(null);
            if (now != null && now.heated() && now.status() == IStockpot.PUT_INGREDIENT
                    && planner.uses(settings(maid)) && planner.permitsCompleted(now)) {
                StockpotAdapter.cover(entity, level, maid, lid);
            }
        } finally { storage.returnInput(lid); }
    }

    private static void collect(BlockEntity entity, ServerLevel level, EntityMaid maid, StockpotWorkStorage storage,
                                StockpotAdapter.Snapshot snapshot) {
        var carrier = StockpotAdapter.carrier(entity, level).orElse(null);
        if (carrier == null || !storage.canOutput(snapshot.result().copyWithCount(1))) return;
        if (!carrier.isEmpty()) {
            var available = storage.available().stream().filter(carrier::test).findFirst();
            if (available.isEmpty() || !storage.prepare(List.of(available.get().copyWithCount(1)))) return;
        }
        if (snapshot.covered()) {
            if (!storage.canReturn(StockpotAdapter.lidToReturn(snapshot)) || !StockpotAdapter.uncover(entity, level, maid)) return;
            storage.returnInput(popHand(maid));
        }
        for (int portion = 0; portion < 9; portion++) {
            var now = StockpotAdapter.inspect(entity, level).orElse(null);
            if (now == null || now.status() != IStockpot.FINISHED || now.portions() <= 0) return;
            ItemStack product = now.result().copyWithCount(1);
            if (!storage.canOutput(product)) return;
            ItemStack vessel = ItemStack.EMPTY;
            if (!carrier.isEmpty()) {
                var available = storage.available().stream().filter(carrier::test).findFirst();
                if (available.isEmpty()) return;
                vessel = available.get().copyWithCount(1);
                if (!storage.prepare(List.of(vessel))) return;
            }
            int before = now.portions();
            if (!interact(maid, storage, vessel, product, true,
                    stack -> StockpotAdapter.takeOne(entity, level, maid, stack))) return;
            var after = StockpotAdapter.inspect(entity, level).orElse(null);
            if (after == null || after.portions() != before - 1) return;
            storage.flush();
        }
    }

    private static void clearIngredients(BlockEntity entity, ServerLevel level, EntityMaid maid,
                                         StockpotWorkStorage storage, StockpotAdapter.Snapshot snapshot) {
        if (!StockpotAdapter.canRetrieveIngredients(entity, maid)) return;
        if (snapshot.covered()) {
            if (!storage.canReturn(StockpotAdapter.lidToReturn(snapshot)) || !StockpotAdapter.uncover(entity, level, maid)) return;
            storage.returnInput(popHand(maid));
        }
        for (int count = 0; count < 9; count++) {
            var now = StockpotAdapter.inspect(entity, level).orElse(null);
            if (now == null || now.status() != IStockpot.PUT_INGREDIENT || !now.hasIngredients()
                    || !StockpotAdapter.canRetrieveIngredients(entity, maid)) return;
            ItemStack ingredient = lastIngredient(now);
            ItemStack container = StockpotAdapter.ingredientContainer(ingredient);
            if (!container.isEmpty() && !storage.prepare(List.of(container))) return;
            ItemStack borrowed = container.isEmpty() ? ItemStack.EMPTY : storage.take(stack -> stack.is(container.getItem()));
            if (!container.isEmpty() && borrowed.isEmpty()) return;
            if (!storage.canReturn(ingredient)) { storage.returnInput(borrowed); return; }
            maid.setItemInHand(InteractionHand.MAIN_HAND, borrowed);
            try {
                if (!StockpotAdapter.removeIngredient(entity, level, maid)) return;
            } finally { storage.returnInput(popHand(maid)); }
        }
    }

    private static boolean interact(EntityMaid maid, StockpotWorkStorage storage, ItemStack expected,
                                    ItemStack receipt, boolean output, Function<ItemStack, Boolean> action) {
        ItemStack borrowed = expected.isEmpty() ? ItemStack.EMPTY : storage.take(expected);
        if (!expected.isEmpty() && borrowed.isEmpty()) return false;
        try {
            if (output ? !storage.canOutput(receipt) : !storage.canReturn(receipt)) return false;
            return action.apply(borrowed);
        } finally {
            ItemStack received = popHand(maid);
            if (output) storage.storeOutput(received); else storage.returnInput(received);
            storage.returnInput(borrowed);
            storage.sync();
        }
    }

    private static ItemStack popHand(EntityMaid maid) {
        ItemStack stack = maid.getMainHandItem();
        maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return stack;
    }

    private static ItemStack lastIngredient(StockpotAdapter.Snapshot snapshot) {
        List<ItemStack> inputs = snapshot.inputs();
        for (int index = inputs.size() - 1; index >= 0; index--) if (!inputs.get(index).isEmpty()) return inputs.get(index);
        return ItemStack.EMPTY;
    }
}
