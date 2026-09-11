package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTargetTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.inventory.container.maid.SteamerRecipeFilterContainer;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
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
import java.util.function.Predicate;

/** Native Kaleidoscope Cookery steamer task, based on Public patch commit de47e15. */
@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.KC_STEAMER)
public final class TaskKcSteamer implements ICookTargetTask {
    public TaskKcSteamer() {
        SteamerAdapter.verifyApi();
    }

    @Override
    public ResourceLocation getUid() {
        return TaskInfo.KC_STEAMER.uid;
    }

    @Override
    public ItemStack getIcon() {
        return ModItems.STEAMER.get().getDefaultInstance();
    }

    @Override
    @Nullable
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return new ArrayList<>(List.of(
                Pair.of(5, new MaidSteamerMoveTask()),
                Pair.of(6, new MaidSteamerWorkTask())
        ));
    }

    // Present in newer TLM APIs. No @Override keeps source compatibility with 1.1.13.
    public boolean workPointTask(EntityMaid maid) {
        return true;
    }

    // Present in newer TLM APIs. No @Override keeps source compatibility with 1.1.13.
    public String getMaidActionSummary() {
        return "Load ingredients into prepared steamers and collect finished food";
    }

    @Override
    public MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        int maidId = maid.getId();
        return new SimpleMenuProvider(
                (containerId, inventory, player) ->
                        new SteamerRecipeFilterContainer(containerId, inventory, maidId),
                getName()
        );
    }

    static void workAt(EntityMaid maid, BlockPos pos) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        SteamerWorkStorage storage = SteamerWorkStorage.forMaid(maid);
        try {
            storage.flushOutputs();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            RecipeFilterData filter = maid.getOrCreateData(DataRegister.KC_STEAMER, RecipeFilterData.DEFAULT);
            boolean worked = SteamerAdapter.inspect(blockEntity, level)
                    .filter(snapshot -> snapshot.accessible() && snapshot.covered())
                    .map(snapshot -> {
                        if (snapshot.canTakeFood()) {
                            if (!storage.canAcceptOutputs(snapshot.items())) {
                                return false;
                            }
                            boolean taken = SteamerAdapter.takeReadyFoodTo(
                                    blockEntity, level, maid, storage.outputDestination());
                            if (taken) {
                                storage.flushOutputs();
                            }
                            return taken;
                        }
                        if (!snapshot.hasHeatSource() || !snapshot.hasEmptySlot()) {
                            return false;
                        }
                        Predicate<ItemStack> placeable = stack ->
                                SteamerAdapter.canPlaceFood(blockEntity, level, stack, filter::allows);
                        if (!storage.prepareIngredient(placeable, snapshot.emptySlotCount())) {
                            return false;
                        }
                        int slot = SteamerAdapter.findPlaceableFoodSlot(
                                blockEntity, level, storage.ingredients(), filter::allows);
                        return SteamerAdapter.placeFoodFromSlot(
                                blockEntity, level, maid, storage.ingredients(), slot, filter::allows);
                    })
                    .orElse(false);
            if (worked) {
                maid.swing(InteractionHand.MAIN_HAND);
            }
        } finally {
            storage.sync();
        }
    }
}
