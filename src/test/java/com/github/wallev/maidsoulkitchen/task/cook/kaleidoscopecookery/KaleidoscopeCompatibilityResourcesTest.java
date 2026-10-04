package com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KaleidoscopeCompatibilityResourcesTest {
    private static final Path PROJECT = Path.of("");

    @Test
    void declaresSupportedKaleidoscopeVersionAsOptional() throws IOException {
        String metadata = Files.readString(PROJECT.resolve("src/main/resources/META-INF/neoforge.mods.toml"));

        assertTrue(metadata.contains("modId=\"kaleidoscope_cookery\""));
        assertTrue(metadata.contains("type=\"optional\""));
        assertTrue(metadata.contains("versionRange=\"[1.4.1,2)\""));
    }

    @Test
    void providesNativeTaskTranslations() throws IOException {
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            Path path = PROJECT.resolve("src/main/resources/assets/maidsoulkitchen/lang/" + locale + ".json");
            JsonObject language = new JsonParser().parse(Files.readString(path)).getAsJsonObject();
            assertTrue(language.has("task.maidsoulkitchen.kaleidoscope_steamer"));
            assertTrue(language.has("task.maidsoulkitchen.kaleidoscope_steamer.desc"));
            assertTrue(language.has("task.maidsoulkitchen.kaleidoscope_stockpot"));
            assertTrue(language.has("task.maidsoulkitchen.kaleidoscope_stockpot.desc.lid"));
            assertTrue(language.has("gui.maidsoulkitchen.stockpot.flex.warning"));
            assertTrue(language.has("config.maidsoulkitchen.task.experimental"));
            assertTrue(language.has("config.maidsoulkitchen.task.experimental.tooltip"));
            assertTrue(language.has("config.maidsoulkitchen.register.kc_stockpot"));
        }
    }

    @Test
    void registersTheSteamerWithTheUpstreamCompatibilityChecker() throws IOException {
        JsonObject root = new JsonParser().parse(Files.readString(
                PROJECT.resolve("src/main/resources/mod_task_clazz.json"))).getAsJsonObject();
        JsonObject entry = root.getAsJsonObject("clazzInfoMap")
                .getAsJsonObject("maidsoulkitchen:kaleidoscope_cookery_steamer_steaming");
        JsonObject api = entry.getAsJsonObject("clazzInfo");

        assertEquals("KC", entry.get("bindMod").getAsString());
        assertTrue(api.getAsJsonArray("classes").toString().contains("SteamerBlockEntity"));
        assertTrue(api.getAsJsonArray("methods").toString().contains("#placeFood"));
        assertTrue(api.getAsJsonArray("methods").toString().contains("#takeFood"));
        assertTrue(api.getAsJsonArray("fields").toString().contains("#MAX_LIT_LEVEL"));
        assertTrue(api.getAsJsonArray("fields").toString().contains("#STEAMER_RECIPE"));
    }

    @Test
    void stockpotRequiresBothRecipeKindsAndTheRealSoupPredicateApi() throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(
                PROJECT.resolve("src/main/resources/mod_task_clazz.json"))).getAsJsonObject();
        JsonObject entry = root.getAsJsonObject("clazzInfoMap")
                .getAsJsonObject("maidsoulkitchen:kaleidoscope_cookery_stockpot_cooking");
        JsonObject api = entry.getAsJsonObject("clazzInfo");
        assertEquals("KC", entry.get("bindMod").getAsString());
        assertTrue(api.getAsJsonArray("classes").toString().contains("FlexStockpotRecipe"));
        assertTrue(api.getAsJsonArray("methods").toString().contains("#isSoupBase"));
        for (String method : new String[]{"#addSoupBase", "#addIngredient", "#removeIngredient", "#onLitClick", "#takeOutProduct"}) {
            assertTrue(api.getAsJsonArray("methods").toString().contains(method), method);
        }
        assertTrue(api.getAsJsonArray("fields").toString().contains("#FLEX_STOCKPOT_RECIPE"));
        String data = Files.readString(PROJECT.resolve(
                "src/main/java/com/github/wallev/maidsoulkitchen/entity/data/inner/task/StockpotTaskData.java"));
        assertTrue(data.contains("RecipeFilterData.DEFAULT, false"));
        assertTrue(data.contains("optionalFieldOf(\"allow_flex_recipes\", false)"));
    }

    @Test
    void keepsTheTlmRecipeSlotHighlightMixinRegistered() throws IOException {
        JsonObject mixins = new JsonParser().parse(Files.readString(
                PROJECT.resolve("src/main/resources/maidsoulkitchen-compat.mixins.json"))).getAsJsonObject();

        assertTrue(mixins.getAsJsonArray("client").toString()
                .contains("touhoulittlemaid.AbstractMaidContainerGuiMixin"));
    }

    @Test
    void pinsTheReviewedKaleidoscopeArtifactAndMskVersion() throws IOException {
        String versionProperties = Files.readString(PROJECT.resolve("setting/version/1.21.1/gradle.properties"));
        String modProperties = Files.readString(PROJECT.resolve("gradle.properties"));

        assertTrue(versionProperties.contains("kaleidoscope_cookery_version=TqaHu4Ma"));
        assertEquals("0.2.0", property(modProperties, "mod_version"));
    }

    private static String property(String content, String key) {
        return content.lines()
                .filter(line -> line.startsWith(key + "="))
                .map(line -> line.substring(key.length() + 1))
                .findFirst()
                .orElseThrow();
    }
}
