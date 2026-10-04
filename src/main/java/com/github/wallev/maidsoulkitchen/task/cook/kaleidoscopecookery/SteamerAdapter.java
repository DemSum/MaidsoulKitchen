package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskClassAnalyzer;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.ISteamer;
import com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Narrow adapter around Kaleidoscope Cookery's steamer API. Keeping all KC
 * implementation details here makes version drift fail in one place.
 *
 * <p>Native port of Maidsoul-Brewery-Public commit de47e15.</p>
 */
@TaskClassAnalyzer(com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.KC_STEAMER)
public final class SteamerAdapter {
    private static final int HALF_STEAMER_SLOTS = 4;
    private static final int FULL_STEAMER_SLOTS = 8;
    private static final int COOKING_COMPLETE = -1;
    private static final int MAX_HEATED_LAYERS = SteamerBlockEntity.MAX_LIT_LEVEL;
    static final double STACK_INTERACTION_DISTANCE = MAX_HEATED_LAYERS;

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
                hasEffectiveHeatSource(steamer, level),
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

    public static List<RecipeOption> getRecipeOptions(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipes.STEAMER_RECIPE).stream()
                .map(recipe -> new RecipeOption(recipe.id(), recipe.value().getResult()))
                .sorted(java.util.Comparator.comparing(RecipeOption::id))
                .toList();
    }

    private static boolean isAccessible(SteamerBlockEntity steamer, Level level) {
        BlockPos above = steamer.getBlockPos().above();
        return !level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    static int[] interactionHeightOffsets() {
        return SteamerStackHeat.interactionHeightOffsets(MAX_HEATED_LAYERS);
    }

    private static boolean hasEffectiveHeatSource(SteamerBlockEntity steamer, Level level) {
        return findHeatSourcePosition(steamer, level).isPresent();
    }

    static Optional<BlockPos> findHeatSourcePosition(BlockEntity blockEntity, Level level) {
        if (!(blockEntity instanceof SteamerBlockEntity steamer)) {
            return Optional.empty();
        }
        BlockPos origin = steamer.getBlockPos();
        int heatedDepth = SteamerStackHeat.findHeatedDepth(MAX_HEATED_LAYERS, depth -> {
            BlockPos layerPos = origin.below(depth);
            if (!level.isLoaded(layerPos)) {
                return SteamerStackHeat.LayerState.NOT_STEAMER;
            }
            BlockEntity layerEntity = level.getBlockEntity(layerPos);
            if (!(layerEntity instanceof SteamerBlockEntity layer)) {
                return SteamerStackHeat.LayerState.NOT_STEAMER;
            }
            // KC updateLitLevel explicitly stops steam above a HALF layer. A half steamer may
            // be the selected top, but cannot be a supporting layer. The old depth predicate
            // accepted this impossible heat path and repeatedly loaded food that never cooked.
            if (depth > 0 && layer.getBlockState().getValue(SteamerBlock.HALF))
                return SteamerStackHeat.LayerState.NOT_STEAMER;
            return layer.hasHeatSource(level)
                    ? SteamerStackHeat.LayerState.DIRECTLY_HEATED
                    : SteamerStackHeat.LayerState.UNHEATED;
        });
        return heatedDepth < 0
                ? Optional.empty()
                : Optional.of(origin.below(heatedDepth + 1));
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

