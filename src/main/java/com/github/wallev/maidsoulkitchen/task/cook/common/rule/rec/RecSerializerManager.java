package com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.itemdown.RecDataUse;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.IndexRange;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.*;

/**
 * Source: 58ec08ec task/cook/common/rule/rec/RecSerializerManager.java (MIT).
 * Keeps upstream selection, batching, resource accounting and conversion method boundaries.
 * Holder/RecipeInput + real RegistryAccess replace 1.20 APIs. Candidate accounting is committed only
 * after acceptance: upstream depleted availability even when recDataUsePredicate rejected a plan.
 * P2 replaces beta getAmountIngredient/transform/repeat; this converter owns no running queue.
 */
public class RecSerializerManager<R extends Recipe<? extends RecipeInput>> {
    protected final RecipeType<R> recipeType;
    protected final RecipeInfoProvider<R> recipeInfoProvider;
    // @Final
    protected List<MKRecipe<R>> recipes;

    protected List<ItemStack> fuels;

    protected RecSerializerManager(RecipeType<R> recipeType) {
        this.recipeType = recipeType;
        this.recipeInfoProvider = this.createRecipeInfoProvider();
    }

    protected RecipeInfoProvider<R> createRecipeInfoProvider() {
        return RecipeInfoProvider.getInstance();
    }

    public LinkedList<MaidRec> createMaidRecs(List<MKRecipe<R>> recs,
                                              Map<ItemDefinition, Long> available,
                                              BiConsumer<MKRecipe<R>, IndexRange> successAdd,
                                              Predicate<MKRecipe<R>> rIsValid,
                                              Predicate<RecDataUse> recDataUsePredicate,
                                              Consumer<Boolean> doneConsumer, ResourceLocation taskId, long generation) {
        LinkedList<MaidRec> maidRecs = new LinkedList<>();
        IndexRange indexRange = new IndexRange();
        RecDataUse recDataUse = new RecDataUse();

        int index = 0;
        for (MKRecipe<R> r : recs) {
            if (!rIsValid.test(r)) {
                continue;
            }

            Map<ItemDefinition, Long> candidateAvailable = new HashMap<>(available);
            List<MaidRec> maidRec = this.createMaidRec(r, candidateAvailable, recDataUse, taskId, generation);

            int size = maidRec.size();
            if (size == 0) {
                continue;
            }

            boolean test = recDataUsePredicate.test(recDataUse);
            if (test) {
                available.clear();
                available.putAll(candidateAvailable);
                maidRecs.addAll(maidRec);
                indexRange.set(index, size);
                successAdd.accept(r, indexRange);
                index += size;
            } else {
                doneConsumer.accept(true);
                break;
            }
        }

        return maidRecs;
    }

    @SuppressWarnings("all")
    protected List<MaidRec> createMaidRec(MKRecipe<R> r, Map<ItemDefinition, Long> available, RecDataUse recDataUse, ResourceLocation taskId, long generation) {
        List<ItemDefinition> invIngredient = new ArrayList<>();
        Map<ItemDefinition, ItemAmount> itemTimes = new HashMap<>();
        boolean[] single = {false};

        List<MaidRec> maidRecs = recProcess(r, available, invIngredient, single, itemTimes, taskId, generation);
        if (!maidRecs.isEmpty()) {
            recDataUse.set(itemTimes, maidRecs.size());
        }
        return maidRecs;
    }

    protected List<MaidRec> recProcess(MKRecipe<R> r, Map<ItemDefinition, Long> available, List<ItemDefinition> invIngredient, boolean[] single, Map<ItemDefinition, ItemAmount> itemTimes, ResourceLocation taskId, long generation) {
        boolean processRecIngres = processRecIngres(r, available, invIngredient, single, itemTimes, taskId, generation);
        if (!processRecIngres) {
            return Collections.emptyList();
        }

        return createCookRec(r, available, single, invIngredient, itemTimes, taskId, generation);
    }

    protected boolean processRecIngres(MKRecipe<R> r, Map<ItemDefinition, Long> available, List<ItemDefinition> invIngredient, boolean[] single, Map<ItemDefinition, ItemAmount> itemTimes, ResourceLocation taskId, long generation) {
        for (RecIngredient ingredient : r.inItems()) {
            boolean hasIngredient = false;
            for (Map.Entry<ItemDefinition, Long> entry : available.entrySet()) {
                ItemDefinition key = entry.getKey();
                Long value = entry.getValue();
                Item item = key.item();

                ItemStack stack = key.toStack(value);
                int test = ingredient.test(stack);
                if (test > 0) {
                    // Upstream overwrote repeated unstackable demand and selected exhausted variants.
                    // Check the candidate before mutating the per-recipe demand or committing the choice.
                    ItemAmount existing = itemTimes.get(key);
                    int amount = test + (existing == null ? 0 : existing.needCount());
                    if (value < amount) continue;
                    invIngredient.add(key);
                    hasIngredient = true;

                    if (stack.getMaxStackSize() == 1) {
                        single[0] = true;
                    }
                    ItemAmount itemAmount = itemTimes.computeIfAbsent(key, k -> new ItemAmount(1, 0));
                    itemAmount.addCount(test);
                    break;

                }
            }

            if (!hasIngredient) {
                return false;
            }
        }
        return true;
    }

    protected List<MaidRec> createCookRec(MKRecipe<R> r, Map<ItemDefinition, Long> available, boolean[] single, List<ItemDefinition> invIngredient, Map<ItemDefinition, ItemAmount> itemTimes, ResourceLocation taskId, long generation) {
        ItemStack result = r.output();
        List<MaidItem> maidItems = new ArrayList<>();

        int canCookAmount = getMaxAmount(available, single, itemTimes, taskId, generation);
        if (canCookAmount <= 0) return Collections.emptyList();
        int amount = canCookAmount;
        boolean isSingle = single[0] || r.isSingle();
        int endAmount = 1;
        if (isSingle) {
            amount = 1;
            endAmount = canCookAmount;
        }

        int ingredientIndex = 0;
        for (ItemDefinition definition : invIngredient) {
            ItemAmount itemAmount = itemTimes.get(definition);
            int minAmount = r.inItems().get(ingredientIndex++).test(definition.toStack(Integer.MAX_VALUE));
            itemAmount.setRecAmount(amount);

            int count = amount * minAmount;
            maidItems.add(new MaidItem(definition, count));
            available.put(definition, available.get(definition) - (long) count * endAmount);
        }

        MaidRec maidRec = new MaidRec(r.holder(), taskId, generation, 0, amount, List.of(result), maidItems, Map.of());
        return this.generateRecs(maidRec, endAmount);
    }

    protected List<MaidRec> createCookRec(MKRecipe<R> r, ItemStack tool, Map<ItemDefinition, Long> available, boolean[] single, List<ItemDefinition> invIngredient, Map<ItemDefinition, ItemAmount> itemTimes, ResourceLocation taskId, long generation) {
        ItemStack result = r.output();
        List<MaidItem> maidItems = new ArrayList<>();

        int canCookAmount = getMaxAmount(available, single, itemTimes, taskId, generation);
        if (canCookAmount <= 0) return Collections.emptyList();
        int amount = canCookAmount;
        boolean isSingle = single[0] || r.isSingle();
        int endAmount = 1;
        if (isSingle) {
            amount = 1;
            endAmount = canCookAmount;
        }

        int ingredientIndex = 0;
        for (ItemDefinition definition : invIngredient) {
            ItemAmount itemAmount = itemTimes.get(definition);
            itemAmount.setRecAmount(amount);
            int minAmount = r.inItems().get(ingredientIndex++).test(definition.toStack(Integer.MAX_VALUE));

            int count = amount * minAmount;
            maidItems.add(new MaidItem(definition, count));
            available.put(definition, available.get(definition) - (long) count * endAmount);
        }
        MaidRec maidRec = new MaidRec(r.holder(), taskId, generation, 0, amount, List.of(result), withTool(maidItems, tool), Map.of());

        return this.generateRecs(maidRec, endAmount);
    }

    protected List<MaidRec> generateRecs(MaidRec maidRec, int count) {
        List<MaidRec> maidRecList = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            maidRecList.add(maidRec);
        }
        return maidRecList;
    }

    protected int getMaxAmount(Map<ItemDefinition, Long> available, boolean[] single, Map<ItemDefinition, ItemAmount> itemTimes, ResourceLocation taskId, long generation) {
        if (itemTimes.isEmpty()) return 0;
        int maxCount = 64;
        for (ItemDefinition itemDefinition : itemTimes.keySet()) {
            if (itemDefinition.getMaxStackSize() == 1) {
                // 最大份量为 64 份；
                // getStack(item).getMaxStackSize()：该种物品的最大堆叠数量，比如 鸡蛋为16个，那么他的最大份量就只能是16份；
                // available.get(item)：该种物品在背包中的数量；
                maxCount = Math.min(maxCount, (int) (available.get(itemDefinition) / itemTimes.get(itemDefinition).needCount()));
            } else {
                // 最大份量为 64 份；
                // getStack(item).getMaxStackSize()：该种物品的最大堆叠数量，比如 鸡蛋为16个，那么他的最大份量就只能是16份；
                // available.get(item)：该种物品在背包中的数量；
                maxCount = Math.min(Math.min(maxCount, itemDefinition.getMaxStackSize()), (int) (available.get(itemDefinition) / itemTimes.get(itemDefinition).needCount()));
            }
        }
        return maxCount;
    }

    protected final ItemStack getStack(Item item) {
        return item.getDefaultInstance();
    }

    private static List<MaidItem> withTool(List<MaidItem> ingredients, ItemStack tool) {
        List<MaidItem> materials = new ArrayList<>(ingredients);
        if (!tool.isEmpty()) materials.add(new MaidItem(ItemDefinition.of(tool), 1, MaidItem.Role.TOOL));
        return materials;
    }

    private List<RecipeHolder<R>> loadedHolders = List.of();
    protected RegistryAccess registryAccess = RegistryAccess.EMPTY;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public final List<MKRecipe<R>> getRecipes(Level level) {
        // A recipe family can contain several RecipeInput implementations; retain the cast at this API boundary.
        List<RecipeHolder<R>> holders = level.getRecipeManager().getAllRecipesFor((RecipeType) recipeType);
        if (this.recipes == null || !holders.equals(loadedHolders)) {
            this.registryAccess = level.registryAccess();
            this.loadedHolders = List.copyOf(holders);
            this.recipes = holders.stream().map(this::createMKRecipe).toList();
        }
        return this.recipes;
    }

    protected MKRecipe<R> createMKRecipe(RecipeHolder<R> holder) {
        R r = holder.value();
        return new MKRecipe<>(holder, recipeInfoProvider.isSingle(this, r),
                recipeInfoProvider.getIngredients(this, r), recipeInfoProvider.getOutput(this, r),
                recipeInfoProvider.getContainer(this, r));
    }

    public RecipeType<R> getRecipeType() { return recipeType; }
    public String getRecipeTypeId() { return recipeType.toString(); }

    public static class RecipeInfoProvider<R extends Recipe<? extends RecipeInput>> {
        private static final RecipeInfoProvider<?> INSTANCE = new RecipeInfoProvider<>();
        @SuppressWarnings("unchecked")
        public static <R extends Recipe<? extends RecipeInput>> RecipeInfoProvider<R> getInstance() {
            return (RecipeInfoProvider<R>) INSTANCE;
        }
        public List<RecIngredient> getIngredients(RecSerializerManager<R> rsm, R rec) { return RecIngredient.from(rec.getIngredients()); }
        public ItemStack getOutput(RecSerializerManager<R> rsm, R rec) { return rec.getResultItem(rsm.registryAccess); }
        public ItemStack getContainer(RecSerializerManager<R> rsm, R rec) { return ItemStack.EMPTY; }
        public boolean isSingle(RecSerializerManager<R> rsm, R rec) { return false; }
    }
}
