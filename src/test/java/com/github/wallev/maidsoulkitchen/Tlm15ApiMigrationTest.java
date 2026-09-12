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
    void steamerUsesTlmPathFindingApiWithoutLegacyReflection() throws IOException {
        String search = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/kaleidoscopecookery/"
                        + "SteamerApproachSearch.class"
        );
        assertTrue(search.contains("MaidPathFindingBFS"));
        assertFalse(search.contains("java/lang/reflect"));
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
