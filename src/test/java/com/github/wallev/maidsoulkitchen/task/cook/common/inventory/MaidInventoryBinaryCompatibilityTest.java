package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaidInventoryBinaryCompatibilityTest {
    private static final String UNSTABLE_METHOD = "getAvailableBackpackInv";
    private static final List<String> STORAGE_CALLERS = List.of(
            "com/github/wallev/maidsoulkitchen/task/cook/kaleidoscopecookery/SteamerWorkStorage.class",
            "com/github/wallev/maidsoulkitchen/task/cook/common/inventory/CulinaryHubWorkStorage.class"
    );

    @Test
    void storageCallersDoNotLinkTheVersionSpecificTlmDescriptor() throws IOException {
        for (String resource : STORAGE_CALLERS) {
            assertFalse(classFileText(resource).contains(UNSTABLE_METHOD),
                    () -> resource + " directly links TLM's version-specific inventory method");
        }
    }

    @Test
    void compatibilityLayerKeepsTheReflectiveMethodName() throws IOException {
        String resource = "com/github/wallev/maidsoulkitchen/task/cook/common/inventory/MaidInventoryCompat.class";
        assertTrue(classFileText(resource).contains(UNSTABLE_METHOD));
    }

    private static String classFileText(String resource) throws IOException {
        try (InputStream stream = MaidInventoryBinaryCompatibilityTest.class
                .getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing compiled class resource: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
