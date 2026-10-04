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
    private ItemStack loanedTool = ItemStack.EMPTY;

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
        Map<ItemDefinition, Long> available = getPhysicalAvailable();
        chestInputInventory.getAvailable().forEach((definition, count) -> available.merge(definition, count, Long::sum));
        hubItemDown.init(getInputInv(), getPhysicalAvailable());
        recsGenerate.setAvailable(available);
        recsGenerate.setCurrentRecs(getRecs());
        runState = 2;
    }

    protected void createIngres() {
        recsGenerate.setAvailable(getItemInventory().getStacks());
        recsGenerate.setCurrentRecs(getRecs());
        runState = 2;
    }

    /** Source: ToolRecSerializerManager.processTool and TickCookRule's existing hand-tool check.
     * A hub excludes equipment from its stored inputs; the source consequently cannot plan with
     * an already held native tool. Include that one physical tool in the derived availability,
     * without reserving a duplicate from a chest or maintaining a second material inventory. */
    private Map<ItemDefinition, Long> getPhysicalAvailable() {
        Map<ItemDefinition, Long> available = new HashMap<>(getItemInventory().getStacks());
        if (hasCulinaryHub && recSerializerManager instanceof ToolRecSerializerManager<?>
                && recsGenerate.getRecs().stream().anyMatch(recipe -> recipe.tool().test(maid.getMainHandItem()) > 0))
            available.merge(ItemDefinition.of(maid.getMainHandItem()), 1L, Long::sum);
        return available;
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
            if (recSerializerManager instanceof ToolRecSerializerManager<?> && definition.is(maid.getMainHandItem())
                    && recsGenerate.getRecs().stream().anyMatch(recipe -> recipe.tool().test(maid.getMainHandItem()) > 0)) missing--;
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
        return peekMaidRec(recipe -> true);
    }

    /** Upstream hasMaidRecs/pollMaidRec device selection, without a second index queue. */
    public MaidRec peekMaidRec(com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase<?> cookBe) {
        return peekMaidRec();
    }
    public boolean hasMaidRecs(com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase<?> cookBe) {
        return peekMaidRec(cookBe) != null;
    }
    protected final MaidRec peekMaidRec(Predicate<MaidRec> matches) {
        if (!checkAndInit()) return null;
        if (!hasMaidRecs()) return null;
        for (MaidRec recipe : maidRecs) {
            if (recipe.resolve(level.getRecipeManager(), task.getUid(), generation).isEmpty()) { invalidate(); return null; }
            if (recipe.maidItems().stream().anyMatch(material -> material.role() == MaidItem.Role.TOOL
                    && getItem(material.item()::is).isFail())) { invalidate(); return null; }
            if (matches.test(recipe)) return recipe;
        }
        return null;
    }

    /** Fix upstream poll-before-insertion: the Be/Rule calls this only after accepting the work unit. */
    public boolean commitMaidRec(MaidRec recipe) {
        if (!isCurrentTask() || initTaskData() || initInvData()) { invalidate(); return false; }
        if (recipe == null || !maidRecs.contains(recipe)
                || recipe.resolve(level.getRecipeManager(), task.getUid(), generation).isEmpty()) return false;
        maidRecs.remove(recipe);
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
    protected final void invalidate() { clear(); generation++; tryTime = 10; }
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

    /** Upstream takeItem/insertAndShrink moved here so the manager owns all inventory transactions.
     * NeoForge transfer receipts prevent duplication on refused extraction and recover remainders. */
    public int takeItem(IItemHandler source, int slot, IItemHandler destination, Predicate<ItemStack> matches) {
        var receipt = CookInventoryTransactions.transfer(source, slot, destination,
                source.getStackInSlot(slot).getCount(), matches);
        CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), receipt.remainder(), maid);
        return receipt.inserted();
    }

    public int insertItem(GatherResult source, IItemHandlerModifiable destination, int slot, int amount) {
        if (source.isFail()) return 0;
        ItemStack expected = source.getItemHandler().getStackInSlot(source.getSlot()).copy();
        var receipt = CookInventoryTransactions.transfer(source.getItemHandler(), source.getSlot(),
                new net.neoforged.neoforge.items.wrapper.RangedWrapper(destination, slot, slot + 1), amount,
                stack -> ItemStack.isSameItemSameComponents(stack, expected));
        CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), receipt.remainder(), maid);
        return receipt.inserted();
    }

    public boolean canTakeResult(ItemStack result) {
        return result.getCount() > ItemHandlerHelper.insertItemStacked(getOutputInv(), result.copy(), true).getCount();
    }

    /** Source: CookBeBase.insertInputs/insertAndShrink (58ec08ec).
     * The source consumed plans before insertion and trusted live stacks. Keep ordered recipe slots,
     * preflight component counts and real slot acceptance, then use extract-first receipts.
     * A refused/partial insert rolls back only this attempt's physical inserts and invalidates its
     * plan; device leftovers remain visible for the Rule's next cleanup. No shadow work state survives.
     */
    public boolean insertInputs(MaidRec rec, IItemHandlerModifiable device, int start, int size) {
        return insertInputs(rec, device, start, size, false);
    }

    /** Source CookBeBase.insertFluidItems -> insertInputs ordering. The native fluid Be confirms
     * its tank before excluding already accepted FLUID materials from the item-slot transaction;
     * ordinary device callers cannot silently skip a planned fluid requirement. */
    public boolean insertInputs(MaidRec rec, IItemHandlerModifiable device, int start, int size, boolean fluidPrepared) {
        List<MaidItem> materials = rec == null ? List.of() : rec.maidItems().stream()
                .filter(material -> material.role() != MaidItem.Role.TOOL && !(fluidPrepared && material.role() == MaidItem.Role.FLUID)).toList();
        if (!checkAndInit() || rec == null || !maidRecs.contains(rec) || runState != 0
                || rec.resolve(level.getRecipeManager(), task.getUid(), generation).isEmpty()
                || materials.size() > size) return false;
        Map<ItemDefinition, Integer> needed = new HashMap<>();
        int[] missing = new int[materials.size()];
        for (int i = 0; i < missing.length; i++) {
            MaidItem material = materials.get(i);
            if (material.isEmpty()) continue;
            if (material.role() == MaidItem.Role.TOOL || material.role() == MaidItem.Role.FLUID) return false;
            ItemStack present = device.getStackInSlot(start + i);
            if (!present.isEmpty() && !material.item().is(present)) return false;
            missing[i] = Math.max(0, material.count() - present.getCount());
            needed.merge(material.item(), missing[i], Integer::sum);
            if (missing[i] > 0 && !device.insertItem(start + i, material.item().toStack(missing[i]), true).isEmpty()) return false;
        }
        for (var entry : needed.entrySet())
            if (CookInventoryTransactions.count(getInputInv(), entry.getKey()::is) < entry.getValue()) return false;
        int[] accepted = new int[missing.length];
        boolean complete = true;
        for (int i = 0; i < missing.length; i++) {
            MaidItem material = materials.get(i);
            for (int source = 0; source < getInputInv().getSlots() && accepted[i] < missing[i]; source++) {
                if (material.isEmpty() || !material.item().is(getInputInv().getStackInSlot(source))) continue;
                accepted[i] += insertItem(new GatherResult(getInputInv(), source), device, start + i, missing[i] - accepted[i]);
            }
            if (accepted[i] < missing[i]) { complete = false; break; }
        }
        if (!complete) {
            for (int i = accepted.length - 1; i >= 0; i--) {
                if (accepted[i] == 0) continue;
                var receipt = CookInventoryTransactions.transfer(device, start + i, getInputInv(), accepted[i],
                        materials.get(i).item()::is);
                CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), receipt.remainder(), maid);
            }
            syncInv(); invalidate();
        }
        return complete;
    }

    /** Source: DryingRackBe.insertInputs native placeFood boundary (58ec08ec).
     * Extract detached physical single-item inputs before the native consuming call instead of
     * passing aliased inventory stacks. Partial/refused native acceptance returns the remainder
     * and invalidates the plan; already accepted materials remain in the real device. No queue or
     * native timing is duplicated here. Used by devices whose native action initializes cooking. */
    public boolean insertInputs(MaidRec rec, Predicate<ItemStack> insertOne) {
        if (rec == null || peekMaidRec() != rec || rec.maidItems().size() != 1) return false;
        MaidItem material = rec.maidItems().getFirst();
        if (material.role() != MaidItem.Role.INGREDIENT
                || CookInventoryTransactions.count(getInputInv(), material.item()::is) < material.count()) return false;
        for (int count = 0; count < material.count(); count++) {
            GatherResult source = getItem(material.item()::is);
            ItemStack extracted = source.isFail() ? ItemStack.EMPTY : source.getItemHandler().extractItem(source.getSlot(), 1, false);
            boolean accepted = false;
            try { accepted = !extracted.isEmpty() && material.item().is(extracted) && insertOne.test(extracted) && extracted.isEmpty(); }
            finally {
                if (!extracted.isEmpty()) CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), source.backItemStack(extracted), maid);
                if (!accepted) { cookInv.syncInv(); cookInv.refreshInv(); invalidate(); }
            }
            if (!accepted) return false;
        }
        return true;
    }

    /** Source: upstream CookBeBase.useItem and beta tea-kettle replenishment. Uses the existing
     * 1.21 FakePlayer interaction boundary; only the inventory ownership is moved here. Extract
     * first so refused Handlers cannot duplicate native effects. Return the actual post-use hand
     * (including containers or partial failure), not a speculative refund of the original input.
     * A consuming interaction is not itself recipe acceptance; the device verifies its native state. */
    public boolean useItem(GatherResult source, BlockPos pos) {
        return useItem(source, pos, getInputInv(), null);
    }

    /** Source: BasinCookRule contItemStack/swapItem -> native SkeweringInput assemble (58ec08ec,
     * MIT). Native assembly consumes several inputs together, including counted ingredients.
     * The source/beta passed live inventory aliases and consumed the plan before device success.
     * Extract the complete unit first, pass only physical detached stacks to the device, and return
     * actual unconsumed remainders even on native failure. Be owns the native operation; this sole
     * manager owns receipts/output placement. No material queue or result cache is introduced. */
    public boolean useItems(MaidRec work, java.util.function.Function<List<ItemStack>, ItemStack> nativeUse) {
        if (work == null || peekMaidRec() != work || !canTakeResult(work.result())) return false;
        var materials = work.maidItems();
        if (materials.stream().anyMatch(item -> item.role() != MaidItem.Role.INGREDIENT)) return false;
        Map<ItemDefinition, Integer> needed = new HashMap<>();
        materials.forEach(item -> needed.merge(item.item(), item.count(), Integer::sum));
        if (needed.entrySet().stream().anyMatch(entry -> CookInventoryTransactions.count(getInputInv(), entry.getKey()::is) < entry.getValue())) return false;
        List<ItemStack> detached = new ArrayList<>();
        boolean complete = false;
        try {
            for (MaidItem material : materials) {
                ItemStack actual = ItemStack.EMPTY;
                int materialIndex = detached.size(); detached.add(actual);
                for (int slot = 0; slot < getInputInv().getSlots() && actual.getCount() < material.count(); slot++) {
                    if (!material.item().is(getInputInv().getStackInSlot(slot))) continue;
                    var part = getInputInv().extractItem(slot, material.count() - actual.getCount(), false);
                    if (part.isEmpty()) continue;
                    if (!material.item().is(part)) { CookInventoryTransactions.returnOrDrop(getInputInv(), part, maid); continue; }
                    if (actual.isEmpty()) { actual = part; detached.set(materialIndex, actual); } else actual.grow(part.getCount());
                }
                if (actual.getCount() != material.count()) return false;
            }
            var result = nativeUse.apply(detached);
            if (result.isEmpty()) return false;
            CookInventoryTransactions.returnOrDrop(getOutputInv(), result, maid);
            complete = true;
            return true;
        } finally {
            for (ItemStack remainder : detached) CookInventoryTransactions.returnOrDrop(getInputInv(), remainder, maid);
            cookInv.syncInv(); cookInv.refreshInv();
            if (!complete) invalidate();
        }
    }

    /** Source fluid insert/useItem output-container path. A pending work identity acknowledges
     * only its own physical transfers until Be confirms all inputs. Native fermentation can put
     * filled bottles in FakePlayer inventory rather than its hand; transfer those actual stacks
     * instead of manufacturing a replacement bottle as the beta fallback did. */
    public boolean useItem(GatherResult source, BlockPos pos, IItemHandler destination, MaidRec work) {
        if (source.isFail() || !level.isLoaded(pos)) return false;
        if (work != null && peekMaidRec() != work) return false;
        var fakePlayer = ((com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid) maid).tlmk$getFakePlayer();
        if (fakePlayer == null || fakePlayer.get() == null) return false;
        ItemStack preview = source.getItemHandler().extractItem(source.getSlot(), 1, true);
        ItemStack extracted = source.getItemHandler().extractItem(source.getSlot(), 1, false);
        if (extracted.isEmpty()) return false;
        if (!ItemStack.isSameItemSameComponents(preview, extracted)) {
            CookInventoryTransactions.returnOrDrop(getInputInv(), source.backItemStack(extracted), maid);
            syncInv(); invalidate(); return false;
        }
        var outcome = com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid.tryInteractUseOnBlockWithItem(maid, pos, extracted);
        if (ItemStack.isSameItemSameComponents(extracted, outcome.remainder()))
            CookInventoryTransactions.returnOrDrop(getInputInv(), source.backItemStack(outcome.remainder()), maid);
        else CookInventoryTransactions.returnOrDrop(destination, outcome.remainder(), maid);
        var nativeInventory = fakePlayer.get().getInventory();
        for (int slot = 0; slot < nativeInventory.getContainerSize(); slot++)
            CookInventoryTransactions.returnOrDrop(destination, nativeInventory.removeItemNoUpdate(slot), maid);
        if (work == null) syncInv();
        else { cookInv.syncInv(); cookInv.refreshInv(); }
        return outcome.result().consumesAction();
    }

    /** Source: upstream TickCookRule.swapItem/swapTool/backpackTool, and local cutting equipTool.
     * Live-stack copyAndClear could alias Handler contents or duplicate the previous hand item.
     * Extract first, transfer the old hand with real receipts, then lend exactly one physical tool.
     * The sole manager owns the loan; Rule holds only its lifecycle tick/process state. */
    public boolean equipTool(Predicate<ItemStack> tool) {
        if (tool.test(maid.getMainHandItem())) return true;
        GatherResult source = getItem(tool);
        if (source.isFail()) return false;
        ItemStack preview = source.getItemHandler().extractItem(source.getSlot(), 1, true);
        ItemStack extracted = source.getItemHandler().extractItem(source.getSlot(), 1, false);
        if (extracted.isEmpty()) return false;
        IItemHandlerModifiable storage = hasCulinaryHub ? getInputInv() : maid.getAvailableBackpackInv();
        if (!tool.test(extracted) || !ItemStack.isSameItemSameComponents(preview, extracted)) {
            CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), source.backItemStack(extracted), maid); return false;
        }
        var hand = maid.getHandsInvWrapper();
        if (!maid.getMainHandItem().isEmpty()) takeItem(hand, 0, storage, stack -> true);
        if (!maid.getMainHandItem().isEmpty()) {
            CookInventoryTransactions.returnOrDrop(maid.getAvailableBackpackInv(), source.backItemStack(extracted), maid);
            cookInv.syncInv(); cookInv.refreshInv(); return false;
        }
        maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, extracted);
        loanedTool = extracted;
        cookInv.syncInv(); cookInv.refreshInv();
        return true;
    }

    /** Return the actual (possibly damaged) loan once; task stop/reload replans remaining resources. */
    public void backpackTool() {
        if (!loanedTool.isEmpty() && maid.getMainHandItem() == loanedTool) {
            var actual = maid.getMainHandItem().copy();
            maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            CookInventoryTransactions.returnOrDrop(hasCulinaryHub ? getInputInv() : maid.getAvailableBackpackInv(), actual, maid);
        }
        loanedTool = ItemStack.EMPTY;
        invalidate();
        cookInv.syncInv(); cookInv.refreshInv();
    }

    public void syncInv() {
        if (cookInv == null) return;
        // Unmigrated consumers cannot silently retain a plan after mutating its live inputs.
        if (!isLastCookInv()) invalidate();
        cookInv.syncInv(); cookInv.refreshInv();
    }

    public GatherResult getItem(Predicate<ItemStack> predicate) {
        int slot = ItemsUtil.findStackSlot(getInputInv(), predicate);
        if (slot >= 0) return new GatherResult(getInputInv(), slot);
        if (hasCulinaryHub && recSerializerManager instanceof ToolRecSerializerManager<?>
                && predicate.test(maid.getMainHandItem()) && !maid.getMainHandItem().isEmpty())
            return new GatherResult(maid.getHandsInvWrapper(), 0);
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
        if (recSerializerManager.getFuels().stream().anyMatch(fuel -> fuel.is(stack.getItem()))) return true;
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
