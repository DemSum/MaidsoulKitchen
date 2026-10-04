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
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.itemdown.RecDataUse;

/** Source: 58ec08ec RecSerializerManager/createMaidRec (MIT), with the existing verified
 * KC quantity grouping, bounded assignment and native overlap checks moved from StockpotRecipePlanner.
 * No 1.20 stockpot existed. Half-pot inputs, real lids/carriers and Flex native quantity require a
 * device-context converter. It produces only MaidRec; the old Plan/Decision and cached availability
 * are deleted. Pure recipe metadata is reload-aware; no queue or storage is owned here. */
public final class StockpotRecSerializerManager extends RecSerializerManager<Recipe<StockpotInput>> {
    public static final StockpotRecSerializerManager INSTANCE = new StockpotRecSerializerManager();
    @SuppressWarnings({"unchecked", "rawtypes"})
    private StockpotRecSerializerManager() { super((RecipeType) ModRecipes.STOCKPOT_RECIPE); }

    @Override @SuppressWarnings({"unchecked", "rawtypes"})
    protected List<RecipeHolder<Recipe<StockpotInput>>> getRecsFromRm(Level level) {
        List<RecipeHolder<?>> holders = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE));
        holders.addAll(level.getRecipeManager().getAllRecipesFor(ModRecipes.FLEX_STOCKPOT_RECIPE));
        return (List) holders;
    }

    @Override protected MKRecipe<Recipe<StockpotInput>> createMKRecipe(RecipeHolder<Recipe<StockpotInput>> holder) {
        var spec = Spec.of(holder);
        List<RecIngredient> ingredients = new ArrayList<>(RecIngredient.from(spec.ingredients()));
        if (!spec.carrier().isEmpty()) ingredients.add(RecIngredient.of(spec.carrier()));
        ingredients.add(RecIngredient.of(Ingredient.of(com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems.STOCKPOT_LID.get())));
        return new MKRecipe<>(holder, true, ingredients, spec.result());
    }

    /** Native appliance conditions attach to descriptors, before the inherited ten-descriptor tick
     * planner. This is metadata, not a work unit; the converter alone creates the final MaidRec. */
    static final class DeviceRecipe extends MKRecipe<Recipe<StockpotInput>> {
        final com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity device;
        final List<Spec> candidates;
        final StockpotTaskData settings;
        DeviceRecipe(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity device, List<Spec> candidates, StockpotTaskData settings) {
            super(candidates.getFirst().holder(), true, RecIngredient.from(candidates.getFirst().ingredients()), candidates.getFirst().result());
            this.device = device; this.candidates = List.copyOf(candidates); this.settings = settings;
        }
    }

    List<DeviceRecipe> forDevice(com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity device,
                                  Level level, StockpotTaskData settings) {
        return groups(level, settings).stream().filter(group -> option(group).allowed(settings.filter()))
                .map(group -> new DeviceRecipe(device, group, settings)).toList();
    }

    @Override protected List<MaidRec> createMaidRec(MKRecipe<Recipe<StockpotInput>> description,
            Map<ItemDefinition, Long> available, RecDataUse use, ResourceLocation taskId, long generation) {
        if (!(description instanceof DeviceRecipe deviceRecipe)) return List.of();
        var device = deviceRecipe.device; var level = device.getLevel();
        var snapshot = StockpotAdapter.inspect(device, level).orElse(null);
        if (snapshot == null || !snapshot.heated() || (snapshot.status() != IStockpot.PUT_SOUP_BASE && snapshot.status() != IStockpot.PUT_INGREDIENT)) return List.of();
        var settings = deviceRecipe.settings;
        // Settings are attached by the sole manager, never read from a second persisted recipe state.
        List<ItemStack> pool = available.entrySet().stream().filter(entry -> entry.getValue() > 0).map(entry -> entry.getKey().toStack(entry.getValue())).toList();
        var work = createWork(snapshot, level, settings, pool, deviceRecipe.candidates, device, taskId, generation);
        if (work == null) return List.of();
        Map<ItemDefinition, ItemAmount> amounts = new HashMap<>();
        for (MaidItem material : work.maidItems()) {
            if (material.isEmpty() || material.role() == MaidItem.Role.DEVICE_INPUT) continue;
            amounts.computeIfAbsent(material.item(), ignored -> new ItemAmount(0)).addCount(material.count());
            available.compute(material.item(), (item, count) -> count - material.count());
        }
        use.set(amounts, 1); return List.of(work);
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

    private static boolean allows(Spec spec, Level level, StockpotTaskData settings) {
        return groups(level, settings).stream().filter(group -> group.stream().anyMatch(candidate -> candidate.id().equals(spec.id())))
                .anyMatch(group -> option(group).allowed(settings.filter()));
    }
    private static List<List<Spec>> groups(Level level, StockpotTaskData settings) {
        List<List<Spec>> groups = new ArrayList<>(quantityGroups(level.getRecipeManager().getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE)
                .stream().map(Spec::ordinary).toList(), level));
        if (settings.allowFlexRecipes()) level.getRecipeManager().getAllRecipesFor(ModRecipes.FLEX_STOCKPOT_RECIPE)
                .forEach(holder -> groups.add(List.of(Spec.flex(holder))));
        return groups;
    }
    static List<Spec> allSpecs(Level level, StockpotTaskData settings) { return groups(level, settings).stream().flatMap(List::stream).toList(); }
    boolean enabled(ResourceLocation id, Level level, StockpotTaskData settings) {
        return allSpecs(level, settings).stream().anyMatch(spec -> spec.id().equals(id) && allows(spec, level, settings));
    }

    boolean retain(ItemStack stack, Level level, StockpotTaskData settings) {
        if (StockpotAdapter.isLid(stack) || (stack.getMaxStackSize() == 1
                && !stack.has(net.minecraft.core.component.DataComponents.FOOD))
                || stack.is(net.minecraft.world.item.Items.BOWL) || stack.is(net.minecraft.world.item.Items.BUCKET)
                || stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) return true;
        for (Spec spec : allSpecs(level, settings)) {
            if (spec.carrier().test(stack)) return true;
            // Reusable empty ingredient containers belong to the input buffer, not unused-food cleanup.
            for (Ingredient ingredient : spec.ingredients()) for (ItemStack candidate : ingredient.getItems()) {
                ItemStack container = StockpotAdapter.ingredientContainer(candidate);
                if (!container.isEmpty() && stack.is(container.getItem())) return true;
            }
            if (allows(spec, level, settings) && (spec.ingredients().stream().anyMatch(ingredient -> ingredient.test(stack))
                    || StockpotAdapter.soupBaseFor(stack).filter(spec.soup()::equals).isPresent())) return true;
        }
        return false;
    }


    MaidRec createWork(StockpotAdapter.Snapshot snapshot, Level level, StockpotTaskData settings,
            List<ItemStack> available, List<Spec> candidates,
            com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity device,
            ResourceLocation taskId, long generation) {
        Set<ResourceLocation> suppliedGroups = new HashSet<>();
        for (Spec spec : candidates) {
            ResourceLocation groupId = candidates.getFirst().id();
            if (suppliedGroups.contains(groupId) || spec.ingredients().isEmpty() || !allows(spec, level, settings)
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
                    MaidRec[] selected = {null};
                    boolean found = fill(spec, mask, 0, branch, usedItems, additions, budget, remaining -> {
                        List<ItemStack> completed = completed(snapshot.inputs(), additions);
                        if (completed == null) return false;
                        StockpotInput input = new StockpotInput(completed, spec.soup());
                        if (!spec.matches(input, level)) return false;
                        materialComplete[0] = true;
                        List<Spec> hits = nativeMatches(input, level, settings);
                        if (hits.isEmpty() || hits.stream().anyMatch(hit -> !allows(hit, level, settings))) {
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
                        List<MaidItem> materials = new ArrayList<>();
                        if (!base.isEmpty()) materials.add(new MaidItem(ItemDefinition.of(base), 1, MaidItem.Role.FLUID));
                        if (!snapshot.covered()) materials.add(new MaidItem(ItemDefinition.of(lid), 1, MaidItem.Role.TOOL));
                        additions.forEach(stack -> materials.add(new MaidItem(ItemDefinition.of(stack), 1)));
                        int carrierStart = supplies.size() + additions.size();
                        resources.subList(carrierStart, resources.size()).forEach(stack -> materials.add(new MaidItem(ItemDefinition.of(stack), 1, MaidItem.Role.CONTAINER)));
                        snapshot.inputs().forEach(stack -> materials.add(new MaidItem(ItemDefinition.of(stack), stack.getCount(), MaidItem.Role.DEVICE_INPUT)));
                        if (snapshot.covered()) materials.add(new MaidItem(ItemDefinition.of(snapshot.lid()), snapshot.lid().getCount(), MaidItem.Role.DEVICE_INPUT));
                        var pos = device.getBlockPos();
                        selected[0] = new MaidRec(spec.holder(), taskId, generation, 0, 1, List.of(preview), materials,
                                Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "device", System.identityHashCode(device),
                                        "status", snapshot.status(), "covered", snapshot.covered() ? 1 : 0));
                        return true;
                    });
                    if (found) return selected[0];
                    if (budget.exhausted) break;
                }
                if (budget.exhausted) break;
            }
            // Smaller batches are a material-shortage fallback, not a way around missing carriers or uncertainty.
            if (materialComplete[0] || budget.exhausted) suppliedGroups.add(groupId);
        }
        return null;
    }

    boolean permitsCompleted(StockpotAdapter.Snapshot snapshot, Level level, StockpotTaskData settings) {
        List<Spec> hits = nativeMatches(new StockpotInput(snapshot.inputs(), snapshot.soupBase()), level, settings);
        return !hits.isEmpty() && hits.stream().allMatch(spec -> allows(spec, level, settings));
    }

    private static List<Spec> nativeMatches(StockpotInput input, Level level, StockpotTaskData settings) {
        var specs = allSpecs(level, settings);
        List<Spec> hits = specs.stream().filter(spec -> !spec.flexible() && spec.matches(input, level)).toList();
        return hits.isEmpty() ? specs.stream().filter(spec -> spec.flexible() && spec.matches(input, level)).toList() : hits;
    }

    /** Native structural completion check used by cleanup. Material shortage/ambiguity must not
     * clear a partially prepared pot; use the same verified ingredient assignment algorithm. */
    boolean hasAllowedCompletion(StockpotAdapter.Snapshot snapshot, Level level, StockpotTaskData settings) {
        for (Spec spec : allSpecs(level, settings)) {
            if (!allows(spec, level, settings) || !spec.soup().equals(snapshot.soupBase())) continue;
            List<ItemStack> fixed = snapshot.inputs().stream().filter(stack -> !stack.isEmpty()).toList();
            int occupied = fixed.size();
            if (spec.flexible()) { Set<Item> seen = new HashSet<>(); fixed = fixed.stream().filter(stack -> seen.add(stack.getItem())).toList(); }
            if (occupied + spec.ingredients().size() - fixed.size() > 9) continue;
            boolean[][] matrix = new boolean[fixed.size()][spec.ingredients().size()];
            for (int i=0; i<fixed.size(); i++) for (int j=0; j<spec.ingredients().size(); j++) matrix[i][j] = spec.ingredients().get(j).test(fixed.get(i));
            if (!StockpotIngredientMatcher.assignments(matrix, spec.ingredients().size()).isEmpty()) return true;
        }
        return false;
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

    record Spec(RecipeHolder<Recipe<StockpotInput>> holder, ResourceLocation id, ResourceLocation soup, List<Ingredient> ingredients,
                        Ingredient carrier, ItemStack result, boolean flexible,
                        StockpotRecipe ordinaryRecipe, FlexStockpotRecipe flexRecipe) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static RecipeHolder<Recipe<StockpotInput>> cast(RecipeHolder<?> holder) { return (RecipeHolder) holder; }
        @SuppressWarnings({"unchecked", "rawtypes"})
        static Spec of(RecipeHolder<Recipe<StockpotInput>> holder) {
            return holder.value() instanceof StockpotRecipe ? ordinary((RecipeHolder) holder) : flex((RecipeHolder) holder);
        }
        static Spec ordinary(RecipeHolder<StockpotRecipe> holder) {
            var recipe = holder.value();
            return new Spec(cast(holder), holder.id(), recipe.soupBase(), nonEmpty(recipe.ingredients()), recipe.carrier(),
                    recipe.result(), false, recipe, null);
        }
        static Spec flex(RecipeHolder<FlexStockpotRecipe> holder) {
            var recipe = holder.value();
            return new Spec(cast(holder), holder.id(), recipe.soupBase(), nonEmpty(recipe.ingredients()), recipe.carrier(),
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
