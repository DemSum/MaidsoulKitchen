package com.github.wallev.maidsoulkitchen.entity.data.inner.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Independent filter; legacy Flex field retained as a server menu snapshot, never an opt-in permission. */
public record StockpotTaskData(RecipeFilterData filter, boolean allowFlexRecipes) {
    public static final StockpotTaskData DEFAULT = new StockpotTaskData(RecipeFilterData.DEFAULT, false);
    public static final Codec<StockpotTaskData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RecipeFilterData.CODEC.optionalFieldOf("filter", RecipeFilterData.DEFAULT)
                    .forGetter(StockpotTaskData::filter),
            Codec.BOOL.optionalFieldOf("allow_flex_recipes", false).forGetter(StockpotTaskData::allowFlexRecipes)
    ).apply(instance, StockpotTaskData::new));
}
