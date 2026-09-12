package com.github.wallev.maidsoulkitchen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tlm15ApiMigrationTest {
    @Test
    void requiresTlm153AtRuntime() throws IOException {
        String metadata = textResource("META-INF/neoforge.mods.toml");
        assertTrue(metadata.contains("versionRange=\"[1.5.3,)\""));
        assertFalse(metadata.contains("versionRange=\"[1.1.13,)\""));
    }

    @Test
    void extensionUsesTheOfficialSpecialCropCallback() throws IOException {
        String extension = classFileText(
                "com/github/wallev/maidsoulkitchen/MaidPlugin.class"
        );
        assertTrue(extension.contains("registerSpecialCropHandler"));
        assertTrue(extension.contains("SpecialCropManager"));
    }

    @Test
    void allCookingSearchesUseOneTlmPathFindingAdapterWithoutLegacyReflection() throws IOException {
        String adapter = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/common/ai/CookPathSearch.class"
        );
        String genericSearch = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/common/ai/ReachableCookDeviceSearch.class"
        );
        String steamerSearch = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/kaleidoscopecookery/"
                        + "SteamerApproachSearch.class"
        );
        assertTrue(adapter.contains("MaidPathFindingBFS"));
        assertTrue(adapter.contains("finish"));
        assertFalse(adapter.contains("java/lang/reflect"));
        assertTrue(genericSearch.contains("CookPathSearch"));
        assertFalse(genericSearch.contains("NodeEvaluator"));
        assertTrue(steamerSearch.contains("CookPathSearch"));
        assertNull(getClass().getClassLoader().getResource(
                "com/github/wallev/maidsoulkitchen/task/cook/common/ai/"
                        + "CookSearchDiagnostics.class"
        ));
    }

    @Test
    void cookingAssignmentsCarryTaskOwnershipAndDeviceClaims() throws IOException {
        String memory = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/common/ai/CookTargetMemory.class"
        );
        String memories = classFileText(
                "com/github/wallev/maidsoulkitchen/init/MkMemories.class"
        );
        String targetTask = classFileText(
                "com/github/wallev/maidsoulkitchen/api/task/v1/cook/ICookTargetTask.class"
        );
        String genericMove = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/common/ai/MaidCookMoveTask.class"
        );
        String steamerMove = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/kaleidoscopecookery/"
                        + "MaidSteamerMoveTask.class"
        );
        assertTrue(memory.contains("COOK_TASK_UID"));
        assertTrue(memory.contains("CookWorkLocks"));
        assertTrue(memories.contains("cook_task_uid"));
        assertTrue(targetTask.contains("enableLookAndRandomWalk"));
        assertTrue(targetTask.contains("enableEating"));
        assertTrue(genericMove.contains("guideBackToWorkArea"));
        assertTrue(steamerMove.contains("CookTargetCycle"));
        assertTrue(steamerMove.contains("isAvailable"));
        assertTrue(steamerMove.contains("guideBackToWorkArea"));
    }

    @Test
    void cookingConfigPacketsValidateCurrentTaskAndRecipes() throws IOException {
        String modePacket = classFileText(
                "com/github/wallev/maidsoulkitchen/network/message/SetCookDataC2SPackage.class"
        );
        String recipePacket = classFileText(
                "com/github/wallev/maidsoulkitchen/network/message/ActionCookDataRecC2SPackage.class"
        );
        assertTrue(modePacket.contains("ICookTask"));
        assertTrue(modePacket.contains("isValidMode"));
        assertTrue(recipePacket.contains("getRecipeHolders"));
        assertTrue(recipePacket.contains("tryParse"));
    }

    @Test
    void legacyTaskTransactionsUseSharedCapacityChecks() throws IOException {
        String transactions = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/common/inventory/CookInventoryTransactions.class"
        );
        String furnace = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/minecraft/TaskFurnace.class"
        );
        String basin = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/barbequesdelight/MaidBasinMakeTask.class"
        );
        assertTrue(transactions.contains("canInsertAll"));
        assertTrue(transactions.contains("returnOrDrop"));
        assertTrue(furnace.contains("SMOKING"));
        assertTrue(furnace.contains("BLASTING"));
        assertTrue(basin.contains("completed"));
    }

    @Test
    void berryTaskOverridesTheSharedBfsReachabilityHook() throws IOException {
        String moveTask = classFileText(
                "com/github/wallev/maidsoulkitchen/task/farm/TaskBerryFarm$1.class"
        );
        assertTrue(moveTask.contains("MaidPathFindingBFS"));
        assertTrue(moveTask.contains("canPathReach"));
    }

    @Test
    void oldInventoryCompatibilityShimIsAbsent() {
        assertNull(getClass().getClassLoader().getResource(
                "com/github/wallev/maidsoulkitchen/task/cook/common/inventory/"
                        + "MaidInventoryCompat.class"
        ));
    }

    private static String textResource(String resource) throws IOException {
        try (InputStream stream = Tlm15ApiMigrationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing resource: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String classFileText(String resource) throws IOException {
        try (InputStream stream = Tlm15ApiMigrationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing compiled class resource: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
