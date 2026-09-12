package com.github.wallev.maidsoulkitchen.task.cook.common.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookingSearchSchedulingTest {
    @Test
    void idleWalkTargetDoesNotGateGenericCookDiscovery() throws IOException {
        String moveTask = classFileText(
                "com/github/wallev/maidsoulkitchen/task/cook/common/ai/MaidCookMoveTask.class"
        );

        assertTrue(moveTask.contains("WORK_POS"));
        assertFalse(moveTask.contains("WALK_TARGET"));
    }

    private static String classFileText(String resource) throws IOException {
        try (InputStream stream = CookingSearchSchedulingTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(stream, () -> "Missing compiled class resource: " + resource);
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
