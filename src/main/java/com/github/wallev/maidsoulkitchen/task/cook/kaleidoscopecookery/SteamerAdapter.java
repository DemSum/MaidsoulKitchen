package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Narrow adapter around Kaleidoscope Cookery's steamer API. Keeping all KC
 * implementation details here makes version drift fail in one place.
 *
 * <p>Native port of Maidsoul-Brewery-Public commit de47e15.</p>
 */
public final class SteamerAdapter {
    private static final int HALF_STEAMER_SLOTS = 4;
    private static final int FULL_STEAMER_SLOTS = 8;
    private static final int COOKING_COMPLETE = -1;

    private SteamerAdapter() {
    }

    public static void verifyApi() {
        if (!ISteamer.class.isAssignableFrom(SteamerBlockEntity.class)) {
            throw new IllegalStateException("Kaleidoscope Cookery SteamerBlockEntity no longer implements ISteamer");
        }
    }

    public static boolean supports(BlockEntity blockEntity) {
        return blockEntity instanceof SteamerBlockEntity;
    }

    public static boolean supports(BlockState blockState) {
        return blockState.getBlock() instanceof SteamerBlock;
    }

    public static Optional<Snapshot> inspect(BlockEntity blockEntity, Level level) {
        if (!(blockEntity instanceof SteamerBlockEntity steamer)) {
            return Optional.empty();
        }

        BlockState state = steamer.getBlockState();
        int configuredSlots = state.hasProperty(SteamerBlock.HALF) && state.getValue(SteamerBlock.HALF)
                ? HALF_STEAMER_SLOTS
                : FULL_STEAMER_SLOTS;
        int[] cookingProgress = steamer.getCookingProgress();
        int[] cookingTime = steamer.getCookingTime();
        int slotCount = Math.min(configuredSlots,
                Math.min(steamer.getItems().size(), Math.min(cookingProgress.length, cookingTime.length)));

        List<ItemStack> items = new ArrayList<>(slotCount);
        List<Integer> progress = new ArrayList<>(slotCount);
        List<Integer> times = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            items.add(steamer.getItems().get(slot).copy());
            progress.add(cookingProgress[slot]);
            times.add(cookingTime[slot]);
        }

        return Optional.of(new Snapshot(
                steamer.getBlockPos(),
                isAccessible(steamer, level),
                hasCoveredTop(steamer, level),
                steamer.hasHeatSource(level),
                items,
                progress,
                times
        ));
    }

    public static boolean canPlaceFood(
            BlockEntity blockEntity,
            Level level,
            ItemStack food,
            Predicate<ResourceLocation> recipeAllowed
    ) {
        if (!(blockEntity instanceof SteamerBlockEntity steamer) || food.isEmpty()) {
            return false;
        }
        Optional<Snapshot> snapshot = inspect(steamer, level);
        return snapshot.isPresent()
                && snapshot.get().accessible()
                && snapshot.get().covered()
                && snapshot.get().hasHeatSource()
                && snapshot.get().hasEmptySlot()
                && steamer.getSteamerRecipe(level, food)
                .map(recipe -> recipeAllowed.test(recipe.id()))
                .orElse(false);
    }

    public static boolean placeFood(
            BlockEntity blockEntity,
            Level level,
            LivingEntity user,
            ItemStack food,
            Predicate<ResourceLocation> recipeAllowed
    ) {
        if (!(blockEntity instanceof SteamerBlockEntity steamer)
                || !isCurrent(steamer, level)
                || !canPlaceFood(steamer, level, food, recipeAllowed)) {
            return false;
        }
        return ((ISteamer) steamer).placeFood(level, user, food);
    }

    public static int findPlaceableFoodSlot(
            BlockEntity blockEntity,
            Level level,
            IItemHandler inventory,
            Predicate<ResourceLocation> recipeAllowed
    ) {
        if (!(blockEntity instanceof SteamerBlockEntity steamer)) {
            return -1;
        }
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (canPlaceFood(steamer, level, stack, recipeAllowed)) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean placeFoodFromSlot(
            BlockEntity blockEntity,
            Level level,
            LivingEntity user,
            IItemHandlerModifiable inventory,
            int slot,
            Predicate<ResourceLocation> recipeAllowed
    ) {
        if (slot < 0 || slot >= inventory.getSlots()) {
            return false;
        }
        ItemStack stack = inventory.getStackInSlot(slot);
        boolean placed = placeFood(blockEntity, level, user, stack, recipeAllowed);
        if (placed) {
            // KC mutates the supplied stack; notify handlers whose stack was
            // modified in place so the inventory remains synchronized.
            inventory.setStackInSlot(slot, stack);
        }
        return placed;
    }

    public static List<RecipeOption> getRecipeOptions(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.STEAMER_RECIPE).stream()
                .map(recipe -> new RecipeOption(recipe.id(), recipe.value().getResult()))
                .sorted(java.util.Comparator.comparing(RecipeOption::id))
                .toList();
    }

    public static boolean takeReadyFoodTo(
            BlockEntity blockEntity,
            Level level,
            LivingEntity user,
            IItemHandler destination
    ) {
        if (!(blockEntity instanceof SteamerBlockEntity steamer) || !isCurrent(steamer, level)) {
            return false;
        }
        Optional<Snapshot> inspected = inspect(steamer, level);
        if (inspected.isEmpty()
                || !inspected.get().canTakeFood()
                || !canFitAll(destination, inspected.get().items())) {
            return false;
        }

        List<ItemStack> expected = inspected.get().items().stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        AABB captureArea = user.getBoundingBox().inflate(2.0);
        Set<UUID> existingDrops = new HashSet<>();
        for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, captureArea)) {
            existingDrops.add(itemEntity.getUUID());
        }
        boolean mainHandWasEmpty = user.getMainHandItem().isEmpty();

        if (!((ISteamer) steamer).takeFood(level, user)) {
            return false;
        }

        // KC gives the first result to a non-player's empty hand and drops the rest.
        if (mainHandWasEmpty) {
            ItemStack handStack = user.getMainHandItem();
            transferExpectedStack(handStack, expected, destination);
            user.setItemInHand(InteractionHand.MAIN_HAND, handStack.isEmpty() ? ItemStack.EMPTY : handStack);
        }

        for (ItemEntity itemEntity : level.getEntitiesOfClass(
                ItemEntity.class,
                captureArea,
                itemEntity -> !existingDrops.contains(itemEntity.getUUID())
        )) {
            ItemStack droppedStack = itemEntity.getItem();
            transferExpectedStack(droppedStack, expected, destination);
            if (droppedStack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(droppedStack);
            }
        }
        return true;
    }

    private static boolean isAccessible(SteamerBlockEntity steamer, Level level) {
        BlockPos above = steamer.getBlockPos().above();
        return !level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    private static boolean hasCoveredTop(SteamerBlockEntity steamer, Level level) {
        BlockPos cursor = steamer.getBlockPos();
        while (cursor.getY() < level.getMaxBuildHeight()) {
            if (!level.isLoaded(cursor)) {
                return false;
            }
            BlockState state = level.getBlockState(cursor);
            if (!(state.getBlock() instanceof SteamerBlock)) {
                return false;
            }
            BlockPos above = cursor.above();
            if (above.getY() >= level.getMaxBuildHeight()
                    || !(level.getBlockState(above).getBlock() instanceof SteamerBlock)) {
                return state.hasProperty(SteamerBlock.HAS_LID) && state.getValue(SteamerBlock.HAS_LID);
            }
            cursor = above;
        }
        return false;
    }

    private static boolean isCurrent(SteamerBlockEntity steamer, Level level) {
        return !steamer.isRemoved() && level.getBlockEntity(steamer.getBlockPos()) == steamer;
    }

    static boolean canFitAll(IItemHandler destination, List<ItemStack> stacks) {
        List<ItemStack> simulated = new ArrayList<>(destination.getSlots());
        for (int slot = 0; slot < destination.getSlots(); slot++) {
            simulated.add(destination.getStackInSlot(slot).copy());
        }

        for (ItemStack source : stacks) {
            int remaining = source.getCount();
            for (int slot = 0; slot < simulated.size() && remaining > 0; slot++) {
                ItemStack present = simulated.get(slot);
                if (present.isEmpty() || !ItemStack.isSameItemSameComponents(present, source)) {
                    continue;
                }
                int limit = Math.min(destination.getSlotLimit(slot), present.getMaxStackSize());
                int inserted = Math.min(remaining, Math.max(0, limit - present.getCount()));
                present.grow(inserted);
                remaining -= inserted;
            }
            for (int slot = 0; slot < simulated.size() && remaining > 0; slot++) {
                if (!simulated.get(slot).isEmpty() || !destination.isItemValid(slot, source)) {
                    continue;
                }
                int limit = Math.min(destination.getSlotLimit(slot), source.getMaxStackSize());
                int inserted = Math.min(remaining, limit);
                simulated.set(slot, source.copyWithCount(inserted));
                remaining -= inserted;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static void transferExpectedStack(
            ItemStack source,
            List<ItemStack> expected,
            IItemHandler destination
    ) {
        if (source.isEmpty()) {
            return;
        }
        int matchingCount = claimExpectedCount(source, expected);
        if (matchingCount <= 0) {
            return;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                destination,
                source.copyWithCount(matchingCount),
                false
        );
        source.shrink(matchingCount - remainder.getCount());
    }

    private static int claimExpectedCount(ItemStack source, List<ItemStack> expected) {
        int remaining = source.getCount();
        int claimed = 0;
        for (ItemStack expectedStack : expected) {
            if (remaining <= 0 || expectedStack.isEmpty()
                    || !ItemStack.isSameItemSameComponents(source, expectedStack)) {
                continue;
            }
            int amount = Math.min(remaining, expectedStack.getCount());
            expectedStack.shrink(amount);
            claimed += amount;
            remaining -= amount;
        }
        return claimed;
    }

    public record Snapshot(
            BlockPos pos,
            boolean accessible,
            boolean covered,
            boolean hasHeatSource,
            List<ItemStack> items,
            List<Integer> cookingProgress,
            List<Integer> cookingTime
    ) {
        public Snapshot {
            items = items.stream().map(ItemStack::copy).toList();
            cookingProgress = List.copyOf(cookingProgress);
            cookingTime = List.copyOf(cookingTime);
        }

        @Override
        public List<ItemStack> items() {
            return items.stream().map(ItemStack::copy).toList();
        }

        public boolean hasEmptySlot() {
            return items.stream().anyMatch(ItemStack::isEmpty);
        }

        public int emptySlotCount() {
            return (int) items.stream().filter(ItemStack::isEmpty).count();
        }

        public boolean allFoodReady() {
            boolean foundFood = false;
            for (int slot = 0; slot < items.size(); slot++) {
                if (items.get(slot).isEmpty()) {
                    continue;
                }
                foundFood = true;
                if (cookingTime.get(slot) != COOKING_COMPLETE) {
                    return false;
                }
            }
            return foundFood;
        }

        public boolean canTakeFood() {
            return accessible && covered && allFoodReady();
        }
    }
}
