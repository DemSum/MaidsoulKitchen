package com.github.wallev.maidsoulkitchen.modclazzchecker.manager;

import com.github.wallev.maidsoulkitchen.foundation.utility.Mods;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityRegistryTest {
    @Test
    void manifestCoversEveryMaintainedCompatibilityIdentity() throws IOException {
        CompatibilityRegistry.Manifest manifest = readManifest();
        assertEquals("[1.4.1,2)", Mods.DB.versionRange());
        Set<String> expectedTasks = Arrays.stream(TaskInfo.VALUES)
                .filter(task -> task != TaskInfo.NONE)
                .map(TaskInfo::getUidStr)
                .collect(Collectors.toSet());

        assertEquals(23, manifest.tasks().size());
        assertEquals(expectedTasks, manifest.tasks().keySet());
        for (TaskInfo task : TaskInfo.VALUES) {
            if (task != TaskInfo.NONE) {
                assertEquals(task.getBindMod(), manifest.tasks().get(task.getUidStr()).bindMod(),
                        task.getUidStr());
            }
        }
    }

    @Test
    void mixinGatesMatchTheCuratedCompatibilityTargets() throws IOException {
        CompatibilityRegistry.Manifest manifest = readManifest();
        Map<String, java.util.List<Mods>> gates = manifest.mixinRequirements();

        assertEquals(5, gates.size());
        assertEquals(java.util.List.of(Mods.BNCD),
                gates.get("umpaz.brewinandchewin.common.block.entity.KegBlockEntity"));
        assertEquals(java.util.List.of(Mods.FD),
                gates.get("vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity"));
        assertEquals(java.util.List.of(Mods.MC),
                gates.get("net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity"));
        assertEquals(java.util.List.of(Mods.YHCD),
                gates.get("dev.xkmc.youkaishomecoming.content.pot.base.BasePotBlockEntity"));
        assertEquals(java.util.List.of(Mods.YHCD),
                gates.get("dev.xkmc.youkaishomecoming.content.pot.kettle.KettleBlock"));

        assertTrue(manifest.mixinsByTask().keySet().stream().allMatch(manifest.tasks()::containsKey));
    }

    @Test
    void externalCompatibilityRequirementsNeverListOurInjectedMethods() throws IOException {
        CompatibilityRegistry.Manifest manifest = readManifest();
        assertTrue(manifest.tasks().entrySet().stream()
                        .flatMap(entry -> entry.getValue().methods().stream()
                                .map(method -> Map.entry(entry.getKey(), method)))
                        .noneMatch(entry -> entry.getValue().contains("#tlmk$")),
                "Mixin-injected tlmk$ methods must be validated through mixin markers, not as upstream API");
    }

    private static CompatibilityRegistry.Manifest readManifest() throws IOException {
        try (InputStream stream = CompatibilityRegistryTest.class.getClassLoader()
                .getResourceAsStream(CompatibilityRegistry.MANIFEST_FILE)) {
            assertNotNull(stream, CompatibilityRegistry.MANIFEST_FILE);
            return CompatibilityRegistry.parseManifest(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
