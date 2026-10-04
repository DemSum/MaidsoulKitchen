package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.StockpotTaskData;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotInput;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import com.mojang.serialization.JsonOps;

/** One short-lived planning pass, shared across devices in the existing throttled BFS. */
final class StockpotRecipePlanner {
    enum Outcome { READY, WAIT_SUPPLIES, NO_ALLOWED_COMPLETION, UNCERTAIN }
    record Decision(Outcome outcome, Plan plan) { }
    record Plan(ResourceLocation recipeId, ResourceLocation soupBase, ItemStack baseItem,
                ItemStack lid, List<ItemStack> additions, List<ItemStack> supplies, ItemStack preview) { }

    private final Level level;
    private final StockpotTaskData settings;
    private final List<Spec> ordinary;
    private final List<Spec> flexible;
    private final List<ItemStack> available;
    private final Map<ResourceLocation, RecipeOption> optionsById = new HashMap<>();

    StockpotRecipePlanner(Level level, StockpotTaskData settings, List<ItemStack> available) {
        this.level = level;
        this.settings = settings;
        this.available = available.stream().map(ItemStack::copy).toList();
        List<List<Spec>> groups = quantityGroups(level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)
                .stream().map(Spec::ordinary).toList(), level);
        ordinary = groups.stream().flatMap(List::stream).toList();
        flexible = settings.allowFlexRecipes()
                ? level.getRecipeManager().getAllRecipesFor(ModRecipes.FLEX_STOCKPOT_RECIPE).stream()
                    .map(Spec::flex).toList() : List.of();
        for (List<Spec> group : groups) {
            RecipeOption option = option(group);
            group.forEach(spec -> optionsById.put(spec.id(), option));
        }
        flexible.forEach(spec -> optionsById.put(spec.id(), new RecipeOption(spec.id(), spec.result())));
    }

    static List<RecipeOption> options(Level level, boolean includeFlex) {
        List<RecipeOption> result = new ArrayList<>();
        quantityGroups(level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)
                .stream().map(Spec::ordinary).toList(), level).forEach(group -> result.add(option(group)));
        if (includeFlex) level.getRecipeManager().getAllRecipesFor(ModRecipes.FLEX_STOCKPOT_RECIPE)
                .forEach(holder -> result.add(new RecipeOption(holder.id(), holder.value().result())));
        result.sort(java.util.Comparator.comparing(option -> option.id().toString()));
        return List.copyOf(result);
    }

    private static RecipeOption option(List<Spec> group) {
        var ids = group.stream().map(Spec::id).sorted().toList();
        return new RecipeOption(ids.getFirst(), group.getFirst().result(), ids);
    }

    /** Keep the native IDs; group only proportional quantity variants, not merely identical outputs. */
    private static List<List<Spec>> quantityGroups(List<Spec> recipes, Level level) {
        Map<String, List<Spec>> grouped = new TreeMap<>();
        for (Spec spec : recipes) grouped.computeIfAbsent(quantityKey(spec, level), ignored -> new ArrayList<>()).add(spec);
        List<List<Spec>> result = new ArrayList<>();
        for (List<Spec> candidates : grouped.values()) {
            if (candidates.stream().map(spec -> spec.ingredients().size()).distinct().count() < 2) {
                candidates.forEach(spec -> result.add(List.of(spec)));
            } else {
                candidates.sort(Comparator.comparingInt((Spec spec) -> spec.ingredients().size()).reversed()
                        .thenComparing(Spec::id));
                result.add(List.copyOf(candidates));
            }
        }
        result.sort(Comparator.comparing(group -> group.stream().map(Spec::id).min(ResourceLocation::compareTo).orElseThrow()));
        return result;
    }

    private static String quantityKey(Spec spec, Level level) {
        if (spec.ingredients().isEmpty()) return spec.id().toString();
        var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        Map<String, Integer> counts = new TreeMap<>();
        for (Ingredient ingredient : spec.ingredients()) {
            var encoded = Ingredient.CODEC.encodeStart(ops, ingredient).result();
            if (encoded.isEmpty()) return spec.id().toString(); // unsupported custom predicate: keep separate
            counts.merge(encoded.get().toString(), 1, Integer::sum);
        }
        int divisor = spec.result().getCount();
        for (int count : counts.values()) divisor = gcd(divisor, count);
        final int scale = Math.max(1, divisor);
        counts.replaceAll((key, count) -> count / scale);
        var carrier = spec.carrier().isEmpty() ? "[]" : Ingredient.CODEC.encodeStart(ops, spec.carrier())
                .result().map(Object::toString).orElse(spec.id().toString());
        var result = ItemStack.CODEC.encodeStart(ops, spec.result().copyWithCount(1))
                .result().map(Object::toString).orElse(spec.id().toString());
        return spec.soup() + "|" + carrier + "|" + result + "|" + spec.result().getCount() / scale + "|" + counts;
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int remainder = a % b; a = b; b = remainder; }
        return a;
    }

    private boolean allows(Spec spec) { return optionsById.get(spec.id()).allowed(settings.filter()); }

    boolean retain(ItemStack stack) {
        if (StockpotAdapter.isLid(stack) || (stack.getMaxStackSize() == 1
                && !stack.has(net.minecraft.core.component.DataComponents.FOOD))
                || stack.is(net.minecraft.world.item.Items.BOWL) || stack.is(net.minecraft.world.item.Items.BUCKET)
                || stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) return true;
        for (Spec spec : allSpecs()) {
            if (spec.carrier().test(stack)) return true;
            // Reusable empty ingredient containers belong to the input buffer, not unused-food cleanup.
            for (Ingredient ingredient : spec.ingredients()) for (ItemStack candidate : ingredient.getItems()) {
                ItemStack container = StockpotAdapter.ingredientContainer(candidate);
                if (!container.isEmpty() && stack.is(container.getItem())) return true;
            }
            if (allows(spec) && (spec.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))
                    || StockpotAdapter.soupBaseFor(stack).filter(spec.soup()::equals).isPresent())) return true;
        }
        return false;
    }

    boolean uses(StockpotTaskData current) { return settings.equals(current); }
    boolean hasSupply(Predicate<ItemStack> predicate) { return available.stream().anyMatch(predicate); }

    Decision plan(StockpotAdapter.Snapshot snapshot) {
        boolean structurallyPossible = false;
        boolean uncertain = false;
        Set<ResourceLocation> suppliedGroups = new HashSet<>();
        for (Spec spec : allSpecs()) {
            ResourceLocation groupId = optionsById.get(spec.id()).id();
            if (suppliedGroups.contains(groupId) || spec.ingredients().isEmpty() || !allows(spec)
                    || (snapshot.status() != IStockpot.PUT_SOUP_BASE && !spec.soup().equals(snapshot.soupBase()))) continue;
            List<ItemStack> fixed = snapshot.inputs().stream().filter(stack -> !stack.isEmpty()).toList();
            int occupiedSlots = fixed.size();
            if (spec.flexible()) {
                Set<Item> seen = new HashSet<>();
                fixed = fixed.stream().filter(stack -> seen.add(stack.getItem())).toList();
            }
            if (occupiedSlots + spec.ingredients().size() - fixed.size() > 9) continue;
            boolean[][] matrix = new boolean[fixed.size()][spec.ingredients().size()];
            for (int i = 0; i < fixed.size(); i++) for (int j = 0; j < spec.ingredients().size(); j++) {
                matrix[i][j] = spec.ingredients().get(j).test(fixed.get(i));
            }
            Set<Integer> masks = StockpotIngredientMatcher.assignments(matrix, spec.ingredients().size());
            if (masks.isEmpty()) continue;
            structurallyPossible = true;
            List<ItemStack> bases = snapshot.status() == IStockpot.PUT_SOUP_BASE
                    ? available.stream().filter(stack -> StockpotAdapter.soupBaseFor(stack)
                        .filter(spec.soup()::equals).isPresent()).map(stack -> stack.copyWithCount(1)).toList()
                    : List.of(ItemStack.EMPTY);
            Budget budget = new Budget(); // shared across alternatives, not restarted for every mask
            boolean[] materialComplete = {false};
            for (ItemStack base : bases) {
                List<ItemStack> pool = copyPool(available);
                List<ItemStack> supplies = new ArrayList<>();
                if (!base.isEmpty()) {
                    if (reserve(pool, stack -> ItemStack.isSameItemSameComponents(base, stack)).isEmpty()) continue;
                    supplies.add(base);
                }
                ItemStack lid = snapshot.covered() ? StockpotAdapter.lidToReturn(snapshot)
                        : reserve(pool, StockpotAdapter::isLid);
                if (lid.isEmpty()) continue;
                if (!snapshot.covered()) supplies.add(lid);
                for (int mask : masks) {
                    List<ItemStack> branch = copyPool(pool);
                    List<ItemStack> additions = new ArrayList<>();
                    Set<Item> usedItems = new HashSet<>();
                    fixed.forEach(stack -> usedItems.add(stack.getItem()));
                    Plan[] selected = {null};
                    boolean[] ambiguous = {false};
                    boolean found = fill(spec, mask, 0, branch, usedItems, additions, budget, remaining -> {
                        List<ItemStack> completed = completed(snapshot.inputs(), additions);
                        if (completed == null) return false;
                        StockpotInput input = new StockpotInput(completed, spec.soup());
                        if (!spec.matches(input, level)) return false;
                        materialComplete[0] = true;
                        List<Spec> hits = nativeMatches(input);
                        if (hits.isEmpty() || hits.stream().anyMatch(hit -> !allows(hit))) {
                            ambiguous[0] = true;
                            return false;
                        }
                        // KC Flex assemble can change result quantity based on the actual inputs.
                        ItemStack preview = spec.assemble(input, level);
                        List<ItemStack> resources = new ArrayList<>(supplies);
                        resources.addAll(additions);
                        List<ItemStack> carrierPool = copyPool(remaining);
                        List<ItemStack> returnedContainers = additions.stream().map(StockpotAdapter::ingredientContainer)
                                .filter(stack -> !stack.isEmpty()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                        for (int portion = 0; !spec.carrier().isEmpty()
                                && portion < Math.min(9, preview.getCount()); portion++) {
                            // Budget native ingredient returns without preparing or manufacturing those items.
                            if (!reserve(returnedContainers, spec.carrier()::test).isEmpty()) continue;
                            ItemStack carrier = reserve(carrierPool, spec.carrier()::test);
                            if (carrier.isEmpty()) return false;
                            resources.add(carrier);
                        }
                        selected[0] = new Plan(spec.id(), spec.soup(), base, lid, List.copyOf(additions),
                                List.copyOf(resources), preview.copyWithCount(1));
                        return true;
                    });
                    uncertain |= ambiguous[0] || budget.exhausted;
                    if (found) return new Decision(Outcome.READY, selected[0]);
                    if (budget.exhausted) break;
                }
                if (budget.exhausted) break;
            }
            // Smaller batches are a material-shortage fallback, not a way around missing carriers or uncertainty.
            if (materialComplete[0] || budget.exhausted) suppliedGroups.add(groupId);
        }
        return new Decision(uncertain ? Outcome.UNCERTAIN
                : structurallyPossible ? Outcome.WAIT_SUPPLIES : Outcome.NO_ALLOWED_COMPLETION, null);
    }

    boolean permitsCompleted(StockpotAdapter.Snapshot snapshot) {
        List<Spec> hits = nativeMatches(new StockpotInput(snapshot.inputs(), snapshot.soupBase()));
        return !hits.isEmpty() && hits.stream().allMatch(this::allows);
    }

    private List<Spec> nativeMatches(StockpotInput input) {
        List<Spec> hits = ordinary.stream().filter(spec -> spec.matches(input, level)).toList();
        return hits.isEmpty() ? flexible.stream().filter(spec -> spec.matches(input, level)).toList() : hits;
    }

    private List<Spec> allSpecs() {
        List<Spec> result = new ArrayList<>(ordinary);
        result.addAll(flexible);
        return result;
    }

    private static boolean fill(Spec spec, int fixedMask, int index, List<ItemStack> pool,
                                Set<Item> used, List<ItemStack> additions, Budget budget,
                                Predicate<List<ItemStack>> complete) {
        if (--budget.remaining < 0) { budget.exhausted = true; return false; }
        if (index == spec.ingredients().size()) return complete.test(pool);
        if ((fixedMask & (1 << index)) != 0) return fill(spec, fixedMask, index + 1, pool, used, additions, budget, complete);
        Ingredient ingredient = spec.ingredients().get(index);
        for (ItemStack candidate : pool) {
            if (!StockpotAdapter.canPlaceIngredient(candidate) || !ingredient.test(candidate)
                    || (spec.flexible() && used.contains(candidate.getItem()))) continue;
            ItemStack unit = candidate.copyWithCount(1);
            candidate.shrink(1);
            additions.add(unit);
            boolean wasUsed = !used.add(unit.getItem());
            if (fill(spec, fixedMask, index + 1, pool, used, additions, budget, complete)) return true;
            if (!wasUsed) used.remove(unit.getItem());
            additions.removeLast();
            candidate.grow(1);
            if (budget.exhausted) return false;
        }
        return false;
    }

    private static List<ItemStack> copyPool(List<ItemStack> input) {
        return input.stream().map(ItemStack::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static ItemStack reserve(List<ItemStack> pool, Predicate<ItemStack> accepts) {
        for (ItemStack stack : pool) {
            if (!stack.isEmpty() && accepts.test(stack)) return stack.split(1);
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> completed(List<ItemStack> existing, List<ItemStack> additions) {
        List<ItemStack> result = existing.stream().map(ItemStack::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        while (result.size() < 9) result.add(ItemStack.EMPTY);
        int next = 0;
        for (ItemStack addition : additions) {
            while (next < result.size() && !result.get(next).isEmpty()) next++;
            if (next >= 9) return null;
            result.set(next++, addition.copy());
        }
        return result;
    }

    private static final class Budget { int remaining = 2048; boolean exhausted; }

    private record Spec(ResourceLocation id, ResourceLocation soup, List<Ingredient> ingredients,
                        Ingredient carrier, ItemStack result, boolean flexible,
                        StockpotRecipe ordinaryRecipe, FlexStockpotRecipe flexRecipe) {
        static Spec ordinary(RecipeHolder<StockpotRecipe> holder) {
            var recipe = holder.value();
            return new Spec(holder.id(), recipe.soupBase(), nonEmpty(recipe.ingredients()), recipe.carrier(),
                    recipe.result(), false, recipe, null);
        }
        static Spec flex(RecipeHolder<FlexStockpotRecipe> holder) {
            var recipe = holder.value();
            return new Spec(holder.id(), recipe.soupBase(), nonEmpty(recipe.ingredients()), recipe.carrier(),
                    recipe.result(), true, null, recipe);
        }
        private static List<Ingredient> nonEmpty(List<Ingredient> ingredients) {
            return ingredients.stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        }
        ItemStack assemble(StockpotInput input, Level level) {
            return flexible ? flexRecipe.assemble(input, level.registryAccess())
                    : ordinaryRecipe.assemble(input, level.registryAccess());
        }
        boolean matches(StockpotInput input, Level level) {
            return flexible ? flexRecipe.matches(input, level) : ordinaryRecipe.matches(input, level);
        }
    }
}
