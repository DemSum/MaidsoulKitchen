package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrinkBeerCompatibilityResourcesTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final List<String> OVERRIDDEN_RECIPES = List.of(
            "beer_mug",
            "beer_mug_apple_lambic",
            "beer_mug_blaze_milk_stout",
            "beer_mug_blaze_stout",
            "beer_mug_frothy_pink_eggnog",
            "beer_mug_night_howl_kvass",
            "beer_mug_pumpkin_kvass",
            "beer_mug_sweet_berry_kriek"
    );

    @Test
    void drinkBeerOverridesUseNativeMskTagsAndFourCups() throws IOException {
        for (String recipeName : OVERRIDDEN_RECIPES) {
            JsonObject recipe = readJson(RESOURCES.resolve(
                    "data/drinkbeer/recipe/" + recipeName + ".json"));
            assertEquals("drinkbeer:brewing", recipe.get("type").getAsString());
            assertEquals(4, recipe.getAsJsonObject("cup").get("count").getAsInt());
            assertEquals(4, recipe.getAsJsonObject("result").get("count").getAsInt());
            assertFalse(recipe.toString().contains("maidsoul_brewery:"));
        }
    }

    @Test
    void compatibilityTagsAndAltarRecipesArePresentAndValid() throws IOException {
        for (String tagName : List.of(
                "drinkbeer_water_inputs",
                "drinkbeer_milk_inputs",
                "drinkbeer_wheat_inputs"
        )) {
            JsonObject tag = readJson(RESOURCES.resolve(
                    "data/maidsoulkitchen/tags/item/" + tagName + ".json"));
            assertTrue(tag.has("values"));
        }

        assertAltarResult("burn_protect_bauble", "maidsoulkitchen:burn_protect_bauble");
        assertAltarResult("culinary_hub", "maidsoulkitchen:culinary_hub");
    }

    private static void assertAltarResult(String recipeName, String expectedItem) throws IOException {
        JsonObject recipe = readJson(RESOURCES.resolve(
                "data/maidsoulkitchen/recipe/altar_recipe/" + recipeName + ".json"));
        assertEquals(
                "touhou_little_maid:altar_recipe_serializers",
                recipe.get("type").getAsString()
        );
        assertEquals(expectedItem, recipe.getAsJsonObject("result").get("id").getAsString());
    }

    private static JsonObject readJson(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), () -> "Missing resource: " + path);
        JsonElement json = new JsonParser().parse(Files.readString(path));
        assertTrue(json.isJsonObject(), () -> "Expected object: " + path);
        return json.getAsJsonObject();
    }
}
