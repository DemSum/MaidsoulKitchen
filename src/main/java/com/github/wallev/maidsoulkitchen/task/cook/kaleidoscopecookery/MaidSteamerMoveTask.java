package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.init.MkMemories;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetCycle;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.ReachableCookDeviceSearch;
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
    private static final int MAX_STACK_LAYERS = SteamerAdapter.maxHeatedLayers();
    private final CookTargetCycle targetCycle = new CookTargetCycle();

    MaidSteamerMoveTask() {
        super(ImmutableMap.of(MkMemories.WORK_POS.get(), MemoryStatus.VALUE_ABSENT));
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
        BlockPos searchCenter = maid.hasRestriction()
                ? maid.getRestrictCenter()
                : maid.blockPosition().below();
        int searchRange = (int) maid.getRestrictRadius();
        ReachableCookDeviceSearch.findCustom(
                level,
                maid,
                searchCenter,
                searchRange,
                targetCycle,
                (approach, offerSteamer) -> selectAdjacentSteamer(
                        level, maid, approach, storage, recipeFilter,
                        checkedSteamers, offerSteamer)
        ).ifPresent(result ->
                rememberTarget(level, maid, result.walkPos(), result.workPos()));
    }

    private void rememberTarget(ServerLevel level, EntityMaid maid, BlockPos approach, BlockPos steamer) {
        if (steamer == null || !CookWorkLocks.tryClaim(level, steamer, maid)) {
            return;
        }
        targetCycle.recordSelection(steamer.asLong());
        CookTargetMemory.remember(maid, approach, steamer, MOVEMENT_SPEED, 0);
        setNextCheckTickCount(5);
    }

    private boolean selectAdjacentSteamer(
            ServerLevel level,
            EntityMaid maid,
            BlockPos approachPos,
            SteamerWorkStorage storage,
            RecipeFilterData filter,
            Set<BlockPos> checkedSteamers,
            Predicate<BlockPos> offerSteamer
    ) {
        if (!maid.isWithinRestriction(approachPos)) {
            return false;
        }
        BlockPos.MutableBlockPos steamerPos = new BlockPos.MutableBlockPos();
        // The maid stands beside the bottom of a continuous stack. Each layer
        // above remains a distinct work target, but an upper floor is never an
        // approach point for that stack.
        for (int yOffset = 0; yOffset < MAX_STACK_LAYERS; yOffset++) {
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
                    if (!isBottomLevelApproach(level, approachPos, immutableSteamerPos)) {
                        continue;
                    }
                    if (!checkedSteamers.add(immutableSteamerPos)) {
                        continue;
                    }
                    BlockEntity blockEntity = level.getBlockEntity(steamerPos);
                    if (!SteamerAdapter.supports(blockEntity)
                            || !CookWorkLocks.isAvailable(level, immutableSteamerPos, maid)
                            || !shouldUseSteamer(level, blockEntity, filter, storage)) {
                        continue;
                    }
                    if (offerSteamer.test(immutableSteamerPos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBottomLevelApproach(
            ServerLevel level,
            BlockPos approachPos,
            BlockPos steamerPos
    ) {
        BlockPos cursor = steamerPos;
        int continuousLayersBelow = 0;
        for (int depth = 1; depth < MAX_STACK_LAYERS; depth++) {
            BlockPos below = cursor.below();
            if (!level.isLoaded(below) || !SteamerAdapter.supports(level.getBlockState(below))) {
                break;
            }
            cursor = below;
            continuousLayersBelow++;
        }
        return SteamerSearchGeometry.isBottomLevelApproach(
                approachPos.getY(), steamerPos.getY(), continuousLayersBelow);
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

}
