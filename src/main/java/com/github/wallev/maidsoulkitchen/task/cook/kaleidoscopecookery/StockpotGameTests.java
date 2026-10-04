package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.StockpotTaskData;
import com.github.wallev.maidsoulkitchen.init.MkItems;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.inventory.container.item.BagType;
import com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory;
import com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CulinaryHubWorkStorage;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.StockpotBlockEntity;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.container.StockpotInput;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotVisuals;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModItems;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModRecipes;
import com.github.ysbbbbbb.kaleidoscopecookery.init.ModSoupBases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.ArrayList;
import java.util.List;

/** Real KC 1.4.1 interactions, with synthetic recipes explicitly isolated from shipped datapacks. */
@PrefixGameTestTemplate(false)
public final class StockpotGameTests {
    private static final ResourceLocation NORMAL = id("test_stockpot_normal");
    private static final ResourceLocation FLEX = id("test_stockpot_flex");
    private static final ResourceLocation RETURNED_CARRIER = id("test_stockpot_returned_carrier");
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, name); }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void halfPotUsesOnlyMissingComponentsAndRevokesEditedConditions(GameTestHelper h) {
        var level = h.getLevel(); var recipes = level.getRecipeManager(); var original = List.copyOf(recipes.getRecipes());
        List<EntityMaid> maids = new ArrayList<>();
        try {
            var holder = new RecipeHolder<>(id("half_pot_components"), new StockpotRecipe(ingredients(Items.CARROT, Items.POTATO), ModSoupBases.WATER,
                    new ItemStack(Items.BEETROOT_SOUP, 2), 20, Ingredient.of(Items.BOWL), StockpotVisuals.DEFAULT));
            recipes.replaceRecipes(List.of(holder)); com.github.wallev.maidsoulkitchen.task.cook.common.task.CookTaskManager.recipesReloaded();
            var maid = maid(h, maids); var task = (TaskKcStockpot) maid.getTask();
            setSettings(maid, new StockpotTaskData(new RecipeFilterData(RecipeFilterData.Mode.WHITELIST, List.of(holder.id()), List.of()), false));
            var nativeBe = pot(h, new BlockPos(2, 2, 2));
            StockpotAdapter.addSoupBase(nativeBe, level, maid, new ItemStack(Items.WATER_BUCKET)); maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var existing = new ItemStack(Items.CARROT); existing.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("existing native half pot"));
            StockpotAdapter.addIngredient(nativeBe, level, maid, existing.copy());
            maid.getMaidBauble().setStackInSlot(0, MkItems.CULINARY_HUB.get().getDefaultInstance());
            var cm = task.getRecipesManager(maid); cm.checkAndInit(); var input = cm.getInputInv();
            var lid = ModItems.STOCKPOT_LID.get().getDefaultInstance(); lid.setDamageValue(23);
            input.setStackInSlot(0, lid.copy()); input.setStackInSlot(1, new ItemStack(Items.BOWL, 2)); cm.syncInv(); finishPlanning(cm);
            var be = new StockpotBe(maid); be.setBe(nativeBe);
            h.assertTrue(cm.peekMaidRec(be) == null && !be.canTakeInputs(cm) && nativeBe.getInputs().stream().filter(stack -> !stack.isEmpty()).count() == 1,
                    "a missing half-pot ingredient must wait without removing its existing native material");
            var missing = new ItemStack(Items.POTATO); missing.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("missing component material"));
            input.setStackInSlot(2, missing.copy()); cm.syncInv(); finishPlanning(cm); var work = cm.peekMaidRec(be);
            h.assertTrue(work != null && work.maidItems().stream().filter(item -> item.role() == com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem.Role.INGREDIENT)
                    .mapToInt(item -> item.count()).sum() == 1 && work.maidItems().stream().anyMatch(item -> item.role() == com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem.Role.DEVICE_INPUT && item.item().is(existing)),
                    "MaidRec must reserve exactly the one missing component and retain the native half-pot condition");
            var actualExisting = nativeBe.getInputs().stream().filter(stack -> !stack.isEmpty()).findFirst().orElseThrow();
            actualExisting.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("player changed input"));
            h.assertTrue(cm.peekMaidRec(be) == null && !be.insertInputs(work, cm) && input.getStackInSlot(2).getCount() == 1,
                    "component-only edits to the native half pot must revoke stale work before consuming a missing ingredient");
            actualExisting.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, existing.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
            finishPlanning(cm); work = cm.peekMaidRec(be);
            var refused = new ItemStackHandler(1) {
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return simulate ? super.extractItem(slot, amount, true) : ItemStack.EMPTY; }
            }; refused.setStackInSlot(0, missing.copy()); int[] nativeCalls = {0};
            h.assertTrue(!cm.useNativeItem(new com.github.wallev.maidsoulkitchen.task.cook.common.manager.GatherResult(refused, 0), cm.getInputInv(), work, false,
                    stack -> { nativeCalls[0]++; return StockpotAdapter.addIngredient(nativeBe, level, maid, stack); }) && nativeCalls[0] == 0 && refused.getStackInSlot(0).getCount() == 1,
                    "real extraction refusal must not reach KC or copy its native effect");
            for (int slot=0; slot<cm.getOutputInv().getSlots(); slot++) cm.getOutputInv().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            h.assertTrue(!be.insertInputs(work, cm) && !nativeBe.hasLid() && input.getStackInSlot(2).getCount() == 1,
                    "a full output buffer must preserve the actual half pot, lid and missing component");
            for (int slot=0; slot<cm.getOutputInv().getSlots(); slot++) cm.getOutputInv().setStackInSlot(slot, ItemStack.EMPTY);
            h.assertTrue(be.insertInputs(work, cm) && cm.commitMaidRec(work) && cm.getMaidRecs().isEmpty() && nativeBe.hasLid()
                    && nativeBe.getLidItem().getDamageValue() == 23 && nativeBe.getInputs().stream().filter(stack -> !stack.isEmpty()).count() == 2
                    && nativeBe.getInputs().stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, existing))
                    && nativeBe.getInputs().stream().anyMatch(stack -> ItemStack.isSameItemSameComponents(stack, missing))
                    && input.getStackInSlot(2).isEmpty() && CookInventoryTransactions.count(input, stack -> stack.is(Items.BOWL)) == 2,
                    "native cover must acknowledge the completed half pot once, preserve components, and keep carriers for actual serving");
        } finally { recipes.replaceRecipes(original); com.github.wallev.maidsoulkitchen.task.cook.common.task.CookTaskManager.recipesReloaded(); maids.forEach(EntityMaid::discard); }
        h.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, timeoutTicks = 160)
    public static void transactionsAndLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        List<EntityMaid> maids = new ArrayList<>();
        var experimental = com.github.wallev.maidsoulkitchen.config.subconfig.TaskConfig.EXPERIMENTAL_FEATURES;
        boolean originalExperimental = experimental.get();
        experimental.set(false);
        // Sample the real KC data before installing controlled fixtures; never label synthetic overlap as built-in.
        long overlaps = manager.getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE).stream().filter(holder -> {
            List<ItemStack> samples = holder.value().ingredients().stream()
                    .map(ingredient -> ingredient.isEmpty() || ingredient.getItems().length == 0
                            ? ItemStack.EMPTY : ingredient.getItems()[0].copyWithCount(1)).toList();
            StockpotInput input = new StockpotInput(samples, holder.value().soupBase());
            return manager.getRecipesFor(ModRecipes.STOCKPOT_RECIPE, input, level).size() > 1;
        }).count();
        MaidsoulKitchen.LOGGER.info("Stockpot native recipe representative samples with ordinary overlap: {}", overlaps);
        Runnable cleanup = () -> {
            manager.replaceRecipes(original); experimental.set(originalExperimental); maids.forEach(EntityMaid::discard);
        };
        try {
            forceCompatibilityClasses();
            var nativeOptions = StockpotAdapter.getRecipeOptions(level, false);
            for (String family : List.of("stockpot/rice_", "stockpot/dumpling_count_")) {
                var quantityOption = nativeOptions.stream()
                        .filter(option -> option.recipeIds().stream().anyMatch(recipeId -> recipeId.getPath().startsWith(family)))
                        .toList();
                helper.assertTrue(quantityOption.size() == 1 && quantityOption.getFirst().recipeIds().size() == 9,
                        "real KC quantity family must display once and retain all nine IDs: " + family);
            }
            MaidsoulKitchen.LOGGER.info("Stockpot native quantity groups: {} raw recipes -> {} menu entries",
                    manager.getAllRecipesFor(ModRecipes.STOCKPOT_RECIPE).size(), nativeOptions.size());
            var legacySettings = StockpotTaskData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    new com.google.gson.JsonObject()).getOrThrow();
            helper.assertTrue(!legacySettings.allowFlexRecipes(), "missing persisted Flex flag must default off");
            var enabledSettings = new StockpotTaskData(RecipeFilterData.DEFAULT, true);
            var encodedSettings = StockpotTaskData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                    enabledSettings).getOrThrow();
            helper.assertTrue(StockpotTaskData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    encodedSettings).getOrThrow().equals(enabledSettings), "task settings must survive codec persistence");
            var ordinary = new StockpotRecipe(ingredients(Items.CARROT, Items.POTATO), ModSoupBases.WATER,
                    new ItemStack(Items.BEETROOT_SOUP, 2), 2, Ingredient.of(Items.BOWL), StockpotVisuals.DEFAULT);
            var flex = new FlexStockpotRecipe(ingredients(Items.APPLE, Items.WHEAT), ModSoupBases.WATER,
                    new ItemStack(Items.COOKIE, 2), 2, Ingredient.of(Items.BOWL), StockpotVisuals.DEFAULT);
            var returnedCarrier = new StockpotRecipe(ingredients(Items.MUSHROOM_STEW), ModSoupBases.WATER,
                    new ItemStack(Items.BEETROOT_SOUP), 2, Ingredient.of(Items.BOWL), StockpotVisuals.DEFAULT);
            List<RecipeHolder<?>> fixtures = List.of(new RecipeHolder<>(NORMAL, ordinary), new RecipeHolder<>(FLEX, flex),
                    new RecipeHolder<>(RETURNED_CARRIER, returnedCarrier));
            manager.replaceRecipes(fixtures);
            for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) helper.setBlock(x, 0, z, Blocks.STONE);
            quantityCases(helper, maids, fixtures);
            EntityMaid maid = maid(helper, maids);
            BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
            var pot = pot(helper, new BlockPos(2, 2, 2));
            ItemStack hub = MkItems.CULINARY_HUB.get().getDefaultInstance();
            maid.getMaidInv().setStackInSlot(4, hub);
            supply(maid, hub, new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.CARROT),
                    new ItemStack(Items.POTATO), new ItemStack(Items.BOWL, 2));
            workAt(maid, pos);
            helper.assertTrue(pot.getStatus() == IStockpot.PUT_SOUP_BASE, "missing lid must not pour soup");
            ItemStack lid = ModItems.STOCKPOT_LID.get().getDefaultInstance();
            lid.setDamageValue(17);
            var storage = CulinaryHubWorkStorage.open(maid).orElseThrow();
            CookInventoryTransactions.insertAll(storage.ingredients(), lid.copy()); storage.sync();
            helper.setBlock(2, 1, 2, Blocks.AIR);
            workAt(maid, pos);
            helper.assertTrue(pot.getStatus() == IStockpot.PUT_SOUP_BASE, "missing heat must leave all supplies intact");
            helper.setBlock(2, 1, 2, Blocks.CAMPFIRE);
            maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            var before = StockpotAdapter.inspect(pot, level).orElseThrow();
            var debugStorage = ((TaskKcStockpot) maid.getTask()).getRecipesManager(maid); debugStorage.checkAndInit();
            var noFoodRecipes = new StockpotTaskData(new RecipeFilterData(RecipeFilterData.Mode.WHITELIST,
                    List.of(), List.of()), false);
            var unusedFood = StockpotRecSerializerManager.INSTANCE;
            helper.assertTrue(!unusedFood.retain(new ItemStack(Items.MUSHROOM_STEW), level, noFoodRecipes) && unusedFood.retain(lid, level, noFoodRecipes),
                    "nonstackable unused meals must not be classified as reusable lids or tools");
            ResourceLocation duplicate = id("test_stockpot_overlap");
            manager.replaceRecipes(List.of(new RecipeHolder<>(NORMAL, ordinary), new RecipeHolder<>(duplicate, ordinary),
                    new RecipeHolder<>(FLEX, flex)));
            helper.assertTrue(plan(level, StockpotTaskData.DEFAULT, stacks(debugStorage.getInputInv()), before, pot) != null, "synthetic overlapping allowed recipes must remain usable");
            var onlyNormal = new StockpotTaskData(new RecipeFilterData(RecipeFilterData.Mode.WHITELIST,
                    List.of(NORMAL), List.of()), false);
            helper.assertTrue(plan(level, onlyNormal, stacks(debugStorage.getInputInv()), before, pot) == null, "synthetic allowed/forbidden ambiguity must not clear raw ingredients");
            manager.replaceRecipes(fixtures);
            workAt(maid, pos);
            helper.assertTrue(pot.hasLid() && pot.getStatus() == IStockpot.PUT_INGREDIENT, "ordinary plan must load and cover");
            helper.assertTrue(pot.getLidItem().getDamageValue() == 17, "cover must preserve actual lid components");
            helper.assertTrue(maid.getMainHandItem().is(Items.IRON_SWORD), "original main hand must be restored");
            storage = CulinaryHubWorkStorage.open(maid).orElseThrow();
            helper.assertTrue(CookInventoryTransactions.count(storage.ingredients(), stack -> stack.is(Items.BUCKET)) == 1,
                    "water base must return one real bucket");
            var oneSlot = new ItemStackHandler(1);
            helper.assertTrue(!CookInventoryTransactions.canFitAll(oneSlot, List.of(lid.copy(), lid.copy())),
                    "two nonstackable lids must require two slots");
            helper.runAfterDelay(30, () -> {
                try {
                    helper.assertTrue(pot.getStatus() == IStockpot.FINISHED,
                            "native covered ticking must finish: " + StockpotAdapter.inspect(pot, level));
                    var live = CulinaryHubWorkStorage.open(maid).orElseThrow();
                    for (int slot = 0; slot < live.ingredients().getSlots(); slot++) live.ingredients().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
                    live.sync();
                    workAt(maid, pos);
                    helper.assertTrue(pot.hasLid() && pot.getTakeoutCount() == 2, "full input must prevent uncovering");
                    supply(maid, hub, new ItemStack(Items.BOWL, 2));
                    live = CulinaryHubWorkStorage.open(maid).orElseThrow();
                    for (int slot = 0; slot < live.outputs().getSlots(); slot++) live.outputs().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
                    live.sync();
                    workAt(maid, pos);
                    helper.assertTrue(pot.hasLid() && pot.getTakeoutCount() == 2, "full output must preserve finished pot");
                    live = CulinaryHubWorkStorage.open(maid).orElseThrow();
                    for (int slot = 0; slot < live.outputs().getSlots(); slot++) live.outputs().setStackInSlot(slot, ItemStack.EMPTY);
                    live.sync();
                    workAt(maid, pos);
                    live = CulinaryHubWorkStorage.open(maid).orElseThrow();
                    helper.assertTrue(pot.getStatus() == IStockpot.PUT_SOUP_BASE && !pot.hasLid(), "last serving must reset native state");
                    helper.assertTrue(CookInventoryTransactions.count(live.outputs(), stack -> stack.is(Items.BEETROOT_SOUP)) == 2,
                            "collect must consume carriers and store both servings exactly once");
                    helper.assertTrue(CookInventoryTransactions.count(live.ingredients(), StockpotAdapter::isLid) == 1,
                            "one lid must return exactly once");
                    helper.assertTrue(CookInventoryTransactions.count(live.ingredients(), stack ->
                            StockpotAdapter.isLid(stack) && stack.getDamageValue() == 17) == 1, "returned lid must keep durability");
                    rawAndStorageCases(helper, maids, maid, hub);
                    EntityMaid reuse = maid(helper, maids);
                    var reusePot = pot(helper, new BlockPos(5, 2, 4));
                    setSettings(reuse, new StockpotTaskData(
                            new RecipeFilterData(RecipeFilterData.Mode.WHITELIST, List.of(RETURNED_CARRIER), List.of()), false));
                    reuse.getMaidInv().setStackInSlot(0, new ItemStack(Items.WATER_BUCKET));
                    reuse.getMaidInv().setStackInSlot(1, new ItemStack(Items.MUSHROOM_STEW));
                    reuse.getMaidInv().setStackInSlot(2, lid.copy());
                    workAt(reuse, reusePot.getBlockPos());
                    helper.assertTrue(reusePot.hasLid() && CookInventoryTransactions.count(reuse.getAvailableBackpackInv(),
                            stack -> stack.is(Items.BOWL)) == 1, "native ingredient return must supply the carrier without an extra empty bowl");
                    navigationCases(helper, maids);
                    supply(maid, hub, new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.APPLE),
                            new ItemStack(Items.WHEAT), new ItemStack(Items.BOWL, 2), lid.copy());
                    setSettings(maid, new StockpotTaskData(
                            new RecipeFilterData(RecipeFilterData.Mode.WHITELIST, List.of(FLEX), List.of()), true));
                    workAt(maid, pos);
                    helper.assertTrue(pot.getStatus() == IStockpot.PUT_SOUP_BASE,
                            "global Flex off must override a legacy per-maid true flag and consume nothing");
                    experimental.set(true);
                    setSettings(maid, new StockpotTaskData(
                            new RecipeFilterData(RecipeFilterData.Mode.WHITELIST, List.of(FLEX), List.of()), false));
                    helper.assertTrue(TaskKcStockpot.settings(maid).allowFlexRecipes()
                                    && TaskKcStockpot.settings(reuse).allowFlexRecipes(),
                            "one global experimental switch must apply to both maids regardless of legacy flags");
                    workAt(maid, pos);
                    helper.assertTrue(pot.hasLid(), "enabled native Flex plan must load and cover");
                    experimental.set(false);
                    helper.runAfterDelay(30, () -> {
                        try {
                            helper.assertTrue(pot.getStatus() == IStockpot.FINISHED, "Flex native ticking must finish");
                            workAt(reuse, reusePot.getBlockPos());
                            helper.assertTrue(reusePot.getStatus() == IStockpot.PUT_SOUP_BASE
                                            && CookInventoryTransactions.count(reuse.getAvailableBackpackInv(), stack -> stack.is(Items.BEETROOT_SOUP)) == 1
                                            && CookInventoryTransactions.count(reuse.getAvailableBackpackInv(), stack -> stack.is(Items.BOWL)) == 0,
                                    "returned carrier must be reused and consumed exactly once");
                            setSettings(maid, StockpotTaskData.DEFAULT);
                            helper.setBlock(6, 1, 6, Blocks.CHEST);
                            var warehouse = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(6, 1, 6)));
                            ItemCulinaryHub.actionModePos(hub, BagType.OUTPUT.name, warehouse.getBlockPos());
                            for (int slot = 0; slot < warehouse.getContainerSize(); slot++) warehouse.setItem(slot, new ItemStack(Items.DIRT, 64));
                            workAt(maid, pos);
                            helper.assertTrue(pot.hasLid() && pot.getStatus() == IStockpot.FINISHED,
                                    "full bound output warehouse must preserve the lid and servings");
                            for (int slot = 0; slot < warehouse.getContainerSize(); slot++) warehouse.setItem(slot, ItemStack.EMPTY);
                            workAt(maid, pos);
                            helper.assertTrue(pot.getStatus() == IStockpot.PUT_SOUP_BASE,
                                    "finished Flex must collect even after disabling the option: " + StockpotAdapter.inspect(pot, level));
                            helper.assertTrue(CookInventoryTransactions.count(ItemCulinaryHub.getBeInv(level, warehouse),
                                    stack -> stack.is(Items.COOKIE)) == 2,
                                    "Flex must preserve native serving count and quality components");
                            helper.succeed();
                        } finally { cleanup.run(); }
                    });
                } catch (RuntimeException | Error error) {
                    MaidsoulKitchen.LOGGER.error("Stockpot lifecycle test failed", error);
                    cleanup.run(); throw error;
                }
            });
        } catch (RuntimeException | Error error) { cleanup.run(); throw error; }
    }

    private static void setSettings(EntityMaid maid, StockpotTaskData settings) {
        com.github.wallev.maidsoulkitchen.entity.data.inner.task.KitchenData.setFilter(maid, com.github.wallev.maidsoulkitchen.task.TaskInfo.KC_STOCKPOT.uid, settings.filter());
    }

    private static void rawAndStorageCases(GameTestHelper h, List<EntityMaid> maids, EntityMaid maid, ItemStack hub) {
        var level = h.getLevel();
        var raw = pot(h, new BlockPos(4, 2, 2));
        EntityMaid worker = maid(h, maids);
        setSettings(worker, new StockpotTaskData(
                new RecipeFilterData(RecipeFilterData.Mode.WHITELIST, List.of(), List.of()), false));
        h.assertTrue(StockpotAdapter.soupBaseFor(new ItemStack(Items.WATER_BUCKET)).orElseThrow().equals(ModSoupBases.WATER),
                "soup IDs must map through native item predicates");
        ItemStack wrong = new ItemStack(Items.STONE);
        h.assertTrue(!StockpotAdapter.addSoupBase(raw, level, worker, wrong) && wrong.getCount() == 1, "wrong soup must not consume input");
        StockpotAdapter.addSoupBase(raw, level, worker, new ItemStack(Items.WATER_BUCKET));
        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        StockpotAdapter.addIngredient(raw, level, worker, new ItemStack(Items.MUSHROOM_STEW));
        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        workAt(worker, raw.getBlockPos());
        h.assertTrue(!raw.isEmpty(), "raw container shortage must preserve input");
        worker.getMaidInv().setStackInSlot(0, new ItemStack(Items.BOWL));
        workAt(worker, raw.getBlockPos());
        h.assertTrue(raw.isEmpty() && raw.getStatus() == IStockpot.PUT_INGREDIENT && raw.getSoupBaseId().equals(ModSoupBases.WATER),
                "unsatisfiable raw ingredients must return while retaining soup");
        h.assertTrue(CookInventoryTransactions.count(worker.getAvailableBackpackInv(), stack -> stack.is(Items.MUSHROOM_STEW)) == 1,
                "raw removal must consume one empty container and return one actual meal");
        var hot = pot(h, new BlockPos(4, 2, 4));
        StockpotAdapter.addSoupBase(hot, level, worker, new ItemStack(Items.LAVA_BUCKET));
        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        StockpotAdapter.addIngredient(hot, level, worker, new ItemStack(Items.CARROT));
        workAt(worker, hot.getBlockPos());
        h.assertTrue(!hot.isEmpty(), "hot raw retrieval without protection must wait");
        ItemStack protection = MkItems.BURN_PROTECT_BAUBLE.get().getDefaultInstance();
        worker.getMaidBauble().setStackInSlot(0, protection);
        float health = worker.getHealth();
        workAt(worker, hot.getBlockPos());
        h.assertTrue(hot.isEmpty() && worker.getHealth() == health && protection.getDamageValue() == 1,
                "existing native damage events must protect maid and charge exactly one durability");
        worker.removeAllEffects();
        worker.invulnerableTime = 0; // independent protection case, outside the preceding hit's grace period
        ItemStack fireProtection = com.github.tartaricacid.touhoulittlemaid.init.InitItems.FIRE_PROTECT_BAUBLE.get().getDefaultInstance();
        worker.getMaidBauble().setStackInSlot(0, fireProtection);
        StockpotAdapter.addIngredient(hot, level, worker, new ItemStack(Items.CARROT));
        workAt(worker, hot.getBlockPos());
        h.assertTrue(hot.isEmpty() && worker.getHealth() == health && fireProtection.getDamageValue() == 1,
                "TLM fire protection alone must allow native raw retrieval: empty=" + hot.isEmpty()
                        + ", health=" + worker.getHealth() + ", durability=" + fireProtection.getDamageValue());
        h.setBlock(1, 1, 4, Blocks.CHEST); h.setBlock(1, 1, 6, Blocks.CHEST);
        var wrongChest = (ChestBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(1, 1, 4)));
        var sameChest = (ChestBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(1, 1, 6)));
        wrongChest.setItem(0, new ItemStack(Items.DIRT)); sameChest.setItem(0, new ItemStack(Items.CARROT));
        ItemCulinaryHub.actionModePos(hub, BagType.INGREDIENT.name, wrongChest.getBlockPos());
        ItemCulinaryHub.actionModePos(hub, BagType.INGREDIENT.name, sameChest.getBlockPos());
        supply(maid, hub, new ItemStack(Items.CARROT, 3), ModItems.STOCKPOT_LID.get().getDefaultInstance());
        var storage = CulinaryHubWorkStorage.open(maid).orElseThrow();
        storage.storeUnusedInputs(StockpotAdapter::isLid); storage.sync();
        h.assertTrue(wrongChest.getItem(1).isEmpty() && sameChest.getItem(0).getCount() == 4,
                "unused raw ingredients must deposit only in a chest already storing that material");
        h.assertTrue(CookInventoryTransactions.count(storage.ingredients(), StockpotAdapter::isLid) == 1,
                "reusable lids must stay in unified input");
        if (com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.FD_COOK_POT.canLoad()) {
            sameChest.setItem(0, new ItemStack(Items.CARROT));
            supply(maid, hub, new ItemStack(Items.CARROT, 3), ModItems.STOCKPOT_LID.get().getDefaultInstance());
            var fdTask = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.TaskFdCookingPot();
            maid.setTask(fdTask);
            var fdInventory = new com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<>(fdTask.getRecSerializerManager(), maid, fdTask);
            fdInventory.checkAndCreateRecipesIngredients();
            h.assertTrue(wrongChest.getItem(1).isEmpty() && sameChest.getItem(0).getCount() == 4,
                    "FD common manager must return unused input to a same-material chest");
            maid.setTask(new TaskKcStockpot());
            storage = CulinaryHubWorkStorage.open(maid).orElseThrow();
            h.assertTrue(CookInventoryTransactions.count(storage.ingredients(), StockpotAdapter::isLid) == 1,
                    "FD replanning must retain reusable supplies");
        }
        for (int slot = 0; slot < storage.ingredients().getSlots(); slot++) storage.ingredients().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        storage.sync();
        h.assertTrue(!storage.prepareIngredientCount(stack -> stack.is(Items.CARROT), 5) && sameChest.getItem(0).getCount() == 4,
                "full input must not consume bound supplies");
        storage.ingredients().setStackInSlot(0, ItemStack.EMPTY); storage.sync();
        h.assertTrue(!storage.prepareIngredientCount(stack -> stack.is(Items.CARROT), 5)
                        && CookInventoryTransactions.count(storage.ingredients(), stack -> stack.is(Items.CARROT)) == 4
                        && sameChest.getItem(0).isEmpty(),
                "insufficient batch must preserve exact total quantity after a partial transfer");
        maid.getMaidInv().setStackInSlot(4, ItemStack.EMPTY); maid.getMaidBauble().setStackInSlot(0, hub);
        h.assertTrue(CulinaryHubWorkStorage.open(maid).isPresent(), "hub must also work from the registered bauble slot");
        maid.getMaidBauble().setStackInSlot(0, ItemStack.EMPTY); maid.getMaidInv().setStackInSlot(4, hub);
    }

    private static void quantityCases(GameTestHelper h, List<EntityMaid> maids, List<RecipeHolder<?>> fixtures) {
        var level = h.getLevel();
        var manager = level.getRecipeManager();
        List<RecipeHolder<?>> batches = new ArrayList<>();
        for (int count : List.of(1, 3, 9)) {
            List<Ingredient> input = new ArrayList<>();
            for (int slot = 0; slot < count; slot++) input.add(Ingredient.of(Items.CARROT));
            batches.add(new RecipeHolder<>(id("batch_" + count), new StockpotRecipe(input, ModSoupBases.WATER,
                    new ItemStack(Items.COOKED_BEEF, count), 2, Ingredient.of(Items.BOWL), StockpotVisuals.DEFAULT)));
        }
        batches.add(new RecipeHolder<>(id("different_material"), new StockpotRecipe(ingredients(Items.POTATO),
                ModSoupBases.WATER, new ItemStack(Items.COOKED_BEEF), 2, Ingredient.of(Items.BOWL), StockpotVisuals.DEFAULT)));
        manager.replaceRecipes(batches);
        EntityMaid worker = maid(h, maids);
        var pot = pot(h, new BlockPos(6, 2, 3));
        try {
            var options = StockpotAdapter.getRecipeOptions(level, false);
            h.assertTrue(options.size() == 2, "same output with different raw materials must remain separate");
            var option = options.stream().filter(entry -> entry.recipeIds().contains(id("batch_1"))).findFirst().orElseThrow();
            h.assertTrue(option.recipeIds().size() == 3 && option.result().getCount() == 9,
                    "quantity menu must have one representative for all IDs, showing the maximum batch");
            var legacyFilter = new RecipeFilterData(RecipeFilterData.Mode.WHITELIST, List.of(id("batch_1")), List.of());
            h.assertTrue(option.allowed(legacyFilter), "old selection of a small recipe must select the entire quantity group");
            var unselected = option.toggle(legacyFilter);
            h.assertTrue(!option.selected(unselected), "toggling a partial legacy selection off must clear the whole group");
            h.assertTrue(option.toggle(unselected).whitelist().containsAll(option.recipeIds()),
                    "toggling on must save every native quantity ID");
            var blacklisted = new RecipeFilterData(RecipeFilterData.Mode.BLACKLIST, List.of(), List.of(id("batch_3")));
            h.assertTrue(!option.allowed(blacklisted), "one legacy blacklisted variant must exclude the quantity family");
            StockpotAdapter.addSoupBase(pot, level, worker, new ItemStack(Items.WATER_BUCKET));
            worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            var snapshot = StockpotAdapter.inspect(pot, level).orElseThrow();
            var settings = new StockpotTaskData(legacyFilter, false);
            ItemStack lid = ModItems.STOCKPOT_LID.get().getDefaultInstance();
            var maximum = plan(level, settings,
                    List.of(lid, new ItemStack(Items.CARROT, 9), new ItemStack(Items.BOWL, 9)), snapshot, pot);
            h.assertTrue(maximum != null
                            && maximum.recipeId().equals(id("batch_9")) && maximum.maidItems().stream().filter(item -> item.role() == com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem.Role.INGREDIENT).mapToInt(item -> item.count()).sum() == 9,
                    "sufficient ingredients must prefer the largest batch");
            var smaller = plan(level, settings,
                    List.of(lid, new ItemStack(Items.CARROT, 4), new ItemStack(Items.BOWL, 9)), snapshot, pot);
            h.assertTrue(smaller != null
                            && smaller.recipeId().equals(id("batch_3")),
                    "material shortage must choose the largest available smaller batch");
            var missingCarriers = plan(level, settings,
                    List.of(lid, new ItemStack(Items.CARROT, 9), new ItemStack(Items.BOWL, 1)), snapshot, pot);
            h.assertTrue(missingCarriers == null,
                    "carrier shortage must not silently lower a fully supplied ingredient batch");
            h.setBlock(6, 1, 1, Blocks.CHEST);
            var source = (ChestBlockEntity) level.getBlockEntity(h.absolutePos(new BlockPos(6, 1, 1)));
            ItemStack hub = MkItems.CULINARY_HUB.get().getDefaultInstance();
            worker.getMaidInv().setStackInSlot(4, hub);
            ItemCulinaryHub.actionModePos(hub, BagType.INGREDIENT.name, source.getBlockPos());
            source.setItem(0, new ItemStack(Items.CARROT, 9)); source.setItem(1, new ItemStack(Items.BOWL, 9));
            setSettings(worker, settings);
            if (com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.FD_COOK_POT.canLoad()) {
                var stove = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(
                        ResourceLocation.parse("farmersdelight:stove")).orElseThrow();
                h.setBlock(new BlockPos(6, 1, 3), stove.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));
                h.assertTrue(!StockpotAdapter.inspect(pot, level).orElseThrow().heated(),
                        "unlit FD stove must fail KC native heat check");
                h.setBlock(new BlockPos(6, 1, 3), stove.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
                h.assertTrue(StockpotAdapter.inspect(pot, level).orElseThrow().heated(),
                        "lit FD stove must pass KC native heat check");
            }
            var move = new TestStockpotMove(worker);
            move.start(level, worker, level.getGameTime());
            h.assertTrue(CookTargetMemory.getWorkPos(worker).isEmpty(),
                    "water and bound raw materials without a lid must not claim work");
            source.setItem(2, lid.copy());
            move.start(level, worker, level.getGameTime());
            h.assertTrue(CookTargetMemory.getWorkPos(worker).orElseThrow().currentBlockPosition().equals(pot.getBlockPos()),
                    "a lid in the same bound input chest must make the water-filled pot selectable");
            workAt(worker, pot.getBlockPos());
            h.assertTrue(pot.hasLid() && pot.getInputs().stream().filter(stack -> !stack.isEmpty()).count() == 9
                            && source.getItem(0).isEmpty() && source.getItem(2).isEmpty(),
                    "real stockpot task must take nine ingredients and one lid from bound input, then cover");
        } finally {
            CookWorkLocks.release(level, pot.getBlockPos(), worker); CookTargetMemory.clear(worker);
            level.destroyBlock(pot.getBlockPos(), false); h.setBlock(6, 1, 3, Blocks.AIR); h.setBlock(6, 1, 1, Blocks.AIR);
            worker.discard(); manager.replaceRecipes(fixtures);
        }
    }

    private static void navigationCases(GameTestHelper h, List<EntityMaid> maids) {
        var level = h.getLevel(); EntityMaid maid = maid(h, maids); EntityMaid other = maid(h, maids);
        h.setBlock(2, 1, 1, Blocks.STONE); h.setBlock(2, 2, 1, Blocks.STONE);
        // Unified planning reserves real resources per device. Supply two complete physical units
        // before testing rotation between two eligible devices (two lids occupy separate slots).
        ItemStack[] resources = {ItemStack.EMPTY, new ItemStack(Items.CARROT, 2),
                new ItemStack(Items.POTATO, 2), new ItemStack(Items.BOWL, 4), ModItems.STOCKPOT_LID.get().getDefaultInstance(),
                ModItems.STOCKPOT_LID.get().getDefaultInstance()};
        for (int slot = 0; slot < resources.length; slot++) maid.getMaidInv().setStackInSlot(slot, resources[slot]);
        // Both nearby water-filled half pots need no bucket; one backpack slot stays free for output.
        var secondReady = pot(h, new BlockPos(6, 2, 5));
        StockpotAdapter.addSoupBase(secondReady, level, maid, new ItemStack(Items.WATER_BUCKET));
        maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var move = new TestStockpotMove(maid);
        move.start(level, maid, level.getGameTime());
        BlockPos first = CookTargetMemory.getWorkPos(maid).orElseThrow().currentBlockPosition();
        BlockPos walk = maid.getBrain().getMemory(com.github.wallev.maidsoulkitchen.init.MkMemories.COOK_WALK_POS.get())
                .orElseThrow().currentBlockPosition();
        h.assertTrue(!first.equals(walk), "work coordinate must be the device and walk coordinate its reachable side");
        h.assertTrue(StockpotAdapter.supports(level.getBlockEntity(first)), "actual move task must select a stockpot");
        h.assertTrue(!CookWorkLocks.tryClaim(level, first, other), "two maids must not claim the same device");
        CookWorkLocks.release(level, first, maid); CookTargetMemory.clear(maid);
        move.start(level, maid, level.getGameTime());
        BlockPos second = CookTargetMemory.getWorkPos(maid).orElseThrow().currentBlockPosition();
        h.assertTrue(!first.equals(second), "actual move task must rotate multiple stockpots");
        CookWorkLocks.release(level, second, maid); CookTargetMemory.clear(maid);
        BlockPos destroyed = h.absolutePos(new BlockPos(4, 2, 4));
        CookTargetMemory.remember(maid, destroyed.below().north(), destroyed, 0.6F, 0);
        level.destroyBlock(destroyed, false);
        h.assertTrue(!CookTargetMemory.hasValidWorkTarget(level, maid, StockpotAdapter::supports),
                "destroyed device must invalidate the assignment");
        CookWorkLocks.release(level, destroyed, maid); CookTargetMemory.clear(maid);
    }

    private static List<ItemStack> stacks(net.neoforged.neoforge.items.IItemHandler inventory) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot=0; slot<inventory.getSlots(); slot++) if (!inventory.getStackInSlot(slot).isEmpty()) result.add(inventory.getStackInSlot(slot).copy());
        return result;
    }
    private static com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec plan(net.minecraft.world.level.Level level,
            StockpotTaskData settings, List<ItemStack> available, StockpotAdapter.Snapshot snapshot, StockpotBlockEntity pot) {
        var rsm = StockpotRecSerializerManager.INSTANCE;
        for (var descriptor : rsm.forDevice(pot, level, settings)) {
            var work = rsm.createWork(snapshot, level, settings, available, descriptor.candidates, pot,
                    com.github.wallev.maidsoulkitchen.task.TaskInfo.KC_STOCKPOT.uid, 0);
            if (work != null) return work;
        }
        return null;
    }
    private static void finishPlanning(com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<?> cm) {
        for (int retry=0; retry<12 && cm.getRunState()==0; retry++) cm.checkAndCreateRecipesIngredients();
        for (int ticks=0; ticks<600 && cm.getRunState()>0; ticks++) {
            if (cm.getRunState()==1) { cm.getChestInputInventory().tickScan(); if (cm.getChestInputInventory().done()) cm.startGenerateRecs(); }
            else if (cm.getRunState()==2) { if (!cm.recsGenerateDone()) cm.tickGenerateRecs(); if (cm.recsGenerateDone()) cm.recsGenDoneAndUpdate(); }
        }
        if (cm.getRunState()!=0) throw new IllegalStateException("bounded stockpot planner did not finish");
    }
    /** Test driver invokes the production shared manager/Be/Rule, never the deleted KC workAt. */
    private static void workAt(EntityMaid maid, BlockPos pos) {
        // This fixture explicitly requests one pot among several independent cases. Restrict the
        // source condition scan to that column; production Move remains free to choose its work.
        var center = maid.getRestrictCenter(); var radius = (int) maid.getRestrictRadius(); var location = maid.position();
        maid.restrictTo(pos, 1); maid.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 0.5);
        var task = (TaskKcStockpot) maid.getTask(); var cm = task.getRecipesManager(maid);
        try { cm.checkAndInit(); finishPlanning(cm); }
        finally { maid.restrictTo(center, radius); maid.moveTo(location.x, location.y, location.z); }
        if (!(maid.level().getBlockEntity(pos) instanceof StockpotBlockEntity nativeBe)) return;
        var be = new StockpotBe(maid); be.setBe(nativeBe);
        StockpotCookRule.INSTANCE.cookMake(be, cm); cm.syncInv(); cm.itemOutput2Chest();
    }
    private static final class TestStockpotMove extends com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookMoveTask<StockpotBlockEntity,
            net.minecraft.world.item.crafting.Recipe<StockpotInput>> {
        TestStockpotMove(EntityMaid maid) { this((TaskKcStockpot) maid.getTask(), maid); }
        private TestStockpotMove(TaskKcStockpot task, EntityMaid maid) {
            super(task, task.getRecipesManager(maid), StockpotCookRule.INSTANCE, new StockpotBe(maid));
        }
        @Override public void start(net.minecraft.server.level.ServerLevel level, EntityMaid maid, long time) {
            getMaidCookManager().clear(); finishPlanning(getMaidCookManager()); super.start(level, maid, time);
        }
    }

    private static NonNullList<Ingredient> ingredients(net.minecraft.world.level.ItemLike... items) {
        NonNullList<Ingredient> result = NonNullList.create();
        for (var item : items) result.add(Ingredient.of(item));
        while (result.size() < 9) result.add(Ingredient.EMPTY);
        return result;
    }

    private static void forceCompatibilityClasses() {
        try (var stream = MaidsoulKitchen.class.getResourceAsStream("/mod_task_clazz.json")) {
            if (stream == null) throw new IllegalStateException("Missing compatibility manifest");
            var entries = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("clazzInfoMap");
            int count = 0;
            for (var task : com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.VALUES) {
                if (task == com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.NONE || !task.canLoad()) continue;
                for (var name : entries.getAsJsonObject(task.getUidStr()).getAsJsonObject("clazzInfo").getAsJsonArray("classes")) {
                    Class<?> type = Class.forName(name.getAsString(), false, StockpotGameTests.class.getClassLoader());
                    type.getDeclaredMethods(); type.getDeclaredFields(); count++;
                }
            }
            MaidsoulKitchen.LOGGER.info("Forced compatibility class linkage passed: {} API class entries", count);
        } catch (java.io.IOException | ClassNotFoundException error) {
            throw new IllegalStateException("Compatibility class linkage failed", error);
        }
    }
    private static EntityMaid maid(GameTestHelper helper, List<EntityMaid> maids) {
        EntityMaid maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new BlockPos(1, 1, 1));
        maid.setNoAi(true); // removeFreeWill clears goals; TLM tasks live in Brain and need an explicit pause.
        maid.getSchedulePos().setHomeModeEnable(maid, helper.absolutePos(new BlockPos(3, 1, 3)));
        maid.getSchedulePos().setConfigured(true);
        maid.setHomeModeEnable(true);
        maid.restrictTo(helper.absolutePos(new BlockPos(3, 1, 3)), 8);
        maid.setOnGround(true);
        maid.setTask(new TaskKcStockpot()); maids.add(maid); return maid;
    }
    private static StockpotBlockEntity pot(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.CAMPFIRE); helper.setBlock(pos, ModBlocks.STOCKPOT.get());
        return (StockpotBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }
    private static void supply(EntityMaid maid, ItemStack hub, ItemStack... items) {
        var containers = ItemCulinaryHub.getContainers(maid.registryAccess(), hub);
        var input = com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidCookBagInventory.logicalInput(containers);
        for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
        for (int slot = 0; slot < items.length; slot++) input.setStackInSlot(slot, items[slot]);
        ItemCulinaryHub.setContainer(maid.registryAccess(), hub, containers);
    }
}
