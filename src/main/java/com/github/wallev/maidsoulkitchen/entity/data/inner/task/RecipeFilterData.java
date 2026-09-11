package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * Persistent recipe filter used by appliance tasks that do not implement the
 * generic {@code ICookTask} recipe manager contract.
 *
 * <p>Originally prototyped in Maidsoul-Brewery-Public commit de47e15.</p>
 */
public record RecipeFilterData(Mode mode, List<ResourceLocation> whitelist, List<ResourceLocation> blacklist) {
    public static final RecipeFilterData DEFAULT = new RecipeFilterData(Mode.BLACKLIST, List.of(), List.of());
    public static final Codec<RecipeFilterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Mode.CODEC.fieldOf("mode").forGetter(RecipeFilterData::mode),
            ResourceLocation.CODEC.listOf().optionalFieldOf("whitelist", List.of())
                    .forGetter(RecipeFilterData::whitelist),
            ResourceLocation.CODEC.listOf().optionalFieldOf("blacklist", List.of())
                    .forGetter(RecipeFilterData::blacklist)
    ).apply(instance, RecipeFilterData::new));

    public RecipeFilterData {
        whitelist = List.copyOf(whitelist);
        blacklist = List.copyOf(blacklist);
    }

    public boolean allows(ResourceLocation recipeId) {
        return RecipeFilterRules.allows(
                mode == Mode.WHITELIST,
                selectedRecipes(),
                recipeId
        );
    }

    public boolean contains(ResourceLocation recipeId) {
        return selectedRecipes().contains(recipeId);
    }

    public List<ResourceLocation> selectedRecipes() {
        return mode == Mode.WHITELIST ? whitelist : blacklist;
    }

    public RecipeFilterData withMode(Mode newMode) {
        return new RecipeFilterData(newMode, whitelist, blacklist);
    }

    public RecipeFilterData toggle(ResourceLocation recipeId) {
        List<ResourceLocation> changed = RecipeFilterRules.toggle(
                selectedRecipes(), recipeId, ResourceLocation::compareTo);
        return mode == Mode.WHITELIST
                ? new RecipeFilterData(mode, changed, blacklist)
                : new RecipeFilterData(mode, whitelist, changed);
    }

    public enum Mode {
        WHITELIST,
        BLACKLIST;

        private static final Codec<Mode> CODEC = Codec.STRING.xmap(Mode::fromName, Mode::serializedName);

        public Mode next() {
            return this == WHITELIST ? BLACKLIST : WHITELIST;
        }

        private String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static Mode fromName(String name) {
            return "whitelist".equalsIgnoreCase(name) ? WHITELIST : BLACKLIST;
        }
    }
}
