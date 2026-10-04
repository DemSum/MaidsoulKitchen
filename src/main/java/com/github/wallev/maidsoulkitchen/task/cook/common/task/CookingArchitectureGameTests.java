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
