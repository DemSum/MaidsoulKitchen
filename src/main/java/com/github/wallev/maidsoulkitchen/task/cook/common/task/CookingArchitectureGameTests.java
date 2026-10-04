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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void taskCookDispatchHasOneOwnerAndRetiresLoans(GameTestHelper helper) {
        var level = helper.getLevel(); var original = List.copyOf(level.getRecipeManager().getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        var other = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(2, 1, 1)); other.setNoAi(true);
        try {
            maid.setFavorability(10000); maid.setTask(new TaskCook());
            var holder = new RecipeHolder<>(id("task_cook_owner"), new com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe(
                    Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 20)); reloadRecipes(helper, List.of(holder));
            helper.assertTrue(TaskCook.resolve(maid).isEmpty() && TaskCook.select(maid, TaskInfo.KC_STEAMER.uid),
                    "source idle entry must select an enabled native device through KitchenData");
            var task = TaskCook.resolve(maid).orElseThrow(); var cm = task.getRecipesManager(maid); cm.checkAndInit();
            helper.assertTrue(task.getRecipesManager(maid) == cm
                    && ((com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid) maid).tlmk$getCookManager() == cm,
                    "brain, UI/native lookup and work must share exactly one active manager");
            cm.getInputInv().setStackInSlot(0, new ItemStack(Items.CARROT));
            ItemStack tool = new ItemStack(Items.IRON_AXE); tool.setDamageValue(12);
            tool.set(DataComponents.CUSTOM_NAME, Component.literal("retired physical loan")); cm.getInputInv().setStackInSlot(1, tool.copy());
            cm.checkAndCreateRecipes(); finishPlanning(cm); MaidRec work = cm.peekMaidRec();
            helper.assertTrue(work != null && work.taskId().equals(TaskInfo.KC_STEAMER.uid) && cm.equipTool(stack -> ItemStack.isSameItemSameComponents(stack, tool)),
                    "delegated device must plan its own holder and lend an actual component-preserving tool");
            var pos = helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4));
            com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks.tryClaim(level, pos, maid);
            com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.remember(maid, pos.west(), pos, 0.5f, 1);
            helper.assertTrue(TaskCook.select(maid, TaskInfo.FURNACE.uid) && !cm.commitMaidRec(work) && cm.getMaidRecs().isEmpty()
                    && maid.getMainHandItem().isEmpty() && CookInventoryTransactions.count(maid.getAvailableBackpackInv(),
                    stack -> ItemStack.isSameItemSameComponents(stack, tool)) == 1
                    && com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks.tryClaim(level, pos, other),
                    "selection must revoke old work, return one actual loan and release its appliance lock");
            helper.assertTrue(TaskCook.resolve(maid).orElseThrow().getRecipesManager(maid) != cm
                    && TaskCook.select(maid, KitchenData.IDLE) && TaskCook.resolve(maid).isEmpty(), "idle must retire the previous source context");
            helper.assertTrue(TaskCook.select(maid, TaskInfo.KC_STEAMER.uid), "native device must remain selectable after idle");
            helper.assertTrue(!cm.checkAndInit(), "retired brain context must stay invalid when its old device UID is selected again");
            var live = TaskCook.resolve(maid).orElseThrow().getRecipesManager(maid); live.checkAndInit();
            helper.assertTrue(live.equipTool(stack -> ItemStack.isSameItemSameComponents(stack, tool)), "same returned tool must be borrowable once");
            maid.die(level.damageSources().generic());
            helper.assertTrue(maid.getMainHandItem().isEmpty() && ((com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid) maid).tlmk$getCookManager() == null
                    && live.getMaidRecs().isEmpty(), "death must retire the actual context before native inventory drops");
        } finally { reloadRecipes(helper, original); maid.discard(); other.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeSteamerOneToFourLayersUseUnifiedWorkAndOutput(GameTestHelper helper) {
        var level = helper.getLevel(); var original = List.copyOf(level.getRecipeManager().getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        long time = level.getGameTime();
        try {
            for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++) helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
            var center = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
            maid.getSchedulePos().setHomeModeEnable(maid, center); maid.getSchedulePos().setConfigured(true);
            maid.setHomeModeEnable(true); maid.restrictTo(center, 8); maid.setOnGround(true);
            var holder = new RecipeHolder<>(id("native_steamer_work"), new com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe(
                    Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 10)); reloadRecipes(helper, List.of(holder));
            var task = new com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.TaskKcSteamer(); maid.setTask(task);
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            maid.getMaidBauble().setStackInSlot(0, com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance());
            for (int layers = 1; layers <= 4; layers++) {
                for (int y = 1; y <= 5; y++) helper.setBlock(4, y, 4, net.minecraft.world.level.block.Blocks.AIR);
                helper.setBlock(4, 0, 4, net.minecraft.world.level.block.Blocks.CAMPFIRE);
                for (int y = 1; y <= layers; y++) helper.setBlock(new net.minecraft.core.BlockPos(4, y, 4),
                        com.github.ysbbbbbb.kaleidoscopecookery.init.ModBlocks.STEAMER.get().defaultBlockState()
                                .setValue(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HALF, y == layers && layers % 2 == 0)
                                .setValue(com.github.ysbbbbbb.kaleidoscopecookery.block.kitchen.SteamerBlock.HAS_LID, y == layers));
                var nativeBe = (com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(4, layers, 4));
                var be = new com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.SteamerBe(maid); be.setBe(nativeBe);
                maid.moveTo(center.getX() - 1.5, center.getY(), center.getZ() - 1.5);
                var cm = task.getRecipesManager(maid); cm.checkAndInit();
                var input = cm.getInputInv(); for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
                var material = new ItemStack(Items.CARROT, 2); material.set(DataComponents.CUSTOM_NAME, Component.literal("steamer components")); input.setStackInSlot(0, material.copy());
                var output = cm.getOutputInv(); for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, ItemStack.EMPTY);
                cm.checkAndCreateRecipes(); finishPlanning(cm);
                var found = com.github.wallev.maidsoulkitchen.task.cook.common.ai.ReachableCookDeviceSearch.find(level, maid, center, 8, 0, be.getVerticalSearchRange(),
                        pos -> pos.equals(nativeBe.getBlockPos()), new com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetCycle(), be.getInteractionHeightOffsets());
                helper.assertTrue(found.isPresent() && !found.get().walkPos().equals(found.get().workPos())
                        && be.getWorkAreaFloorAnchor(found.get().walkPos()).equals(helper.absolutePos(new net.minecraft.core.BlockPos(4, 0, 4)))
                        && be.cookStateMatch(), "common single BFS and heat-floor anchor must reach native steamer layer " + layers);
                for (int unit = 0; unit < 2; unit++) com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.SteamerCookRule.INSTANCE.cookMake(be, cm);
                helper.assertTrue(cm.getMaidRecs().isEmpty() && input.getStackInSlot(0).isEmpty()
                        && nativeBe.getItems().stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, material)).count() == 2,
                        "two physical single-slot inputs must preserve components and commit exactly twice at layer " + layers);
                for (int tick = 0; tick < 80; tick++) {
                    ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + tick);
                    for (int y = 1; y <= layers; y++) ((com.github.ysbbbbbb.kaleidoscopecookery.blockentity.kitchen.SteamerBlockEntity)
                            helper.getBlockEntity(new net.minecraft.core.BlockPos(4, y, 4))).tick(level);
                }
                for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
                helper.assertTrue(be.hasResult() && !be.extractResult(cm) && nativeBe.getItems().stream().filter(stack -> !stack.isEmpty()).count() == 2,
                        "full unified output must leave the actual native batch in the steamer layer " + layers + ": " + be.snapshot());
                for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, ItemStack.EMPTY);
                var sword = new ItemStack(Items.IRON_SWORD); sword.setDamageValue(9); maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
                helper.assertTrue(be.extractResult(cm) && !be.hasInputs() && maid.getMainHandItem() == sword
                        && CookInventoryTransactions.count(output, stack -> stack.is(Items.BAKED_POTATO)) == 2,
                        "manager must collect the actual hand/drop batch once and restore the original hand");
                maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        } finally {
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time); reloadRecipes(helper, original); maid.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeFdSkilletAcceptsBoundedBatchAndSelectedRecipe(GameTestHelper helper) {
        var level = helper.getLevel(); var original = List.copyOf(level.getRecipeManager().getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            var holder = new RecipeHolder<>(id("native_skillet_batch"), new CampfireCookingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 20));
            var excluded = new RecipeHolder<>(id("excluded_skillet_recipe"), new CampfireCookingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.BEEF), new ItemStack(Items.COOKED_BEEF), 0, 20)); reloadRecipes(helper, List.of(holder, excluded));
            var task = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.skillet.TaskFdSkillet(); maid.setTask(task);
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            var input = maid.getAvailableInv(true); for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            var material = new ItemStack(Items.CARROT, 2); material.set(DataComponents.CUSTOM_NAME, Component.literal("native skillet components"));
            input.setStackInSlot(0, material.copy()); input.setStackInSlot(2, new ItemStack(Items.BEEF));
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm); var work = cm.peekMaidRec();
            helper.setBlock(new net.minecraft.core.BlockPos(2, 0, 2), net.minecraft.world.level.block.Blocks.CAMPFIRE);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), vectorwing.farmersdelight.common.registry.ModBlocks.SKILLET.get().defaultBlockState()
                    .setValue(vectorwing.farmersdelight.common.block.SkilletBlock.WATERLOGGED, true));
            var skillet = (vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var be = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.skillet.SkilletBe(maid); be.setBe(skillet);
            helper.assertTrue(work != null && work.amount() == 2 && work.maidItems().getFirst().count() == 2 && !be.insertInputs(work, cm)
                    && input.getStackInSlot(0).getCount() == 2 && !skillet.hasStoredStack(), "a waterlogged native skillet cannot consume its bounded component-bearing plan");
            level.setBlockAndUpdate(skillet.getBlockPos(), skillet.getBlockState().setValue(vectorwing.farmersdelight.common.block.SkilletBlock.WATERLOGGED, false));
            var rule = com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.NormalCookRule.<vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity, CampfireCookingRecipe>getInstance();
            rule.cookMake(be, cm);
            helper.assertTrue(cm.getMaidRecs().isEmpty() && skillet.getStoredStack().getCount() == 2 && ItemStack.isSameItemSameComponents(skillet.getStoredStack(), material)
                    && input.getStackInSlot(0).isEmpty() && input.getStackInSlot(2).is(Items.BEEF), "native whole-stack acceptance must preserve components and leave excluded recipe materials untouched");
            for (int i = 0; i < 100; i++) vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity.cookingTick(level, skillet.getBlockPos(), skillet.getBlockState(), skillet);
            int actual = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(skillet.getBlockPos()).inflate(3)).stream()
                    .filter(entity -> entity.getItem().is(Items.BAKED_POTATO)).mapToInt(entity -> entity.getItem().getCount()).sum();
            cm.checkAndCreateRecipes(); finishPlanning(cm); rule.cookMake(be, cm);
            helper.assertTrue(actual == 2 && !skillet.hasStoredStack() && cm.getMaidRecs().isEmpty() && input.getStackInSlot(2).getCount() == 1,
                    "native timing must emit exactly two real results; no fallback loop may cook an excluded recipe");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeBasinConsumesCompleteCountedUnitOnce(GameTestHelper helper) {
        var level = helper.getLevel(); var original = List.copyOf(level.getRecipeManager().getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            var recipe = new com.mao.barbequesdelight.content.recipe.SimpleSkeweringRecipe(); recipe.tool = Ingredient.of(Items.STICK);
            recipe.ingredient = Ingredient.of(Items.CARROT); recipe.ingredientCount = 3; recipe.side = Ingredient.of(Items.POTATO); recipe.sideCount = 2;
            recipe.output = new ItemStack(Items.COOKED_BEEF, 2); var holder = new RecipeHolder<>(id("native_counted_skewer"), recipe); reloadRecipes(helper, List.of(holder));
            var task = new com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.basin.TaskBbqBasin(); maid.setTask(task);
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            maid.getMaidBauble().setStackInSlot(0, com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance());
            boolean[] rejectSide = {true};
            var cm = new com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<com.mao.barbequesdelight.content.recipe.SkeweringRecipe<?>>(task.getRecSerializerManager(), maid, task) {
                @Override public net.neoforged.neoforge.items.IItemHandlerModifiable getInputInv() {
                    var actual = super.getInputInv(); return new net.neoforged.neoforge.items.IItemHandlerModifiable() {
                        @Override public int getSlots() { return actual.getSlots(); }
                        @Override public ItemStack getStackInSlot(int slot) { return actual.getStackInSlot(slot); }
                        @Override public void setStackInSlot(int slot, ItemStack stack) { actual.setStackInSlot(slot, stack); }
                        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return actual.insertItem(slot, stack, simulate); }
                        @Override public ItemStack extractItem(int slot, int count, boolean simulate) { return rejectSide[0] && actual.getStackInSlot(slot).is(Items.POTATO) && !simulate ? ItemStack.EMPTY : actual.extractItem(slot, count, simulate); }
                        @Override public int getSlotLimit(int slot) { return actual.getSlotLimit(slot); }
                        @Override public boolean isItemValid(int slot, ItemStack stack) { return actual.isItemValid(slot, stack); }
                    };
                }
            };
            cm.checkAndInit(); var input = cm.getInputInv(); input.setStackInSlot(0, new ItemStack(Items.STICK, 2));
            var carrot = new ItemStack(Items.CARROT, 6); carrot.set(DataComponents.CUSTOM_NAME, Component.literal("counted native basin components"));
            input.setStackInSlot(1, carrot.copy()); input.setStackInSlot(2, new ItemStack(Items.POTATO, 4)); cm.syncInv(); cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), com.mao.barbequesdelight.init.registrate.BBQDBlocks.BASIN.get());
            var basin = (com.mao.barbequesdelight.content.block.BasinBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var be = new com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.basin.BasinBe(maid); be.setBe(basin);
            var work = cm.peekMaidRec(); helper.assertTrue(cm.getMaidRecs().size() == 2 && work.maidItems().get(1).count() == 3 && work.maidItems().get(2).count() == 2,
                    "native ingredientCount and sideCount must reserve two complete independent work units");
            helper.assertTrue(!be.insertInputs(work, cm) && !be.hasInputs() && cm.getMaidRecs().isEmpty()
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.STICK)) == 2 && CookInventoryTransactions.count(input, stack -> ItemStack.isSameItemSameComponents(stack, carrot)) == 6
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.POTATO)) == 4 && CookInventoryTransactions.count(cm.getOutputInv(), stack -> stack.is(Items.COOKED_BEEF)) == 0,
                    "actual side extraction refusal must refund preceding physical inputs and produce no native effect or executable work");
            rejectSide[0] = false; cm.checkAndCreateRecipes(); finishPlanning(cm); work = cm.peekMaidRec();
            var output = cm.getOutputInv(); for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            helper.assertTrue(!be.insertInputs(work, cm) && cm.peekMaidRec() == work && !be.hasInputs()
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.CARROT)) == 6, "full output must retain counted physical ingredients and pending work");
            output.setStackInSlot(0, ItemStack.EMPTY);
            helper.assertTrue(be.insertInputs(work, cm) && cm.commitMaidRec(work) && !cm.commitMaidRec(work) && !be.hasInputs()
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.STICK)) == 1 && CookInventoryTransactions.count(input, stack -> stack.is(Items.CARROT)) == 3
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.POTATO)) == 2 && CookInventoryTransactions.count(output, stack -> stack.is(Items.COOKED_BEEF)) == 2,
                    "native assembly must consume one actual stick, three ingredient items and two sides before one work commits");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeGrillAcceptsEntriesFlipsAndRetainsFullOutput(GameTestHelper helper) {
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        var rule = com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.grill.GrillCookRule.getInstance().getOrCreate();
        var be = new com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.grill.GrillBe(maid);
        com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<com.mao.barbequesdelight.content.recipe.GrillingRecipe<?>> cm = null;
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.grill.TaskBbqGrill(); maid.setTask(task);
            var description = task.getRecSerializerManager().getRecipes(helper.getLevel()).stream()
                    .filter(recipe -> recipe.inItems().size() == 1 && recipe.inItems().getFirst().ingredient.getItems().length > 0).findFirst().orElseThrow();
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(description.idStr()), List.of()));
            maid.getMaidBauble().setStackInSlot(0, com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance());
            cm = task.getRecipesManager(maid); cm.checkAndInit();
            var material = description.inItems().getFirst().ingredient.getItems()[0].copyWithCount(2); material.set(DataComponents.CUSTOM_NAME, Component.literal("native grill components"));
            cm.getInputInv().setStackInSlot(0, material.copy()); cm.syncInv(); cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 0, 2), net.minecraft.world.level.block.Blocks.CAMPFIRE);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), com.mao.barbequesdelight.init.registrate.BBQDBlocks.GRILL.get());
            var grill = (com.mao.barbequesdelight.content.block.GrillBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2)); be.setBe(grill);
            var output = cm.getOutputInv(); for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            helper.assertTrue(cm.getMaidRecs().size() == 2 && rule.canMoveTo(be, cm), "two physical grill inputs must have distinct sole-manager work units");
            rule.cookMake(be, cm);
            helper.assertTrue(cm.getMaidRecs().size() == 1 && grill.entries[0].stack.getCount() == 1
                    && CookInventoryTransactions.count(cm.getInputInv(), stack -> ItemStack.isSameItemSameComponents(stack, material)) == 1,
                    "native entry acceptance, not copied grillStacks, must consume exactly one work and one material");
            rule.tickCookMake(be, cm);
            helper.assertTrue(cm.getMaidRecs().isEmpty() && grill.entries[1].stack.getCount() == 1, "the next native entry must accept the second actual work once");
            int duration = grill.entries[0].duration;
            for (int i = 0; i <= duration; i++) {
                for (var entry : grill.entries) entry.tick(grill, true);
                rule.tickCookMake(be, cm);
            }
            helper.assertTrue(grill.entries[0].flipped && grill.entries[1].flipped && be.hasResult()
                    && java.util.Arrays.stream(grill.entries).mapToInt(entry -> entry.stack.getCount()).sum() == 2,
                    "full output must not prevent native flipping and must retain both actual cooked entries");
            var expected = grill.entries[0].stack.copy(); output.setStackInSlot(0, ItemStack.EMPTY); rule.tickCookMake(be, cm);
            helper.assertTrue(!be.hasInputs() && CookInventoryTransactions.count(output, stack -> ItemStack.isSameItemSameComponents(stack, expected)) == 2,
                    "only real finished native stacks with retained components may move into available output");
        } finally { if (cm != null) rule.tickStop(be, cm); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeCuisineRetainsPlanUntilPhysicalServing(GameTestHelper helper) {
        var level = helper.getLevel(); long time = level.getGameTime();
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        var rule = com.github.wallev.maidsoulkitchen.task.cook.cuisine.cuisine.CuisineCookRule.getInstance().getOrCreate();
        var be = new com.github.wallev.maidsoulkitchen.task.cook.cuisine.cuisine.CuisineBe(maid);
        com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<dev.xkmc.cuisinedelight.content.recipe.BaseCuisineRecipe<?>> cm = null;
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.cuisine.cuisine.TaskCdCuisine(); maid.setTask(task);
            var description = task.getRecSerializerManager().getRecipes(level).stream().filter(recipe -> recipe.inItems().size() > 1 && recipe.inItems().size() <= 6
                    && recipe.inItems().stream().skip(1).allMatch(ingredient -> java.util.Arrays.stream(ingredient.ingredient.getItems())
                            .anyMatch(stack -> dev.xkmc.cuisinedelight.content.logic.IngredientConfig.get().getEntry(stack) != null))).findFirst().orElseThrow();
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(description.idStr()), List.of()));
            maid.getMaidBauble().setStackInSlot(0, com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance());
            cm = task.getRecipesManager(maid); cm.checkAndInit();
            var input = cm.getInputInv();
            input.setStackInSlot(0, dev.xkmc.cuisinedelight.init.registrate.CDItems.PLATE.asStack());
            for (int i = 1; i < description.inItems().size(); i++) input.setStackInSlot(i, java.util.Arrays.stream(description.inItems().get(i).ingredient.getItems())
                    .filter(stack -> dev.xkmc.cuisinedelight.content.logic.IngredientConfig.get().getEntry(stack) != null).findFirst().orElseThrow().copyWithCount(1));
            var tool = dev.xkmc.cuisinedelight.init.registrate.CDItems.SPATULA.asStack(); tool.set(DataComponents.CUSTOM_NAME, Component.literal("native cuisine tool"));
            input.setStackInSlot(7, tool.copy()); cm.syncInv(); cm.checkAndCreateRecipes(); finishPlanning(cm);
            var work = cm.peekMaidRec();
            helper.assertTrue(work != null && work.maidItems().getFirst().role() == MaidItem.Role.CONTAINER
                    && work.maidItems().stream().anyMatch(item -> item.role() == MaidItem.Role.TOOL && item.item().is(tool)), "the sole work unit must preserve actual component-bearing tool and plate");
            helper.setBlock(new net.minecraft.core.BlockPos(2, 0, 2), net.minecraft.world.level.block.Blocks.CAMPFIRE);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), dev.xkmc.cuisinedelight.init.registrate.CDBlocks.SKILLET.get());
            var skillet = (dev.xkmc.cuisinedelight.content.block.CuisineSkilletBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2)); be.setBe(skillet);
            var output = cm.getOutputInv(); for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            helper.assertTrue(rule.canMoveTo(be, cm), "native heated Cuisine must use the common Rule selection"); rule.cookMake(be, cm);
            helper.assertTrue(be.hasInputs() && cm.peekMaidRec() == work && CookInventoryTransactions.count(input, stack -> stack.is(dev.xkmc.cuisinedelight.init.registrate.CDItems.PLATE.get())) == 1,
                    "initial real ingredients must not consume the plan or serving plate");
            int maxTime = work.maidItems().stream().filter(item -> item.role() == MaidItem.Role.INGREDIENT)
                    .mapToInt(item -> dev.xkmc.cuisinedelight.content.logic.IngredientConfig.get().getEntry(item.item().stack()).min_time).max().orElseThrow();
            for (int i = 1; i <= maxTime + 12; i++) {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + i);
                skillet.cookingData.update(time + i); rule.tickCookMake(be, cm);
            }
            helper.assertTrue(be.hasInputs() && cm.peekMaidRec() == work && skillet.cookingData.contents.size() == description.inItems().size() - 1
                    && CookInventoryTransactions.count(input, stack -> stack.is(dev.xkmc.cuisinedelight.init.registrate.CDItems.PLATE.get())) == 1,
                    "full output must preserve native cooked data, every scheduled ingredient, plate and pending work");
            output.setStackInSlot(0, ItemStack.EMPTY);
            var expected = be.getResult(); rule.tickCookMake(be, cm);
            helper.assertTrue(!be.hasInputs() && cm.getMaidRecs().isEmpty() && CookInventoryTransactions.count(input, stack -> stack.is(dev.xkmc.cuisinedelight.init.registrate.CDItems.PLATE.get())) == 0
                    && CookInventoryTransactions.count(output, stack -> ItemStack.isSameItemSameComponents(stack, expected)) == expected.getCount(),
                    "native PlateItem must produce exactly its real food before committing work and resetting native data");
            rule.tickStop(be, cm); rule.tickStop(be, cm);
            helper.assertTrue(maid.getMainHandItem().isEmpty() && CookInventoryTransactions.count(input, stack -> ItemStack.isSameItemSameComponents(stack, tool)) == 1,
                    "the actual component-bearing spatula must return exactly once");
        } finally {
            if (cm != null) rule.tickStop(be, cm);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time); maid.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeFermentationUsesExactHalfTankVolume(GameTestHelper helper) {
        var level = helper.getLevel(); var original = List.copyOf(level.getRecipeManager().getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            var sake = (dev.xkmc.youkaishomecoming.content.item.fluid.SakeFluid) net.minecraft.core.registries.BuiltInRegistries.FLUID.stream()
                    .filter(fluid -> fluid instanceof dev.xkmc.youkaishomecoming.content.item.fluid.SakeFluid candidate && candidate.getSource() == fluid && candidate.type.amount() == 250).findFirst().orElseThrow();
            var recipe = new dev.xkmc.youkaishomecoming.content.pot.ferment.SimpleFermentationRecipe();
            recipe.inputFluid = net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(new net.neoforged.neoforge.fluids.FluidStack(sake, 1000));
            recipe.outputFluid = new net.neoforged.neoforge.fluids.FluidStack(sake, 500); recipe.time = 2;
            recipe.results = new java.util.ArrayList<>(List.of(new ItemStack(Items.DRIED_KELP)));
            var holder = new RecipeHolder<>(id("native_half_fermentation"), recipe);
            var oversized = new dev.xkmc.youkaishomecoming.content.pot.ferment.SimpleFermentationRecipe();
            oversized.inputFluid = net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(net.minecraft.world.level.material.Fluids.WATER);
            oversized.outputFluid = new net.neoforged.neoforge.fluids.FluidStack(sake, 4000);
            var oversizedHolder = new RecipeHolder<>(id("impossible_native_capacity"), oversized);
            reloadRecipes(helper, List.of(holder, oversizedHolder));
            var task = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.TaskYhcFermentationTank(); maid.setTask(task);
            helper.assertTrue(task.getRecSerializerManager().getRecipes(level).stream().noneMatch(description -> description.id().equals(oversizedHolder.id())),
                    "the registered native 1000mB tank must not plan a physically impossible 4000mB recipe");
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            var filled = sake.type.asStack(2); filled.set(DataComponents.CUSTOM_NAME, Component.literal("half-tank fluid components")); input.setStackInSlot(0, filled);
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            var rec = cm.peekMaidRec();
            helper.assertTrue(rec != null && rec.maidItems().size() == 1 && rec.maidItems().getFirst().role() == MaidItem.Role.FLUID && rec.maidItems().getFirst().count() == 2,
                    "the native exact 500mB requirement must reserve two 250mB bottles, independently of the ingredient's representative 1000mB stack");
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), dev.xkmc.youkaishomecoming.init.registrate.YHBlocks.FERMENT.get());
            var tank = (dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var be = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationCookBe(maid); be.setBe(tank);
            boolean inserted = be.insertInputs(rec, cm);
            helper.assertTrue(inserted && be.recMatch() && be.getFluidStack().getAmount() == 500 && cm.commitMaidRec(rec)
                    && CookInventoryTransactions.count(input, stack -> stack.is(sake.type.getContainer())) == 2,
                    "native half-tank fluid-only work must accept both physical component-bearing bottles and return both actual containers: inserted=" + inserted
                            + ", fluid=" + be.getFluidStack() + ", matching=" + be.recMatch() + ", containers=" + CookInventoryTransactions.count(input, stack -> stack.is(sake.type.getContainer()))
                            + ", pending=" + cm.getMaidRecs().size() + ", source=" + input.getStackInSlot(0)
                            + ", expected=" + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(sake)
                            + ", predicate=" + recipe.inputFluid.test(be.getFluidStack())
                            + ", nativeMatch=" + recipe.matches(new dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationDummyContainer(tank.items, tank.fluids), level));
            for (int i = 0; i < 4; i++) tank.tick();
            helper.assertTrue(be.hasResult() && be.getFluidStack().getAmount() == 500, "native half-tank processing must preserve volume and create its own actual result");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeFermentationAcceptsBothInputsAndRealOutputs(GameTestHelper helper) {
        var level = helper.getLevel(); var original = List.copyOf(level.getRecipeManager().getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.TaskYhcFermentationTank(); maid.setTask(task);
            var sake = (dev.xkmc.youkaishomecoming.content.item.fluid.SakeFluid) net.minecraft.core.registries.BuiltInRegistries.FLUID.stream()
                    .filter(fluid -> fluid instanceof dev.xkmc.youkaishomecoming.content.item.fluid.SakeFluid candidate && candidate.getSource() == fluid).findFirst().orElseThrow();
            var recipe = new dev.xkmc.youkaishomecoming.content.pot.ferment.SimpleFermentationRecipe();
            recipe.ingredients = new java.util.ArrayList<>(List.of(Ingredient.of(Items.CARROT)));
            recipe.inputFluid = net.neoforged.neoforge.fluids.crafting.FluidIngredient.of(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
            recipe.outputFluid = new net.neoforged.neoforge.fluids.FluidStack(sake, 1000);
            recipe.results = new java.util.ArrayList<>(List.of(new ItemStack(Items.DRIED_KELP), new ItemStack(Items.COOKED_BEEF))); recipe.time = 2;
            var holder = new RecipeHolder<>(id("native_fermentation"), recipe); reloadRecipes(helper, List.of(holder));
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            maid.getMaidBauble().setStackInSlot(0, com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance());
            var cm = task.getRecipesManager(maid); cm.checkAndInit();
            cm.getInputInv().setStackInSlot(0, new ItemStack(Items.WATER_BUCKET)); cm.getInputInv().setStackInSlot(1, new ItemStack(Items.CARROT)); cm.syncInv();
            cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), dev.xkmc.youkaishomecoming.init.registrate.YHBlocks.FERMENT.get());
            var tank = (dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var be = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.ferment.FermentationCookBe(maid); be.setBe(tank);
            var rec = cm.peekMaidRec();
            helper.assertTrue(rec != null && rec.maidItems().stream().anyMatch(material -> material.role() == MaidItem.Role.FLUID && material.count() == 1), "native fluid work must retain its physical fluid-container requirement");
            boolean inserted = be.insertInputs(rec, cm);
            helper.assertTrue(inserted && be.recMatch() && cm.commitMaidRec(rec)
                    && !tank.getBlockState().getValue(dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlock.OPEN)
                    && CookInventoryTransactions.count(cm.getInputInv(), stack -> stack.is(Items.BUCKET)) == 1,
                    "fermentation may close and commit only after real tank and item acceptance, returning one actual input bucket: inserted=" + inserted
                            + ", matching=" + be.recMatch() + ", fluid=" + be.getFluidStack() + ", item0=" + tank.items.getItem(0) + ", progress=" + tank.inProgress()
                            + ", buckets=" + CookInventoryTransactions.count(cm.getInputInv(), stack -> stack.is(Items.BUCKET)) + ", pending=" + cm.getMaidRecs().size());
            for (int i = 0; i < 6; i++) tank.tick();
            helper.assertTrue(be.hasResult() && CookInventoryTransactions.count(be.getInv(), stack -> stack.is(Items.DRIED_KELP) || stack.is(Items.COOKED_BEEF)) == 2 && be.getFluidStack().getFluid() == sake,
                    "native fermentation, not a duplicate processor, must produce both solid and fluid results: items=" + tank.items.getAsList()
                            + ", fluid=" + be.getFluidStack() + ", progress=" + tank.inProgress() + ", matching=" + be.recMatch() + ", open=" + tank.getBlockState().getValue(dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationTankBlock.OPEN));
            var output = cm.getOutputInv();
            for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            cm.getInputInv().setStackInSlot(2, sake.type.getContainer().getDefaultInstance().copyWithCount(1000 / sake.type.amount())); cm.syncInv();
            var rule = com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.FluidPotCookRule2.getInstance(); rule.cookMake(be, cm);
            helper.assertTrue(CookInventoryTransactions.count(be.getInv(), stack -> stack.is(Items.DRIED_KELP) || stack.is(Items.COOKED_BEEF)) == 2 && be.getFluidStack().getAmount() == 1000, "full output must retain native solid and fluid results in the device");
            output.setStackInSlot(0, new ItemStack(Items.DRIED_KELP, 63)); rule.cookMake(be, cm);
            helper.assertTrue(be.getResult().is(Items.COOKED_BEEF) && output.getStackInSlot(0).getCount() == 64,
                    "partial solid capacity must move only one real result and never redirect the remainder into input");
            output.setStackInSlot(1, ItemStack.EMPTY); output.setStackInSlot(2, ItemStack.EMPTY); rule.cookMake(be, cm);
            helper.assertTrue(!be.hasResult() && be.getFluidStack().getAmount() == 1000 - sake.type.amount()
                    && CookInventoryTransactions.count(output, stack -> ItemStack.isSameItemSameComponents(stack, sake.type.asStack(1))) == 1,
                    "native Sake output placed in FakePlayer inventory must reach output once without a manufactured fallback");
            for (int slot = 3; slot < output.getSlots(); slot++) output.setStackInSlot(slot, ItemStack.EMPTY);
            for (int visit = 0; visit < 32 && be.hasFluid(); visit++) rule.cookMake(be, cm);
            helper.assertTrue(!be.hasFluid() && CookInventoryTransactions.count(output, stack -> ItemStack.isSameItemSameComponents(stack, sake.type.asStack(1))) == 1000 / sake.type.amount(),
                    "repeated visits must bottle each remaining native portion once and retain no unclaimed fluid");
            var fake = ((com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid) maid).tlmk$getFakePlayer().get();
            helper.assertTrue(fake.getInventory().isEmpty() && fake.getMainHandItem().isEmpty(), "native temporary player storage must retain no duplicate or unclaimed output");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeKettleReplenishesThroughOwnedTransactions(GameTestHelper helper) {
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.kettle.TaskYhcKettle(); maid.setTask(task);
            var description = task.getRecSerializerManager().getRecipes(helper.getLevel()).stream().filter(recipe -> !recipe.inItems().isEmpty()
                    && recipe.inItems().size() <= 4 && recipe.inItems().stream().allMatch(ingredient -> ingredient.ingredient.getItems().length > 0)).findFirst().orElseThrow();
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(description.idStr()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            for (int slot = 0; slot < description.inItems().size(); slot++) input.setStackInSlot(slot, description.inItems().get(slot).ingredient.getItems()[0].copyWithCount(1));
            helper.setBlock(new net.minecraft.core.BlockPos(2, 0, 2), net.minecraft.world.level.block.Blocks.CAMPFIRE);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), dev.xkmc.youkaishomecoming.init.registrate.YHBlocks.KETTLE.get());
            var kettle = (dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var be = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.kettle.KettleBe(maid); be.setBe(kettle);
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            var rule = com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.WaterFdPotCookRule.getInstance();
            helper.assertTrue(!rule.canMoveTo(be, cm), "dry kettle without a native water source cannot claim a cooking job");
            var refused = new ItemStackHandler(1) {
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return simulate ? super.extractItem(slot, amount, true) : ItemStack.EMPTY; }
            }; refused.setStackInSlot(0, new ItemStack(Items.WATER_BUCKET));
            helper.assertTrue(!cm.useItem(new com.github.wallev.maidsoulkitchen.task.cook.common.manager.GatherResult(refused, 0), kettle.getBlockPos())
                    && kettle.getWater() == 0 && refused.getStackInSlot(0).is(Items.WATER_BUCKET),
                    "refused real extraction must not create native kettle water");
            input.setStackInSlot(5, new ItemStack(Items.WATER_BUCKET)); cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.assertTrue(rule.canMoveTo(be, cm), "a native water source must make the planned kettle work eligible"); rule.cookMake(be, cm);
            helper.assertTrue(be.recMatch() && be.hasFluid() && cm.getMaidRecs().isEmpty()
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.WATER_BUCKET)) == 0
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.BUCKET)) == 1,
                    "native kettle refill must consume exactly one physical water source and return its actual bucket once");
            kettle.setWater(dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlockEntity.WATER_BOTTLE);
            int before = CookInventoryTransactions.count(input, stack -> stack.is(Items.BUCKET));
            helper.assertTrue(be.hasFluid() && !be.replenishFluid(cm) && CookInventoryTransactions.count(input, stack -> stack.is(Items.BUCKET)) == before,
                    "the exact native bottle-water threshold is sufficient and must not refill again");
        } finally { maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeDryingBoundsReservationsAndStartsTimers(GameTestHelper helper) {
        var level = helper.getLevel(); var original = List.copyOf(level.getRecipeManager().getRecipes()); var day = level.getDayTime();
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            level.setDayTime(1000); level.updateSkyBrightness();
            var task = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.dryingrack.TaskYhcDryingRack(); maid.setTask(task);
            var holder = new RecipeHolder<>(id("native_drying"), new dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 2));
            reloadRecipes(helper, List.of(holder));
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            input.setStackInSlot(0, new ItemStack(Items.CARROT, 8));
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), dev.xkmc.youkaishomecoming.init.registrate.YHBlocks.RACK.get());
            var rack = (dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var be = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.dryingrack.DryingRackBe(maid); be.setBe(rack);
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            var rec = cm.peekMaidRec();
            helper.assertTrue(rec != null && rec.amount() == 4 && rec.maidItems().getFirst().count() == 4,
                    "four-position native drying must reserve only four of eight physical materials");
            helper.assertTrue(be.insertInputs(rec, cm) && cm.commitMaidRec(rec) && rack.getItems().stream().allMatch(stack -> stack.is(Items.CARROT) && stack.getCount() == 1)
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.CARROT)) == 4,
                    "native placeFood must accept exactly four detached inputs, preserving the unreserved half");
            helper.assertTrue(!be.insertInputs(rec, cm), "an accepted/stale drying work identity cannot insert again");
            for (int i = 0; i < 3; i++) dev.xkmc.youkaishomecoming.content.pot.rack.DryingRackBlockEntity.cookTick(level, rack.getBlockPos(), rack.getBlockState(), rack);
            helper.assertTrue(rack.getItems().stream().allMatch(ItemStack::isEmpty), "native timers initialized by placeFood must finish every occupied position");
            cm.checkAndCreateRecipes(); finishPlanning(cm); level.setDayTime(18000); level.updateSkyBrightness();
            helper.assertTrue(!be.cookStateMatch() && !be.isCookBe(rack) && !be.insertInputs(cm.peekMaidRec(), cm)
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.CARROT)) == 4,
                    "night must prevent both selection and actual native insertion without consuming materials");
        } finally { level.setDayTime(day); level.updateSkyBrightness(); reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeMokaSharesPotRuleAndContainers(GameTestHelper helper) {
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.moka.TaskYhcMoka(); maid.setTask(task);
            var description = task.getRecSerializerManager().getRecipes(helper.getLevel()).stream().filter(recipe -> !recipe.inItems().isEmpty()
                    && recipe.inItems().size() <= 4 && recipe.inItems().stream().allMatch(ingredient -> ingredient.ingredient.getItems().length > 0)).findFirst().orElseThrow();
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(description.idStr()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            for (int slot = 0; slot < description.inItems().size(); slot++) input.setStackInSlot(slot, description.inItems().get(slot).ingredient.getItems()[0].copyWithCount(1));
            helper.setBlock(new net.minecraft.core.BlockPos(2, 0, 2), net.minecraft.world.level.block.Blocks.CAMPFIRE);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), dev.xkmc.youkaishomecoming.init.registrate.YHBlocks.MOKA.get());
            var moka = (dev.xkmc.youkaishomecoming.content.pot.moka.MokaMakerBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var be = new com.github.wallev.maidsoulkitchen.task.cook.youkaishomecoming.moka.MokaBe(maid); be.setBe(moka);
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            var rec = cm.peekMaidRec();
            helper.assertTrue(rec != null && be.cookStateMatch() && be.insertInputs(rec, cm) && be.recMatch() && cm.commitMaidRec(rec),
                    "native Moka must accept the unified descriptor and match its original Holder");
            for (int slot = 0; slot < 4; slot++) moka.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
            moka.getInventory().setStackInSlot(be.getContainerSlot(), new ItemStack(Items.BOWL, 2));
            var rule = com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.FdPotCookRule.<dev.xkmc.youkaishomecoming.content.pot.moka.MokaMakerBlockEntity, dev.xkmc.youkaishomecoming.content.pot.moka.MokaRecipe>getInstance();
            helper.assertTrue(rule.canMoveTo(be, cm), "shared pot rule must see idle native containers"); rule.cookMake(be, cm);
            helper.assertTrue(be.getNowContainer().isEmpty() && CookInventoryTransactions.count(input, stack -> stack.is(Items.BOWL)) == 2,
                    "shared pot rule must return only the actual idle containers through manager transactions");
        } finally { maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeCuttingUsesTickLifecycleAndPhysicalTool(GameTestHelper helper) {
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        var task = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cuttingboard.TaskFdCuttingBoard(); maid.setTask(task);
        var cm = task.getRecipesManager(maid);
        var rule = com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cuttingboard.CuttingBoardCookRule.getInstance().getOrCreate();
        var cookBe = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cuttingboard.CuttingBoardBe(maid);
        try {
            var description = task.getRecSerializerManager().getRecipes(helper.getLevel()).stream()
                    .filter(recipe -> recipe.inItems().size() == 1 && recipe.inItems().getFirst().ingredient.getItems().length > 0
                            && recipe.tool().ingredient.getItems().length > 0).findFirst().orElseThrow();
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(description.idStr()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            var ingredient = description.inItems().getFirst().ingredient.getItems()[0].copyWithCount(2);
            var tool = description.tool().ingredient.getItems()[0].copyWithCount(1);
            tool.set(DataComponents.CUSTOM_NAME, Component.literal("borrowed cutting tool"));
            input.setStackInSlot(0, ingredient.copy()); input.setStackInSlot(2, tool.copy());
            cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), vectorwing.farmersdelight.common.registry.ModBlocks.CUTTING_BOARD.get());
            var board = (vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            cookBe.setBe(board);
            helper.assertTrue(cm.getMaidRecs().size() == 2, "each native board input must have its own work identity");
            rule.cookMake(cookBe, cm);
            helper.assertTrue(cm.getMaidRecs().size() == 2 && ItemStack.isSameItemSameComponents(maid.getMainHandItem(), tool)
                    && CookInventoryTransactions.count(input, stack -> ItemStack.isSameItemSameComponents(stack, ingredient)) == 2,
                    "start must lend the actual tool and retain both plans and previous hand materials");
            rule.tickCookMake(cookBe, cm);
            helper.assertTrue(board.getStoredItem().getCount() == 1 && cm.getMaidRecs().size() == 1
                    && CookInventoryTransactions.count(input, stack -> ItemStack.isSameItemSameComponents(stack, ingredient)) == 1,
                    "only actual board acceptance may consume one planned material and work identity");
            for (int i = 0; i < 5; i++) rule.tickCookMake(cookBe, cm);
            helper.assertTrue(board.getStoredItem().isEmpty(), "the fifth tick must perform native cutting rather than a second recipe implementation");
            var actualTool = maid.getMainHandItem().copy();
            rule.tickStop(cookBe, cm); rule.tickStop(cookBe, cm);
            helper.assertTrue(maid.getMainHandItem().isEmpty()
                    && CookInventoryTransactions.count(input, stack -> ItemStack.isSameItemSameComponents(stack, actualTool)) == 1
                    && cm.getMaidRecs().isEmpty(), "stop must return the actual damaged component-bearing tool exactly once and revoke stale work");
            board.getInventory().insertItem(0, ingredient.copyWithCount(1), false);
            cm.checkAndInit(); cookBe.setBe(board); rule.cookMake(cookBe, cm); rule.tickCookMake(cookBe, cm);
            helper.assertTrue(board.getStoredItem().isEmpty(), "a stored native input must resume without another queued work unit");
            rule.tickStop(cookBe, cm);
            var returnedTool = input.getStackInSlot(com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil.findStackSlot(input, description.tool().ingredient::test)).copy();
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            maid.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, returnedTool);
            maid.getMaidBauble().setStackInSlot(0, com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance());
            cm.checkAndInit(); cm.getInputInv().setStackInSlot(0, ingredient.copyWithCount(1)); cm.syncInv();
            cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.assertTrue(cm.peekMaidRec() != null, "hub planning must use the already held physical tool without requesting a second tool from a chest");
            rule.cookMake(cookBe, cm); rule.tickCookMake(cookBe, cm); rule.tickStop(cookBe, cm);
            helper.assertTrue(board.getStoredItem().getCount() == 1 && maid.getMainHandItem() == returnedTool,
                    "stopping a hub job must preserve a tool originally held by the maid rather than returning a borrowed copy");
        } finally { rule.tickStop(cookBe, cm); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeFurnaceFamiliesShareOneQueue(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var original = List.copyOf(recipes.getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1)); maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace.TaskFurnace(); maid.setTask(task);
            maid.restrictTo(helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 2)), 5);
            List<RecipeHolder<?>> fixtures = List.of(
                    new RecipeHolder<>(id("furnace_smelting"), new SmeltingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.IRON_ORE), new ItemStack(Items.IRON_INGOT), 0, 20)),
                    new RecipeHolder<>(id("furnace_smoking"), new SmokingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 20)),
                    new RecipeHolder<>(id("furnace_blasting"), new BlastingRecipe("", CookingBookCategory.MISC, Ingredient.of(Items.GOLD_ORE), new ItemStack(Items.GOLD_INGOT), 0, 20)));
            reloadRecipes(helper, fixtures);
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", fixtures.stream().map(holder -> holder.id().toString()).toList(), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            input.setStackInSlot(0, new ItemStack(Items.IRON_ORE)); input.setStackInSlot(1, new ItemStack(Items.CARROT));
            input.setStackInSlot(2, new ItemStack(Items.GOLD_ORE)); input.setStackInSlot(3, new ItemStack(Items.COAL));
            var blocks = List.of(net.minecraft.world.level.block.Blocks.FURNACE, net.minecraft.world.level.block.Blocks.SMOKER, net.minecraft.world.level.block.Blocks.BLAST_FURNACE);
            var devices = new java.util.ArrayList<net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity>();
            for (int i = 0; i < blocks.size(); i++) {
                var pos = new net.minecraft.core.BlockPos(2 + i, 1, 2); helper.setBlock(pos, blocks.get(i));
                var be = (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
                be.setItem(1, new ItemStack(Items.COAL)); devices.add(be);
            }
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.assertTrue(cm.getMaidRecs().size() == 3, "all present furnace families must share the manager's one queue");
            var cookBe = new com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace.FurnaceCookBe(maid);
            var rule = com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.FuelCookRule.getInstance();
            for (int i = 0; i < devices.size(); i++) {
                cookBe.setBe(devices.get(i));
                var selected = cm.peekMaidRec(cookBe);
                helper.assertTrue(selected != null && selected.recipeType()
                        == ((com.github.wallev.maidsoulkitchen.task.cook.common.cbaccessor.IAbstractFurnaceAccessor) devices.get(i)).tlmk$getRecipeType(),
                        "device-condition selection must preserve each real furnace's recipe type");
                rule.cookMake(cookBe, cm);
                helper.assertTrue(cookBe.recMatch() && cm.getMaidRecs().size() == 2 - i,
                        "native acceptance must remove exactly its selected work identity, without a stale index queue");
            }
            var smoker = devices.get(1); cookBe.setBe(smoker); smoker.setItem(1, ItemStack.EMPTY);
            rule.cookMake(cookBe, cm);
            helper.assertTrue(smoker.getItem(1).is(Items.COAL) && input.getStackInSlot(3).isEmpty(),
                    "native fuel refill must transfer actual material through the manager");
            smoker.setItem(1, new ItemStack(Items.BUCKET)); rule.cookMake(cookBe, cm);
            helper.assertTrue(smoker.getItem(1).isEmpty() && CookInventoryTransactions.count(input, stack -> stack.is(Items.BUCKET)) == 1,
                    "a returned fuel container must be reclaimed while valid ingredients remain");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void commonMoveMakeOwnsTargetsAndLocks(GameTestHelper helper) {
        for (int x = 0; x < 7; x++) for (int z = 0; z < 7; z++)
            helper.setBlock(x, 0, z, net.minecraft.world.level.block.Blocks.STONE);
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        var other = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 2));
        maid.setNoAi(true); other.setNoAi(true);
        try {
            var center = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3));
            maid.getSchedulePos().setHomeModeEnable(maid, center); maid.getSchedulePos().setConfigured(true);
            maid.setHomeModeEnable(true); maid.restrictTo(center, 8); maid.setOnGround(true);
            var task = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.TaskFdCookingPot(); maid.setTask(task);
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            for (var pos : List.of(new net.minecraft.core.BlockPos(4, 2, 4), new net.minecraft.core.BlockPos(6, 2, 4))) {
                helper.setBlock(pos, vectorwing.farmersdelight.common.registry.ModBlocks.COOKING_POT.get().defaultBlockState());
                var pot = (vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity) helper.getBlockEntity(pos);
                pot.getInventory().setStackInSlot(pot.OUTPUT_SLOT, new ItemStack(Items.COOKED_BEEF));
            }
            var cm = task.getRecipesManager(maid); cm.checkAndInit();
            var cookBe = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.CookingPotBe(maid);
            var rule = com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.FdPotCookRule.<vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity, vectorwing.farmersdelight.common.crafting.CookingPotRecipe>getInstance();
            var move = new com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookMoveTask<vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity, vectorwing.farmersdelight.common.crafting.CookingPotRecipe>(task, cm, rule, cookBe) {
                public void runSearch() { start(helper.getLevel(), maid, helper.getLevel().getGameTime()); }
            };
            move.runSearch();
            var first = com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.getWorkPos(maid).orElseThrow().currentBlockPosition();
            var walk = maid.getBrain().getMemory(com.github.wallev.maidsoulkitchen.init.MkMemories.COOK_WALK_POS.get()).orElseThrow().currentBlockPosition();
            helper.assertTrue(!first.equals(walk) && cookBe.getPos().equals(first)
                    && !com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks.tryClaim(helper.getLevel(), first, other),
                    "common single-BFS Move must bind the selected real device and reserve it separately from its walk position");
            com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.clear(maid);
            move.runSearch();
            var second = com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.getWorkPos(maid).orElseThrow().currentBlockPosition();
            helper.assertTrue(!first.equals(second), "common Move must retain validated multi-device rotation");
            walk = maid.getBrain().getMemory(com.github.wallev.maidsoulkitchen.init.MkMemories.COOK_WALK_POS.get()).orElseThrow().currentBlockPosition();
            maid.moveTo(walk.getX() + 0.5, walk.getY(), walk.getZ() + 0.5);
            var make = new com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookMakeTask<>(task, cm, rule, cookBe);
            helper.assertTrue(make.tryStart(helper.getLevel(), maid, helper.getLevel().getGameTime()), "common Make must start at the reachable side");
            make.tickOrStop(helper.getLevel(), maid, helper.getLevel().getGameTime() + 1);
            helper.assertTrue(!com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.hasCookingAssignment(maid)
                    && cookBe.getBe() == null
                    && com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookWorkLocks.isAvailable(helper.getLevel(), second, other)
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.COOKED_BEEF)) == 1,
                    "common Make stop must release Be/memories/claim after the actual output transfer");
            move.runSearch();
            var selected = cookBe.getBe();
            helper.getLevel().destroyBlock(selected.getBlockPos(), false);
            helper.getLevel().setBlock(selected.getBlockPos(), vectorwing.farmersdelight.common.registry.ModBlocks.COOKING_POT.get().defaultBlockState(), 3);
            walk = maid.getBrain().getMemory(com.github.wallev.maidsoulkitchen.init.MkMemories.COOK_WALK_POS.get()).orElseThrow().currentBlockPosition();
            maid.moveTo(walk.getX() + 0.5, walk.getY(), walk.getZ() + 0.5);
            helper.assertTrue(!make.tryStart(helper.getLevel(), maid, helper.getLevel().getGameTime() + 2)
                    && !com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.hasCookingAssignment(maid),
                    "replacement at the same coordinate must revoke the old selected block entity");
        } finally {
            com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.clear(maid);
            maid.discard(); other.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void devicePartialAcceptanceRollsBack(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var original = List.copyOf(recipes.getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace.TaskFurnace(); maid.setTask(task);
            maid.restrictTo(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)), 4);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.SMOKER);
            var holder = new RecipeHolder<>(id("device_partial_fixture"), new SmokingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 20));
            reloadRecipes(helper, List.of(holder));
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            input.setStackInSlot(0, new ItemStack(Items.CARROT, 5));
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            var rec = cm.peekMaidRec();
            var partial = new ItemStackHandler(1) {
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (simulate) return super.insertItem(slot, stack, true);
                    ItemStack remainder = super.insertItem(slot, stack.copyWithCount(Math.min(2, stack.getCount())), false);
                    return stack.copyWithCount(stack.getCount() - Math.min(2, stack.getCount()) + remainder.getCount());
                }
            };
            helper.assertTrue(rec != null && !cm.insertInputs(rec, partial, 0, 1)
                    && partial.getStackInSlot(0).isEmpty() && cm.getMaidRecs().isEmpty()
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.CARROT)) == 5,
                    "real partial device acceptance must roll back actual inserts and revoke the unfulfilled plan");
            cm.checkAndCreateRecipes(); finishPlanning(cm);
            var refused = new ItemStackHandler(1) {
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    return simulate ? super.insertItem(slot, stack, true) : stack.copy();
                }
            };
            helper.assertTrue(!cm.insertInputs(cm.peekMaidRec(), refused, 0, 1) && refused.getStackInSlot(0).isEmpty()
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.CARROT)) == 5,
                    "real refusal after simulation must conserve all inputs");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeCookingPotAcceptsUnifiedWork(GameTestHelper helper) {
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.TaskFdCookingPot();
            maid.setTask(task);
            var description = task.getRecSerializerManager().getRecipes(helper.getLevel()).stream()
                    .filter(recipe -> !recipe.inItems().isEmpty() && recipe.inItems().size() <= 6
                            && recipe.inItems().stream().allMatch(ingredient -> ingredient.ingredient.getItems().length > 0))
                    .findFirst().orElseThrow();
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(description.idStr()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            for (int slot = 0; slot < description.inItems().size(); slot++) {
                var ingredient = description.inItems().get(slot);
                input.setStackInSlot(slot, ingredient.ingredient.getItems()[0].copyWithCount(ingredient.test(ingredient.ingredient.getItems()[0])));
            }
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), vectorwing.farmersdelight.common.registry.ModBlocks.COOKING_POT.get().defaultBlockState());
            var pot = (vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var cookBe = new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.CookingPotBe(maid); cookBe.setBe(pot);
            var rec = cm.peekMaidRec();
            helper.assertTrue(rec != null && cookBe.insertInputs(rec, cm) && cm.getMaidRecs().size() == 1
                    && cookBe.recMatch() && cm.commitMaidRec(rec) && !cm.commitMaidRec(rec),
                    "native FD Handler must accept the selected Holder before exactly one work unit commits");
            ItemStack result = description.output().copyWithCount(5);
            result.set(DataComponents.CUSTOM_NAME, Component.literal("component output"));
            pot.getInventory().setStackInSlot(pot.OUTPUT_SLOT, result.copy());
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
            helper.assertTrue(!cookBe.extractResult(cm) && pot.getInventory().getStackInSlot(pot.OUTPUT_SLOT).getCount() == 5,
                    "full output must leave native device contents intact");
            input.setStackInSlot(0, result.copyWithCount(result.getMaxStackSize() - 1));
            helper.assertTrue(cookBe.extractResult(cm) && pot.getInventory().getStackInSlot(pot.OUTPUT_SLOT).getCount() == 4
                    && input.getStackInSlot(0).getCount() == result.getMaxStackSize()
                    && ItemStack.isSameItemSameComponents(input.getStackInSlot(0), result),
                    "partial output acceptance must remove only actual accepted component-identical output");
        } finally { maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void nativeBeerBarrelRetainsNativeFixes(GameTestHelper helper) {
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel.TaskDbBeerBarrel(); maid.setTask(task);
            var description = task.getRecSerializerManager().getRecipes(helper.getLevel()).stream()
                    .filter(recipe -> recipe.inItems().size() == 5
                            && recipe.inItems().stream().allMatch(ingredient -> ingredient.ingredient.getItems().length > 0))
                    .findFirst().orElseThrow();
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(description.idStr()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            for (int slot = 0; slot < description.inItems().size(); slot++) {
                var ingredient = description.inItems().get(slot);
                ItemStack sample = ingredient.ingredient.getItems()[0];
                input.setStackInSlot(slot, sample.copyWithCount(ingredient.test(sample)));
            }
            var cm = task.getRecipesManager(maid); cm.checkAndCreateRecipes(); finishPlanning(cm);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), lekavar.lma.drinkbeer.registries.BlockRegistry.BEER_BARREL.get().defaultBlockState());
            var barrel = (lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity) helper.getBlockEntity(new net.minecraft.core.BlockPos(2, 1, 2));
            var cookBe = new com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel.BeerBarrelBe(maid); cookBe.setBe(barrel);
            var rec = cm.peekMaidRec();
            helper.assertTrue(rec != null && cookBe.insertInputs(rec, cm) && cm.commitMaidRec(rec)
                    && barrel.getBrewingInventory().getItem(4).getCount() == description.rec().getRequiredCupCount(),
                    "native beer inputs and all required cups must accept one unified work unit");
            // The confirmed partial-cup repair is a device action, not a second plan or manager.
            barrel.getBrewingInventory().setItem(4, new ItemStack(lekavar.lma.drinkbeer.registries.ItemRegistry.EMPTY_BEER_MUG.get(), 2));
            input.setStackInSlot(0, new ItemStack(lekavar.lma.drinkbeer.registries.ItemRegistry.EMPTY_BEER_MUG.get(), 2));
            new com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel.BeerBarrelCookRule().cookMake(cookBe, cm);
            helper.assertTrue(barrel.getBrewingInventory().getItem(4).getCount() == 4 && input.getStackInSlot(0).isEmpty(),
                    "existing native ingredients must receive only missing cups: cup=" + barrel.getBrewingInventory().getItem(4) + ", source=" + input.getStackInSlot(0) + ", modifiable=" + barrel.canModifyInputs() + ", matching=" + cookBe.recMatch());
            barrel.getBrewingInventory().setItem(0, new ItemStack(Items.BUCKET));
            helper.assertTrue(cookBe.takeReturnedBuckets(cm) && barrel.getBrewingInventory().getItem(0).isEmpty()
                    && CookInventoryTransactions.count(input, stack -> stack.is(Items.BUCKET)) == 1,
                    "native returned buckets must move through the owned physical inventory exactly once");
        } finally { maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void managerOwnsQueueAndInvalidation(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var original = List.copyOf(recipes.getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace.TaskFurnace(); maid.setTask(task);
            maid.restrictTo(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)), 4);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.SMOKER);
            ItemStack named = new ItemStack(Items.WATER_BUCKET);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("managed material"));
            var holder = new RecipeHolder<>(id("managed_fixture"), new SmokingRecipe("", CookingBookCategory.MISC,
                    DataComponentIngredient.of(true, named), new ItemStack(Items.BAKED_POTATO), 0, 20));
            reloadRecipes(helper, List.of(holder));
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            var input = maid.getAvailableInv(true);
            for (int slot = 0; slot < input.getSlots(); slot++) input.setStackInSlot(slot, ItemStack.EMPTY);
            input.setStackInSlot(0, named.copy()); input.setStackInSlot(1, named.copy());
            var manager = task.getRecipesManager(maid);
            manager.checkAndCreateRecipes();
            helper.assertTrue(manager.getRunState() == 2 && manager.getMaidRecs().isEmpty(),
                    "candidates must not be exposed as executable work while generation is active");
            finishPlanning(manager);
            helper.assertTrue(manager.getMaidRecs().size() == 2
                    && manager.getMaidRecs().get(0) != manager.getMaidRecs().get(1),
                    "repeated work units must have distinct identities for safe acknowledgments");
            MaidRec first = manager.peekMaidRec();
            manager.peekMaidRec().maidItems(); manager.peekMaidRec().maidItems();
            helper.assertTrue(manager.peekMaidRec() == first && manager.getMaidRecs().size() == 2,
                    "reading materials must not consume the plan before device acceptance");
            var device = new ItemStackHandler(1);
            var accepted = CookInventoryTransactions.transfer(manager.getInputInv(), 0, device, 1, first.maidItems().getFirst().item()::is);
            helper.assertTrue(accepted.inserted() == 1 && manager.commitMaidRec(first)
                    && !manager.commitMaidRec(first) && manager.getMaidRecs().size() == 1,
                    "one accepted work unit may commit once; an old acknowledgment cannot consume the next unit");
            manager.getInputInv().setStackInSlot(1, new ItemStack(Items.WATER_BUCKET));
            long generation = manager.getGeneration(); manager.checkAndInit();
            helper.assertTrue(manager.getMaidRecs().isEmpty() && manager.getGeneration() > generation,
                    "component changes in live inputs must invalidate pending work");
            manager.getInputInv().setStackInSlot(1, named.copy()); manager.checkAndCreateRecipes(); finishPlanning(manager);
            MaidRec beforeReload = manager.peekMaidRec();
            reloadRecipes(helper, List.of(new RecipeHolder<>(holder.id(), new SmokingRecipe("", CookingBookCategory.MISC,
                    DataComponentIngredient.of(true, named), new ItemStack(Items.BAKED_POTATO), 0, 20))));
            manager.checkAndInit();
            helper.assertTrue(beforeReload != null && manager.getMaidRecs().isEmpty() && !manager.commitMaidRec(beforeReload),
                    "same-ID recipe reload must discard the old work identity");
            manager.checkAndCreateRecipes(); finishPlanning(manager);
            if (com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo.FD_COOK_POT.canLoad()) {
                maid.setTask(new com.github.wallev.maidsoulkitchen.task.cook.farmersdelight.cookingpot.TaskFdCookingPot());
                helper.assertTrue(!manager.checkAndInit() && manager.getMaidRecs().isEmpty(),
                        "switching tasks must revoke the previous manager's work");
                maid.setTask(task);
            }
            manager.checkAndCreateRecipes(); finishPlanning(manager);
            maid.discard();
            helper.assertTrue(!manager.checkAndInit() && manager.getMaidRecs().isEmpty(), "death/removal must revoke pending work");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
    public static void managerPartialChestExtraction(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var original = List.copyOf(recipes.getRecipes());
        var maid = helper.spawnWithNoFreeWill(InitEntities.MAID.get(), new net.minecraft.core.BlockPos(1, 1, 1));
        maid.setNoAi(true);
        try {
            var task = new com.github.wallev.maidsoulkitchen.task.cook.minecraft.furnace.TaskFurnace(); maid.setTask(task);
            maid.restrictTo(helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)), 4);
            helper.setBlock(new net.minecraft.core.BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.SMOKER);
            var holder = new RecipeHolder<>(id("partial_fixture"), new SmokingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.CARROT), new ItemStack(Items.BAKED_POTATO), 0, 20));
            reloadRecipes(helper, List.of(holder));
            KitchenData.get(maid).setCookData(task.getUid(), new CookData("whitelist", List.of(holder.id().toString()), List.of()));
            for (int slot = 0; slot < maid.getAvailableInv(true).getSlots(); slot++) maid.getAvailableInv(true).setStackInSlot(slot, ItemStack.EMPTY);
            ItemStack hub = com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance();
            maid.getMaidBauble().setStackInSlot(0, hub);
            var source = new ItemStackHandler(1) {
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    return super.extractItem(slot, simulate ? amount : Math.min(2, amount), simulate);
                }
            };
            source.setStackInSlot(0, new ItemStack(Items.CARROT, 5));
            var manager = new com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<>(task.getRecSerializerManager(), maid, task) {
                @Override protected List<net.minecraft.world.level.block.entity.BlockEntity> initChestData() {
                    getChestInputInventory().init(new com.github.wallev.maidsoulkitchen.task.cook.common.inv.chest.ChestInvsData(
                            List.of(), List.of(), List.of(source), 1));
                    return List.of();
                }
            };
            manager.checkAndCreateRecipes(); finishPlanning(manager);
            helper.assertTrue(manager.getMaidRecs().isEmpty() && source.getStackInSlot(0).getCount() == 3
                    && CookInventoryTransactions.count(manager.getInputInv(), stack -> stack.is(Items.CARROT)) == 2,
                    "partial extraction must keep actual buffered material and expose no unfulfilled work");
            manager.checkAndCreateRecipes(); finishPlanning(manager);
            helper.assertTrue(manager.getMaidRecs().isEmpty() && source.getStackInSlot(0).getCount() == 1
                    && CookInventoryTransactions.count(manager.getInputInv(), stack -> stack.is(Items.CARROT)) == 4,
                    "replanning must count buffered input once and request only the remaining deficit");
            manager.checkAndCreateRecipes(); finishPlanning(manager);
            helper.assertTrue(manager.getMaidRecs().size() == 1 && manager.peekMaidRec().amount() == 5
                    && source.getStackInSlot(0).isEmpty()
                    && CookInventoryTransactions.count(manager.getInputInv(), stack -> stack.is(Items.CARROT)) == 5,
                    "only a fully prepared plan may enter the executable queue without item loss or duplication");
        } finally { reloadRecipes(helper, original); maid.discard(); }
        helper.succeed();
    }

    private static void finishPlanning(com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<?> manager) {
        for (int ticks = 0; ticks < 100 && manager.getRunState() > 0; ticks++) {
            if (manager.getRunState() == 1) {
                manager.getChestInputInventory().tickScan();
                if (manager.getChestInputInventory().done()) manager.startGenerateRecs();
            } else if (manager.getRunState() == 2) {
                if (!manager.recsGenerateDone()) manager.tickGenerateRecs();
                if (manager.recsGenerateDone()) manager.recsGenDoneAndUpdate();
            }
        }
        if (manager.getRunState() != 0) throw new IllegalStateException("bounded planning did not finish");
    }

    private static void reloadRecipes(GameTestHelper helper, List<? extends RecipeHolder<?>> recipes) {
        helper.getLevel().getRecipeManager().replaceRecipes(new java.util.ArrayList<RecipeHolder<?>>(recipes));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.OnDatapackSyncEvent(
                helper.getLevel().getServer().getPlayerList(), null));
    }

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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

    @GameTest(template = "stockpot_empty", templateNamespace = MaidsoulKitchen.MOD_ID, batch = "cooking_architecture")
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
        } finally { manager.replaceRecipes(original); }
        helper.succeed();
    }
}


