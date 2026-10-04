package com.github.wallev.maidsoulkitchen.task.cook.common.manager;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.wallev.maidsoulkitchen.api.task.v1.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.inventory.container.item.BagType;
import com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.chest.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.itemdown.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.github.wallev.maidsoulkitchen.task.cook.common.task.CookTaskManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.*;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import com.github.wallev.maidsoulkitchen.util.BubbleUtil;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * Source: 58ec08ec task/cook/common/manager/MaidCookManager.java (MIT).
 * Ports the upstream inventory views, chest scan -> generate -> transfer states and MaidRec queue.
 * Holder/RecipeInput, canonical KitchenData, existing hub/bauble lookup and safe transfers adapt 1.21.
 * Reload/task/inventory invalidation and commit-after-acceptance fix confirmed upstream defects.
 * Replaces beta MaidRecipesManager's Pair queue, plannedResults and synchronous ingredient planner.
 * The temporary getRecipeIngredient projection owns no state and never polls; P3-P5 consumers replace
 * it with Be/Rule transactions. Empty upstream hooks and unused duplicate queues are omitted.
 */
public class MaidCookManager<R extends Recipe<? extends RecipeInput>> {
    protected final EntityMaid maid;
    protected final ServerLevel level;
    protected final ICookTask<?, R> task;
    protected final RecSerializerManager<R> recSerializerManager;
    protected final RecsGenerate<R> recsGenerate = new RecsGenerate<>();
    protected final ChestInventory chestInputInventory = new ChestInventory();
    protected final HubItemDown hubItemDown = new HubItemDown();
    protected final LinkedList<MaidRec> maidRecs = new LinkedList<>();
    protected IMaidCookInventory cookInv;
    protected CookData cookData;
    protected List<BlockEntity> validChests = List.of();
    protected Map<BagType, List<BlockPos>> bindingPoses = Map.of();
    protected boolean hasCulinaryHub;
    // Upstream states: idle, scanning chests, generating recipes, transferring resources.
    protected int runState;
    protected int tryTime;
    private ItemStack lastCulinaryHub = ItemStack.EMPTY;
    private long generation;
    private long recipeFingerprint = Long.MIN_VALUE;
    private long recipeGeneration = Long.MIN_VALUE;
    private long collectIngredientsBubbleId = -1;
    private long availableFoodsBubbleId = -1;
    private long noIngredientBubbleId = -1;

    public MaidCookManager(RecSerializerManager<R> recSerializerManager, EntityMaid maid, ICookTask<?, R> task) {
        this.recSerializerManager = recSerializerManager;
        this.maid = maid;
        this.level = (ServerLevel) maid.level();
        this.task = task;
    }

    public EntityMaid getMaid() { return maid; }
    public RecSerializerManager<R> getRecSerializerManager() { return recSerializerManager; }
    public IMaidCookInventory getCookInv() { return cookInv; }
    public ItemInventory getItemInventory() { return cookInv.getItemInventory(); }
    public ChestInventory getChestInputInventory() { return chestInputInventory; }
    public int getRunState() { return runState; }
    public long getGeneration() { return generation; }
    public boolean recsGenerateDone() { return recsGenerate.done(); }
    public ItemStack findCulinaryHub() { return ItemCulinaryHub.getItem(maid); }

    private boolean initInvData() {
        ItemStack hub = findCulinaryHub();
        Map<BagType, List<BlockPos>> bindings = ItemCulinaryHub.getBindPoses(hub);
        boolean changed = cookInv == null || hub != lastCulinaryHub || !bindings.equals(bindingPoses);
        if (changed) {
            hasCulinaryHub = !hub.isEmpty();
            lastCulinaryHub = hub;
            bindingPoses = bindings;
            cookInv = hasCulinaryHub ? new MaidCookBagInventory(maid, hub) : new MaidInventory(maid);
            cookInv.refreshInv();
        }
        return changed;
    }

    private boolean initTaskData() {
        CookData current = task.getTaskData(maid);
        long currentGeneration = CookTaskManager.getRecipeGeneration();
        boolean settingsChanged = cookData == null || !current.mode().equals(cookData.mode())
                || !current.whitelistRecs().equals(cookData.whitelistRecs())
                || !current.blacklistRecs().equals(cookData.blacklistRecs());
        if (!settingsChanged && currentGeneration == recipeGeneration) return false;
        List<MKRecipe<R>> recipes = recSerializerManager.getRecipes(level);
        long fingerprint = 1;
        for (MKRecipe<R> recipe : recipes) {
            fingerprint = 31 * fingerprint + recipe.id().hashCode();
            fingerprint = 31 * fingerprint + System.identityHashCode(recipe.rec());
        }
        boolean changed = settingsChanged || currentGeneration != recipeGeneration || fingerprint != recipeFingerprint;
        if (changed) {
            cookData = new CookData(current.mode(), current.whitelistRecs(), current.blacklistRecs());
            recipeFingerprint = fingerprint;
            recipeGeneration = currentGeneration;
            recsGenerate.setRecs(getValidRecipesFor(recipes));
        }
        return changed;
    }

    private List<MKRecipe<R>> getValidRecipesFor(List<MKRecipe<R>> recipes) {
        boolean whitelist = cookData.mode().equals(CookData.Mode.WHITELIST.name);
        List<String> selected = cookData.recs(cookData.mode());
        return recipes.stream().filter(recipe -> selected.contains(recipe.idStr()) == whitelist).toList();
    }

    private boolean isLastCookInv() {
        IItemHandler input = cookInv.getInputInv();
        List<ItemStack> previous = cookInv.getLastInvStack();
        if (input.getSlots() != previous.size()) return false;
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (!ItemStack.matches(input.getStackInSlot(slot), previous.get(slot))) return false;
        }
        return true;
    }

    public boolean checkAndInit() {
        if (!isCurrentTask()) {
            invalidate();
            return false;
        }
        boolean settingsChanged = initTaskData();
        boolean storageChanged = initInvData();
        boolean inventoryChanged = !isLastCookInv();
        // Refresh detects external serialized component changes as well as handler mutations.
        List<ItemStack> previous = cookInv.getLastInvStack().stream().map(ItemStack::copy).toList();
        cookInv.refreshInv();
        inventoryChanged |= !sameInventory(previous, cookInv.getLastInvStack());
        if (settingsChanged || storageChanged || inventoryChanged) invalidate();
        return true;
    }

    private static boolean sameInventory(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) if (!ItemStack.matches(left.get(index), right.get(index))) return false;
        return true;
    }

    public void checkAndCreateRecipes() {
        if (!checkAndInit() || runState > 0 || !maidRecs.isEmpty()) return;
        if (isLastCookInv() && tryTime++ < 10) return;
        createRecipesIngredients();
    }

    /** Temporary Move entry point; work remains owned by the upstream manager's tick states. */
    public boolean checkAndCreateRecipesIngredients() {
        if (!checkAndInit()) return false;
        checkAndCreateRecipes();
        return true;
    }

    protected List<MKRecipe<R>> getRecs() {
        List<MKRecipe<R>> recipes = new ArrayList<>(recsGenerate.getRecs());
        Collections.shuffle(recipes);
        return recipes;
    }

    private void createRecipesIngredients() {
        clear();
        itemUnIngre2Chest();
        cookInv.refreshInv();
        if (hasCulinaryHub) startCollectChestIngredient();
        else createIngres();
        makeCollectIngredientsBubble();
    }

    protected List<BlockEntity> initChestData() {
        List<BlockPos> poses = new ArrayList<>();
        List<BlockEntity> entities = new ArrayList<>();
        List<IItemHandler> handlers = new ArrayList<>();
        int slots = 0;
        for (BlockPos pos : getBindingTypePoses(BagType.INGREDIENT)) {
            if (isExtraZone(pos) || !level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler handler = be == null || !isStorageAccessible(be) ? null : ItemCulinaryHub.getBeInv(level, be);
            if (handler == null) continue;
            poses.add(pos); entities.add(be); handlers.add(handler); slots += handler.getSlots();
        }
        chestInputInventory.init(new ChestInvsData(poses, entities, handlers, slots));
        return entities;
    }

    public void startCollectChestIngredient() {
        validChests = initChestData();
        runState = 1;
        if (chestInputInventory.done()) startGenerateRecs();
    }

    public void startGenerateRecs() {
        if (!checkAndInit()) return;
        Map<ItemDefinition, Long> available = new HashMap<>(getItemInventory().getStacks());
        chestInputInventory.getAvailable().forEach((definition, count) -> available.merge(definition, count, Long::sum));
        hubItemDown.init(getInputInv(), getItemInventory().getStacks());
        recsGenerate.setAvailable(available);
        recsGenerate.setCurrentRecs(getRecs());
        runState = 2;
    }

    protected void createIngres() {
        recsGenerate.setAvailable(getItemInventory().getStacks());
        recsGenerate.setCurrentRecs(getRecs());
        runState = 2;
    }

    public void tickGenerateRecs() {
        if (!checkAndInit() || runState != 2) return;
        maidRecs.addAll(recSerializerManager.createMaidRecs(recsGenerate.tickRun(), recsGenerate.getAvailable(),
                (recipe, range) -> { }, recipe -> true, reservation -> !hasCulinaryHub || hubItemDown.read(reservation),
                done -> { if (done) recsGenerate.markDone(); }, task.getUid(), generation));
    }

    public boolean recsGenDoneAndUpdate() {
        if (!checkAndInit() || runState != 2) return false;
        runState = 3;
        if (hasCulinaryHub && !extractedChestItem2Bag()) {
            syncInv();
            invalidate();
            makeResultsBubble();
            return false;
        }
        cookInv.syncInv();
        cookInv.refreshInv();
        for (BlockEntity be : validChests) makeChanged(be);
        resetState();
        makeResultsBubble();
        return true;
    }

    /** Upstream removeItemStacks transfer: use actual Handler receipts, never pre-insert or assume extraction. */
    private boolean extractedChestItem2Bag() {
        for (BlockEntity be : validChests) {
            if (be.isRemoved() || !level.isLoaded(be.getBlockPos()) || level.getBlockEntity(be.getBlockPos()) != be
                    || !isStorageAccessible(be)) return false;
        }
        for (var use : hubItemDown.getUseItemDef().entrySet()) {
            ItemDefinition definition = use.getKey();
            int missing = use.getValue() - CookInventoryTransactions.count(getInputInv(), definition::is);
            if (missing <= 0) continue;
            ChestInventory.ChestItemDef sources = chestInputInventory.getItemDefinitions().get(definition);
            if (sources == null) return false;
            for (var handler : sources.getValueMap().entrySet()) {
                for (int slot : handler.getValue()) {
                    var receipt = CookInventoryTransactions.transfer(handler.getKey(), slot, getInputInv(), missing, definition::is);
                    CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), receipt.remainder(), maid);
                    missing -= receipt.inserted();
                    if (missing <= 0) break;
                }
                if (missing <= 0) break;
            }
            if (missing > 0) return false;
        }
        return true;
    }

    public List<MaidRec> getMaidRecs() { return hasMaidRecs() ? List.copyOf(maidRecs) : List.of(); }
    public boolean hasMaidRecs() {
        if (!isCurrentTask() || recipeGeneration != CookTaskManager.getRecipeGeneration()) return false;
        return runState == 0 && !maidRecs.isEmpty();
    }
    public MaidRec peekMaidRec() {
        if (!checkAndInit()) return null;
        if (!hasMaidRecs()) return null;
        MaidRec recipe = maidRecs.peek();
        if (recipe.resolve(level.getRecipeManager(), task.getUid(), generation).isEmpty()) { invalidate(); return null; }
        return recipe;
    }

    /** Fix upstream poll-before-insertion: the Be/Rule calls this only after accepting the work unit. */
    public boolean commitMaidRec(MaidRec recipe) {
        if (!isCurrentTask() || initTaskData() || initInvData()) { invalidate(); return false; }
        if (recipe == null || maidRecs.peek() != recipe
                || recipe.resolve(level.getRecipeManager(), task.getUid(), generation).isEmpty()) return false;
        maidRecs.poll();
        cookInv.syncInv(); cookInv.refreshInv();
        BubbleUtil.makeFood(maid, recipe.result());
        return true;
    }

    /** Temporary beta consumer projection from MaidRec; no plan is removed and no result queue exists. */
    @Deprecated
    public Pair<List<Integer>, List<List<ItemStack>>> getRecipeIngredient() {
        MaidRec recipe = peekMaidRec();
        if (recipe == null) return Pair.of(List.of(), List.of());
        List<Integer> counts = new ArrayList<>();
        List<List<ItemStack>> materials = new ArrayList<>();
        if (recSerializerManager instanceof FluidRecSerializerManager<?> && recipe.maidItems().stream()
                .noneMatch(material -> material.role() == MaidItem.Role.FLUID)) {
            counts.add(0); materials.add(List.of());
        }
        for (MaidItem material : recipe.maidItems()) {
            counts.add(material.count());
            var stacks = getItemInventory().getItemStacks(material.item());
            materials.add(stacks == null ? List.of() : new ArrayList<>(stacks));
        }
        return Pair.of(counts, materials);
    }

    public boolean isRecipeEnabled(ResourceLocation recipeId) {
        return recsGenerate.getRecs().stream().anyMatch(recipe -> recipe.id().equals(recipeId));
    }

    public void makeResultsBubble() {
        if (maidRecs.isEmpty()) {
            if (!recsGenerate.getRecs().isEmpty()) noIngredientBubbleId = BubbleUtil.noIngredient(maid, noIngredientBubbleId);
            return;
        }
        List<ItemStack> results = maidRecs.stream().flatMap(recipe -> recipe.results().stream().map(result ->
                result.copyWithCount(result.getCount() * recipe.amount()))).toList();
        availableFoodsBubbleId = BubbleUtil.availableFoods(maid, results, availableFoodsBubbleId);
    }

    public void makeCollectIngredientsBubble() {
        collectIngredientsBubbleId = BubbleUtil.collectIngredients(maid, collectIngredientsBubbleId);
    }

    public void clear() {
        resetState(); recsGenerate.clear(); maidRecs.clear(); hubItemDown.clear(); chestInputInventory.clear();
    }
    public void resetState() { runState = 0; tryTime = 0; }
    private void invalidate() { clear(); generation++; tryTime = 10; }
    private List<BlockPos> getBindingTypePoses(BagType type) { return bindingPoses.getOrDefault(type, List.of()); }
    private boolean isExtraZone(BlockPos pos) {
        double range = maid.getRestrictRadius() * ItemCulinaryHub.WORK_RANGE;
        return maid.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > range * range;
    }

    private boolean isCurrentTask() { return maid.isAlive() && maid.getTask().getUid().equals(task.getUid()); }

    /** Existing WIP CulinaryHubWorkStorage.isStorageAccessible: preserve TLM opened-chest gating. */
    private boolean isStorageAccessible(BlockEntity be) {
        for (IChestType type : ChestManager.getAllChestTypes()) {
            if (type.isChest(be)) return type.getOpenCount(level, be.getBlockPos(), be) <= 0;
        }
        return true;
    }

    public IItemHandlerModifiable getInputInv() { return cookInv.getInputInv(); }
    public IItemHandlerModifiable getOutputInv() { return cookInv.getOutputInv(); }
    public IItemHandlerModifiable getIngredientInv() { return getInputInv(); }
    public IItemHandlerModifiable getOutputAdditionInv() { return getInputInv(); }

    public void syncInv() {
        if (cookInv == null) return;
        // Unmigrated consumers cannot silently retain a plan after mutating its live inputs.
        if (!isLastCookInv()) invalidate();
        cookInv.syncInv(); cookInv.refreshInv();
    }

    public GatherResult getItem(Predicate<ItemStack> predicate) {
        int slot = ItemsUtil.findStackSlot(getInputInv(), predicate);
        if (slot >= 0) return new GatherResult(getInputInv(), slot);
        for (BlockPos pos : getBindingTypePoses(BagType.OUTPUT_ADDITION)) {
            if (!level.isLoaded(pos) || isExtraZone(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler handler = be == null || !isStorageAccessible(be) ? null : ItemCulinaryHub.getBeInv(level, be);
            if (handler == null) continue;
            slot = ItemsUtil.findStackSlot(handler, predicate);
            if (slot >= 0) return new GatherResult(handler, slot);
        }
        return GatherResult.FAIL;
    }

    // Short-lived method-name adaptation for beta Be consumers; all operations use the owned Handler.
    public boolean hasOutputAdditionItem(Predicate<ItemStack> predicate) { return !getItem(predicate).isFail(); }
    public boolean hasOutputAdditionItem(ItemStack stack) { return hasOutputAdditionItem(candidate -> candidate.is(stack.getItem())); }
    public ItemStack findOutputAdditionItem(Predicate<ItemStack> predicate) {
        GatherResult result = getItem(predicate);
        ItemStack stack = result.isFail() ? ItemStack.EMPTY : result.queryItemStack(64);
        syncInv(); return stack;
    }
    public ItemStack findOutputAdditionItem(ItemStack stack) { return findOutputAdditionItem(candidate -> candidate.is(stack.getItem())); }
    public int getOutputAdditionItemCount(ItemStack stack) {
        int count = CookInventoryTransactions.count(getInputInv(), candidate -> candidate.is(stack.getItem()));
        if (count > 0) return count;
        GatherResult result = getItem(candidate -> candidate.is(stack.getItem()));
        return result.isFail() ? 0 : result.getItemHandler().getStackInSlot(result.getSlot()).getCount();
    }
    public void shrinkOutputAdditionItem(ItemStack stack, int count) {
        GatherResult result = getItem(candidate -> candidate.is(stack.getItem()));
        if (!result.isFail()) result.queryItemStack(count);
        syncInv();
    }

    private void itemCookBag2Chest(BagType type, boolean requireHasItem) {
        if (!hasCulinaryHub) return;
        IItemHandlerModifiable source = cookInv.getAvailableInv(type);
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot).copy();
            if (stack.isEmpty() || requireHasItem && retainInput(stack)) continue;
            for (BlockPos pos : getBindingTypePoses(type)) {
                if (!level.isLoaded(pos) || isExtraZone(pos)) continue;
                BlockEntity be = level.getBlockEntity(pos);
                IItemHandler handler = be == null || !isStorageAccessible(be) ? null : ItemCulinaryHub.getBeInv(level, be);
                if (handler == null || requireHasItem && !CookInventoryTransactions.containsItem(handler, stack)) continue;
                var receipt = CookInventoryTransactions.transfer(source, slot, handler, stack.getCount(),
                        item -> ItemStack.isSameItemSameComponents(item, stack));
                CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), receipt.remainder(), maid);
                if (receipt.inserted() > 0) makeChanged(be);
                if (source.getStackInSlot(slot).isEmpty()) break;
            }
        }
        cookInv.syncInv(); cookInv.refreshInv();
    }

    private boolean retainInput(ItemStack stack) {
        if (stack.getMaxStackSize() == 1 || stack.is(net.minecraft.world.item.Items.BOWL)
                || stack.is(net.minecraft.world.item.Items.BUCKET) || stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) return true;
        return recsGenerate.getRecs().stream().anyMatch(recipe -> recipe.inItems().stream().anyMatch(ingredient -> ingredient.test(stack) > 0)
                || recipe.tool().test(stack) > 0 || recipe.inFluids().stream().anyMatch(fluid -> fluid.is(stack.getItem())));
    }

    public void itemOutput2Chest() { itemCookBag2Chest(BagType.OUTPUT, false); }
    public void itemUnIngre2Chest() { itemCookBag2Chest(BagType.INGREDIENT, true); }
    public void tranOutput2Chest() { itemOutput2Chest(); }
    public void tranUnIngre2Chest() { itemUnIngre2Chest(); }
    public static void makeChanged(BlockEntity be) {
        be.setChanged();
        if (be.getLevel() != null) be.getLevel().sendBlockUpdated(be.getBlockPos(), be.getBlockState(), be.getBlockState(), 3);
    }
}
