package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetCycle;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Searches once through reachable standing nodes and selects a usable steamer beside one. */
final class MaidSteamerMoveTask extends MaidCheckRateTask {
    private static final float MOVEMENT_SPEED = 0.6F;
    private static final int MAX_DELAY_TICKS = 120;
    private static final int[] INTERACTION_HEIGHT_OFFSETS = SteamerAdapter.interactionHeightOffsets();
    private final CookTargetCycle targetCycle = new CookTargetCycle();

    MaidSteamerMoveTask() {
        super(ImmutableMap.of(
                MkMemories.WORK_POS.get(), MemoryStatus.VALUE_ABSENT
        ));
        setMaxCheckRate(MAX_DELAY_TICKS);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        SteamerWorkStorage storage = SteamerWorkStorage.forMaid(maid);
        storage.flushOutputs();
        storage.sync();
        searchForSideApproach(level, maid, storage, filter(maid));
    }

    private void searchForSideApproach(
            ServerLevel level,
            EntityMaid maid,
            SteamerWorkStorage storage,
            RecipeFilterData recipeFilter
    ) {
        Set<BlockPos> checkedSteamers = new HashSet<>();
        Selection selection = new Selection(targetCycle);
        SteamerApproachSearch.find(level, maid, pos -> selectAdjacentSteamer(
                level, maid, pos, storage, recipeFilter, checkedSteamers, selection));
        Target target = selection.result();
        if (target == null) {
            BlockPos searchCenter = maid.hasRestriction()
                    ? maid.getRestrictCenter() : maid.blockPosition();
            CookTargetMemory.guideBackToWorkArea(maid, searchCenter, MOVEMENT_SPEED);
            return;
        }
        if (!CookWorkLocks.tryClaim(level, target.workPos(), maid)) return;
        CookTargetMemory.remember(
                maid,
                target.walkPos(),
                target.workPos(),
                MOVEMENT_SPEED,
                0
        );
        targetCycle.recordSelection(target.workPos().asLong());
        setNextCheckTickCount(5);
    }

    private boolean selectAdjacentSteamer(
            ServerLevel level,
            EntityMaid maid,
            BlockPos approachPos,
            SteamerWorkStorage storage,
            RecipeFilterData filter,
            Set<BlockPos> checkedSteamers,
            Selection selection
    ) {
        BlockPos.MutableBlockPos steamerPos = new BlockPos.MutableBlockPos();
        for (int yOffset : INTERACTION_HEIGHT_OFFSETS) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (!SteamerSearchGeometry.isSideOffset(xOffset, zOffset)) {
                        continue;
                    }
                    steamerPos.setWithOffset(approachPos, xOffset, yOffset, zOffset);
                    if (!maid.isWithinRestriction(steamerPos)
                            || !withinOwnerRange(maid, steamerPos)
                            || !level.isLoaded(steamerPos)
                            || !SteamerAdapter.supports(level.getBlockState(steamerPos))) {
                        continue;
                    }

                    BlockPos immutableSteamerPos = steamerPos.immutable();
                    if (!checkedSteamers.add(immutableSteamerPos)) {
                        continue;
                    }
                    BlockEntity blockEntity = level.getBlockEntity(steamerPos);
                    if (!SteamerAdapter.supports(blockEntity)
                            || !shouldUseSteamer(level, blockEntity, filter, storage)
                            || !CookWorkLocks.isAvailable(level, immutableSteamerPos, maid)) {
                        continue;
                    }
                    if (selection.offer(new Target(approachPos.immutable(), immutableSteamerPos))) return true;
                }
            }
        }
        return false;
    }

    private static boolean shouldUseSteamer(
            ServerLevel level,
            BlockEntity blockEntity,
            RecipeFilterData filter,
            SteamerWorkStorage storage
    ) {
        Predicate<ItemStack> placeable = stack ->
                SteamerAdapter.canPlaceFood(blockEntity, level, stack, filter::allows);
        return SteamerAdapter.inspect(blockEntity, level)
                .filter(snapshot -> snapshot.accessible() && snapshot.covered())
                .map(snapshot -> {
                    if (snapshot.canTakeFood()) {
                        return storage.canAcceptOutputs(snapshot.items());
                    }
                    return snapshot.hasHeatSource()
                            && snapshot.hasEmptySlot()
                            && storage.canSupplyIngredient(placeable);
                })
                .orElse(false);
    }

    private static RecipeFilterData filter(EntityMaid maid) {
        return maid.getOrCreateData(DataRegister.KC_STEAMER, RecipeFilterData.DEFAULT);
    }

    private static boolean withinOwnerRange(EntityMaid maid, BlockPos pos) {
        if (maid.isHomeModeEnable()) {
            return true;
        }
        LivingEntity owner = maid.getOwner();
        return owner != null && pos.closerToCenterThan(owner.position(), 8.0);
    }

    private record Target(BlockPos walkPos, BlockPos workPos) {
    }

    private static final class Selection {
        private final CookTargetCycle targetCycle;
        private Target preferred;
        private Target fallback;

        private Selection(CookTargetCycle targetCycle) {
            this.targetCycle = targetCycle;
        }

        private boolean offer(Target candidate) {
            if (targetCycle.prefers(candidate.workPos().asLong())) {
                preferred = candidate;
                return true;
            }
            if (fallback == null) fallback = candidate;
            return false;
        }

        private Target result() {
            return preferred != null ? preferred : fallback;
        }
    }
}
