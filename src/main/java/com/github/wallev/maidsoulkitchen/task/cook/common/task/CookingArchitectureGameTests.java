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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native registry tests: ordinary JUnit cannot initialize Minecraft recipes/components or TLM data keys. */
@PrefixGameTestTemplate(false)
public final class CookingArchitectureGameTests {
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(MaidsoulKitchen.MOD_ID, name); }

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
