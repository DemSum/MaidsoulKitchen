package com.github.wallev.maidsoulkitchen.task.cook.common.task;

import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.CookData;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.KitchenData;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidItem;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.RecSerializerManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.mkrec.MKRecipe;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.ingredient.RecIngredient;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native registry tests: ordinary JUnit cannot initialize Minecraft recipes/components or TLM data keys. */
@PrefixGameTestTemplate(false)
public final class CookingArchitectureGameTests {
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, name); }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void nativeRecipeDescriptors(GameTestHelper helper) {
        var level = helper.getLevel();
        if (com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.FD_COOK_POT.canLoad()) {
            var recipes = com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.CookingPotRecSerializerManager.getInstance().getRecipes(level);
            helper.assertTrue(!recipes.isEmpty(), "FD native pot recipe descriptors must load");
            for (var description : recipes) helper.assertTrue(description.holder().value() == description.rec()
                    && ItemStack.matches(description.container(), description.rec().getOutputContainer()),
                    "FD descriptor must preserve native Holder identity and output-container requirements");
        }
        if (com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.FD_CUTTING_BOARD.canLoad()) {
            var recipes = com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cuttingboard.CuttingBoardRecSerializerManager.getInstance().getRecipes(level);
            helper.assertTrue(!recipes.isEmpty() && recipes.stream().allMatch(description -> !description.tool().isEmpty()),
                    "FD cutting descriptors must retain native tools separately from consumed ingredients");
        }
        if (com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.DB_BEER.canLoad()) {
            var recipes = com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel.BeerBarrelRecSerializerManager.getInstance().getRecipes(level);
            helper.assertTrue(!recipes.isEmpty(), "DrinkBeer native recipe descriptors must load");
            for (var description : recipes) {
                ItemStack cups = description.rec().getBeerCup();
                helper.assertTrue(description.inItems().size() == description.rec().getIngredients().size() + 1
                        && description.inItems().getLast().test(cups.copyWithCount(64)) == cups.getCount(),
                        "DrinkBeer native cup count must be reserved as the final material slot");
            }
        }
        if (com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.YHC_FERMENTATION_TANK.canLoad()) {
            var recipes = com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationRecSerializerManager.getInstance().getRecipes(level);
            helper.assertTrue(!recipes.isEmpty(), "YHC native fluid-ingredient descriptors must load");
            for (var description : recipes) helper.assertTrue(level.getRecipeManager().byKey(description.id()).orElseThrow().value() == description.rec(),
                    "YHC native registry scan must retain the original Holder rather than a second recipe map");
        }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void reusableToolsAndFluidAccounting(GameTestHelper helper) {
        var holder = new RecipeHolder<>(id("fluid_tool_plan"), new SmokingRecipe("", CookingBookCategory.MISC,
                Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 20));
        var toolConverter = new com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.ToolRecSerializerManager<SmokingRecipe>(RecipeType.SMOKING) {
            @Override protected ToolRecipeInfoProvider<SmokingRecipe> createRecipeInfoProvider() {
                return new ToolRecipeInfoProvider<>() {
                    @Override public RecIngredient getTool(RecSerializerManager<SmokingRecipe> rsm, SmokingRecipe recipe) {
                        return RecIngredient.of(Ingredient.of(Items.IRON_HOE));
                    }
                };
            }
        };
        ItemStack namedTool = new ItemStack(Items.IRON_HOE);
        namedTool.set(DataComponents.CUSTOM_NAME, Component.literal("reusable tool"));
        var tool = ItemDefinition.of(namedTool);
        var carrot = ItemDefinition.of(Items.CARROT);
        var toolDescription = new MKRecipe<>(holder, false, RecIngredient.of(Ingredient.of(Items.IRON_HOE)),
                List.of(RecIngredient.of(Ingredient.of(Items.CARROT))), new ItemStack(Items.BAKED_POTATO));
        var toolsAvailable = new HashMap<ItemDefinition, Long>(); toolsAvailable.put(tool, 1L); toolsAvailable.put(carrot, 3L);
        var tools = toolConverter.createMaidRecs(List.of(toolDescription), toolsAvailable,
                (r, range) -> { }, r -> true, reservation -> {
                    helper.assertTrue(reservation.getItemUse().get(tool).isTool(), "hub reservation must include the reusable tool");
                    return true;
                }, done -> { }, TaskInfo.FD_CUTTING_BOARD.uid, 1);
        helper.assertTrue(tools.size() == 1 && tools.getFirst().amount() == 3 && toolsAvailable.get(tool) == 1L
                && toolsAvailable.get(carrot) == 0L && tools.getFirst().maidItems().stream()
                .anyMatch(material -> material.role() == MaidItem.Role.TOOL && material.item().equals(tool) && material.count() == 1),
                "one component-bearing reusable tool must support all ingredient batches without being consumed by planning");
        var fluidConverter = new com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.FluidRecSerializerManager<SmokingRecipe>(RecipeType.SMOKING) {
            @Override protected FluidRecipeInfoProvider<SmokingRecipe> createRecipeInfoProvider() {
                return new FluidRecipeInfoProvider<>() {
                    @Override public net.minecraft.world.level.material.Fluid getOutputFluid(RecSerializerManager<SmokingRecipe> rsm, SmokingRecipe recipe) {
                        return net.minecraft.world.level.material.Fluids.EMPTY;
                    }
                };
            }
            @Override protected void initFluidRecs(net.minecraft.world.level.Level level, List<RecipeHolder<SmokingRecipe>> holders) {
                this.recipes = holders.stream().map(this::createMKRecipe).toList();
            }
        };
        ItemStack namedBucket = new ItemStack(Items.WATER_BUCKET);
        namedBucket.set(DataComponents.CUSTOM_NAME, Component.literal("fluid component"));
        var bucket = ItemDefinition.of(namedBucket);
        var available = new HashMap<ItemDefinition, Long>(); available.put(bucket, 2L); available.put(carrot, 8L);
        var fluidDescription = new MKRecipe<>(holder, true, List.of(new ItemStack(Items.WATER_BUCKET)),
                List.of(RecIngredient.of(Ingredient.of(Items.CARROT))), new ItemStack(Items.BAKED_POTATO));
        var fluids = fluidConverter.createMaidRecs(List.of(fluidDescription), available, (r, range) -> { }, r -> true,
                reservation -> true, done -> { }, TaskInfo.YHC_FERMENTATION_TANK.uid, 1);
        helper.assertTrue(fluids.size() == 2 && available.get(bucket) == 0L && available.get(carrot) == 6L
                && fluids.stream().allMatch(plan -> plan.amount() == 1 && plan.maidItems().getFirst().role() == MaidItem.Role.FLUID
                && plan.maidItems().getFirst().item().equals(bucket)), "each repeated fluid work unit must reserve its full inputs once");
        var bottle = ItemDefinition.of(Items.GLASS_BOTTLE);
        available.clear(); available.put(bottle, 6L); available.put(carrot, 9L);
        var batched = new MKRecipe<>(holder, false, List.of(new ItemStack(Items.GLASS_BOTTLE, 2)),
                List.of(RecIngredient.ofCount(new ItemStack(Items.CARROT, 3))), new ItemStack(Items.BAKED_POTATO));
        var batches = fluidConverter.createMaidRecs(List.of(batched), available, (r, range) -> { }, r -> true,
                reservation -> true, done -> { }, TaskInfo.YHC_FERMENTATION_TANK.uid, 1);
        helper.assertTrue(batches.size() == 1 && batches.getFirst().amount() == 3 && available.get(bottle) == 0L
                && available.get(carrot) == 0L, "a batched fluid plan must not also be repeated and overspend its ingredients");
        available.clear(); available.put(carrot, 3L);
        var noFluid = new MKRecipe<>(holder, true, List.of(RecIngredient.of(Ingredient.of(Items.CARROT))), new ItemStack(Items.BAKED_POTATO));
        helper.assertTrue(fluidConverter.createMaidRecs(List.of(noFluid), available, (r, range) -> { }, r -> true,
                reservation -> true, done -> { }, TaskInfo.YHC_FERMENTATION_TANK.uid, 1).size() == 3 && available.get(carrot) == 0L,
                "no-fluid recipes must use ordinary ingredient conversion rather than being rejected");
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void incrementalScanAndFilteredGeneration(GameTestHelper helper) {
        var handler = new ItemStackHandler(23);
        for (int i = 0; i < 23; i++) handler.setStackInSlot(i, new ItemStack(Items.CARROT, 1));
        var scanner = new com.github.wallev.maidsoulkitchen.task.cook.common.inv.chest.ChestInventory();
        var data = new com.github.wallev.maidsoulkitchen.task.cook.common.inv.chest.ChestInvsData(
                List.of(), List.of(), List.of(handler), 23);
        scanner.init(data); scanner.tickScan();
        helper.assertTrue(!scanner.done() && scanner.getAvailable().get(ItemDefinition.of(Items.CARROT)) == 10,
                "one chest scan tick must inspect at most ten slots");
        scanner.tickScan(); scanner.tickScan();
        helper.assertTrue(scanner.done() && scanner.getAvailable().get(ItemDefinition.of(Items.CARROT)) == 23,
                "incremental scanning must finish without skipping the final partial page");
        scanner.init(data); scanner.tickScan();
        helper.assertTrue(scanner.getAvailable().get(ItemDefinition.of(Items.CARROT)) == 10,
                "a fresh scan must reset both handler cursors and accumulated counts");
        scanner.init(new com.github.wallev.maidsoulkitchen.task.cook.common.inv.chest.ChestInvsData(
                List.of(), List.of(), List.of(), 0));
        helper.assertTrue(scanner.done() && scanner.getAvailable().isEmpty(), "removed input devices must leave no cached material");
        var holder = new RecipeHolder<>(id("scan_page"), new SmokingRecipe("", CookingBookCategory.MISC,
                Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 20));
        var description = new MKRecipe<>(holder, false, List.of(RecIngredient.of(Ingredient.of(Items.CARROT))),
                new ItemStack(Items.BAKED_POTATO));
        var generator = new com.github.wallev.maidsoulkitchen.task.cook.common.manager.RecsGenerate<SmokingRecipe>();
        generator.setRecs(java.util.Collections.nCopies(23, description));
        generator.setCurrentRecs(java.util.Collections.nCopies(3, description));
        helper.assertTrue(generator.tickRun().size() == 3 && generator.done() && generator.tickRun().isEmpty(),
                "filtered recipe pagination must use the current list's size and be safe after completion");
        generator.setCurrentRecs(java.util.Collections.nCopies(23, description));
        helper.assertTrue(generator.tickRun().size() == 10 && !generator.done()
                && generator.tickRun().size() == 10 && generator.tickRun().size() == 3 && generator.done(),
                "recipe generation must keep the upstream ten-recipe tick budget");
        generator.clear();
        helper.assertTrue(generator.done() && generator.getCurrentRecs().isEmpty() && generator.getRecs().size() == 23,
                "clear must discard the generation page while retaining the recipe catalog");
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void inventoryViewsPreserveComponentsAndTransfers(GameTestHelper helper) {
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        maid.setNoAi(true);
        ItemStack hub = com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance();
        maid.getMaidBauble().setStackInSlot(0, hub);
        var view = new com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidCookBagInventory(maid, hub);
        ItemStack named = new ItemStack(Items.CARROT, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("view component"));
        view.getInputInv().setStackInSlot(0, named.copy());
        view.getInputInv().setStackInSlot(1, new ItemStack(Items.CARROT, 7));
        view.refreshInv();
        helper.assertTrue(view.getItemInventory().getItemCount(named) == 3
                && view.getItemInventory().getItemCount(new ItemStack(Items.CARROT)) == 7,
                "component variants must remain distinct in the upstream inventory view");
        view.getInputInv().extractItem(0, 1, false);
        view.refreshInv();
        helper.assertTrue(view.getItemInventory().getItemCount(named) == 2,
                "refresh must preserve unsynchronized transfers instead of reloading stale hub components");
        view.syncInv();
        var reloaded = new com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidCookBagInventory(maid, hub);
        reloaded.refreshInv();
        helper.assertTrue(reloaded.getItemInventory().getItemCount(named) == 2
                && reloaded.getInputInv().getSlots() == java.util.Arrays.stream(
                com.github.wallev.maidsoulkitchen.inventory.container.item.BagType.INPUT_VALS).mapToInt(type -> type.size * 9).sum(),
                "component sync and all four logical input sections must survive reopening");
        var editedContainers = com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub.getContainers(maid.registryAccess(), hub);
        var editedInput = com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid.MaidCookBagInventory.logicalInput(editedContainers);
        editedInput.setStackInSlot(0, named.copyWithCount(5));
        com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub.setContainer(maid.registryAccess(), hub, editedContainers);
        reloaded.refreshInv();
        helper.assertTrue(reloaded.getItemInventory().getItemCount(named) == 5,
                "external component inventory edits must replace the derived view on the next refresh");
        var counts = new com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemInventory();
        ItemStack live = named.copy(); counts.add(live); live.shrink(2); counts.markDirty(); counts.update();
        helper.assertTrue(counts.getItemCount(named) == 1 && counts.getItemCount(Items.DIRT) == 0,
                "dirty inventory counts must follow shrunk references and absent items must count as zero");
        live.setCount(0); counts.markDirty(); counts.update();
        helper.assertTrue(counts.getItemCount(named) == 0 && counts.getItemStacksWithNbt(named).isEmpty(),
                "empty references must leave no stale ingredient counts");
        maid.discard();
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void refusedAndPartialHandlers(GameTestHelper helper) {
        var refusedSource = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return simulate ? super.extractItem(slot, amount, true) : ItemStack.EMPTY;
            }
        };
        refusedSource.setStackInSlot(0, new ItemStack(Items.CARROT, 5));
        var destination = new ItemStackHandler(1);
        var refused = CookInventoryTransactions.transfer(refusedSource, 0, destination, 5, stack -> stack.is(Items.CARROT));
        helper.assertTrue(refused.inserted() == 0 && destination.getStackInSlot(0).isEmpty()
                && refusedSource.getStackInSlot(0).getCount() == 5, "refused real extraction must never mint destination items");
        var source = new ItemStackHandler(1); source.setStackInSlot(0, new ItemStack(Items.CARROT, 5));
        var partial = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate) return super.insertItem(slot, stack, true);
                int accepted = Math.min(2, stack.getCount());
                ItemStack left = super.insertItem(slot, stack.copyWithCount(accepted), false);
                return stack.copyWithCount(stack.getCount() - accepted + left.getCount());
            }
        };
        var result = CookInventoryTransactions.transfer(source, 0, partial, 5, stack -> stack.is(Items.CARROT));
        helper.assertTrue(result.extracted() == 5 && result.inserted() == 2 && result.remainder().isEmpty()
                && source.getStackInSlot(0).getCount() == 3 && partial.getStackInSlot(0).getCount() == 2,
                "partial real insertion must restore the remainder and report only destination acceptance");
        var rejectReturn = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack.copy(); }
        };
        rejectReturn.setStackInSlot(0, new ItemStack(Items.CARROT, 5));
        var rejectActual = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return simulate ? super.insertItem(slot, stack, true) : stack.copy();
            }
        };
        var recovery = CookInventoryTransactions.transfer(rejectReturn, 0, rejectActual, 5, stack -> stack.is(Items.CARROT));
        helper.assertTrue(recovery.inserted() == 0 && recovery.remainder().getCount() == 5
                && rejectReturn.getStackInSlot(0).isEmpty() && rejectActual.getStackInSlot(0).isEmpty(),
                "a rejected rollback must return physical items to the manager instead of discarding them");
        recovery.remainder().setCount(0);
        helper.assertTrue(recovery.remainder().getCount() == 5, "receipt must not alias caller-owned mutable stacks");
        destination.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        var full = CookInventoryTransactions.transfer(source, 0, destination, 3, stack -> stack.is(Items.CARROT));
        helper.assertTrue(full.extracted() == 0 && source.getStackInSlot(0).getCount() == 3,
                "full destination must preserve source material before extraction");
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void conversionAndRejectedReservation(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.CARROT, 8);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("recipe component"));
        var definition = ItemDefinition.of(named);
        var plain = ItemDefinition.of(Items.CARROT);
        var ingredient = RecIngredient.of(DataComponentIngredient.of(true, named));
        var holder = new RecipeHolder<>(id("component_plan"), new SmokingRecipe("", CookingBookCategory.MISC,
                ingredient.ingredient, new ItemStack(Items.BAKED_POTATO), 0, 20));
        var description = new MKRecipe<>(holder, false, List.of(ingredient, ingredient), new ItemStack(Items.BAKED_POTATO));
        RecSerializerManager<SmokingRecipe> converter = new RecSerializerManager<>(RecipeType.SMOKING) { };
        var available = new HashMap<ItemDefinition, Long>();
        available.put(definition, 8L); available.put(plain, 64L);
        var refused = converter.createMaidRecs(List.of(description), available,
                (r, range) -> { }, r -> true, reservation -> false, done -> { }, TaskInfo.FURNACE.uid, 1);
        helper.assertTrue(refused.isEmpty() && available.get(definition) == 8L && available.get(plain) == 64L,
                "a rejected reservation must not deplete scan availability");
        var accepted = converter.createMaidRecs(List.of(description), available,
                (r, range) -> { }, r -> true, reservation -> true, done -> { }, TaskInfo.FURNACE.uid, 1);
        helper.assertTrue(accepted.size() == 1 && accepted.getFirst().amount() == 4
                && accepted.getFirst().maidItems().stream().mapToInt(MaidItem::count).sum() == 8
                && available.get(definition) == 0L && available.get(plain) == 64L,
                "repeated material slots must account exactly and must not substitute plain same-item stacks");
        var bucketDescription = new MKRecipe<>(holder, true,
                List.of(RecIngredient.of(Ingredient.of(Items.WATER_BUCKET)), RecIngredient.of(Ingredient.of(Items.WATER_BUCKET))),
                new ItemStack(Items.BAKED_POTATO));
        var buckets = new HashMap<ItemDefinition, Long>(); buckets.put(ItemDefinition.of(Items.WATER_BUCKET), 1L);
        helper.assertTrue(converter.createMaidRecs(List.of(bucketDescription), buckets,
                (r, range) -> { }, r -> true, reservation -> true, done -> { }, TaskInfo.FURNACE.uid, 1).isEmpty()
                && buckets.get(ItemDefinition.of(Items.WATER_BUCKET)) == 1L,
                "two unstackable input slots cannot plan from one bucket or drive availability negative");
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void settingsMigration(GameTestHelper helper) {
        var maid = InitEntities.MAID.get().create(helper.getLevel());
        helper.assertTrue(maid != null, "TLM maid must be constructible");
        try {
            var original = new RecipeFilterData(RecipeFilterData.Mode.WHITELIST, List.of(id("allowed")), List.of());
            maid.setAndSyncData(DataRegister.KC_STEAMER, original);
            helper.assertTrue(KitchenData.getSteamerFilter(maid).equals(original), "legacy steamer filter must migrate once");
            maid.setAndSyncData(DataRegister.KC_STEAMER, RecipeFilterData.DEFAULT);
            helper.assertTrue(KitchenData.getSteamerFilter(maid).equals(original), "legacy key must never overwrite unified data");
            KitchenData.setFilter(maid, TaskInfo.KC_STEAMER.uid, RecipeFilterData.DEFAULT);
            helper.assertTrue(KitchenData.getSteamerFilter(maid).equals(RecipeFilterData.DEFAULT), "all subsequent writes use KitchenData");
            var kitchen = KitchenData.get(maid);
            kitchen.setCookName(TaskInfo.FD_COOK_POT.uid);
            CookData legacy = new CookData("whitelist", List.of(id("pot").toString()), List.of());
            kitchen.migrate(TaskInfo.FD_COOK_POT.uid, () -> legacy);
            legacy.setMode("blacklist");
            helper.assertTrue(kitchen.getCookData().mode().equals("whitelist"), "migration must not alias legacy mutable lists or settings");
            var decoded = KitchenData.CODEC.parse(JsonOps.INSTANCE,
                    KitchenData.CODEC.encodeStart(JsonOps.INSTANCE, kitchen).getOrThrow()).getOrThrow();
            helper.assertTrue(decoded.getCookName().equals(TaskInfo.FD_COOK_POT.uid)
                    && decoded.getCookData().mode().equals("whitelist"), "selected task and independent filters must survive load");
            helper.assertTrue(CookTaskManager.getTaskIndex().stream().map(task -> task.getUid()).distinct().count()
                    == CookTaskManager.getTaskMap().size(), "catalog must have one task per UID");
            helper.succeed();
        } finally { maid.discard(); }
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID)
    public static void componentValuesAndReload(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            ItemStack named = new ItemStack(Items.CARROT, 4);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("component ingredient"));
            ItemDefinition value = ItemDefinition.of(named);
            named.setCount(0);
            helper.assertTrue(!value.is(new ItemStack(Items.CARROT)) && value.toStack(4).getCount() == 4,
                    "material must retain original components and be detached from inventory changes");
            ItemStack returned = value.stack(); returned.remove(DataComponents.CUSTOM_NAME);
            helper.assertTrue(value.stack().has(DataComponents.CUSTOM_NAME), "material access must not expose mutable plan state");
            var recipe = new SmokingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 20);
            var holder = new RecipeHolder<>(id("reload_fixture"), recipe);
            manager.replaceRecipes(List.of(holder));
            ItemStack result = new ItemStack(Items.BAKED_POTATO);
            var work = new MaidRec(holder, TaskInfo.FURNACE.uid, 7, 20, 1, List.of(result),
                    List.of(new MaidItem(value, 1)), Map.of());
            result.setCount(0); work.result().setCount(0);
            helper.assertTrue(work.result().getCount() == 1, "results must remain in the same immutable work unit");
            helper.assertTrue(work.resolve(manager, TaskInfo.FURNACE.uid, 7).isPresent(), "live identity must resolve");
            helper.assertTrue(work.resolve(manager, TaskInfo.FD_COOK_POT.uid, 7).isEmpty()
                    && work.resolve(manager, TaskInfo.FURNACE.uid, 8).isEmpty(), "task/generation changes must invalidate work");
            manager.replaceRecipes(List.of(new RecipeHolder<>(holder.id(), new SmokingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.POTATO), new ItemStack(Items.BAKED_POTATO), 0, 20))));
            helper.assertTrue(work.resolve(manager, TaskInfo.FURNACE.uid, 7).isEmpty(), "same-ID reload must invalidate cached Holder value");
            manager.replaceRecipes(List.of());
            helper.assertTrue(work.resolve(manager, TaskInfo.FURNACE.uid, 7).isEmpty(), "removed recipes must never execute");
            helper.succeed();
        } finally { manager.replaceRecipes(original); }
    }
}
