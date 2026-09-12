package com.github.wallev.maidsoulkitchen.compat.farmersdelight;

import com.github.tartaricacid.touhoulittlemaid.api.task.ISpecialCropHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.task.crop.SpecialCropManager;
import com.github.wallev.maidsoulkitchen.MaidsoulKitchen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Registers Farmer's Delight mushroom colonies through TLM 1.5.3's official
 * special-crop extension point.
 *
 * <p>Adapted from MaidSoul Brewery Public commits f5e0fa9 and de47e15.</p>
 */
public final class FarmersDelightMushroomColonyCompat {
    private static final ResourceLocation RICH_SOIL = id("rich_soil");
    private static final ResourceLocation BROWN_MUSHROOM_COLONY = id("brown_mushroom_colony");
    private static final ResourceLocation RED_MUSHROOM_COLONY = id("red_mushroom_colony");

    private FarmersDelightMushroomColonyCompat() {
    }

    public static void register(SpecialCropManager manager) {
        Block richSoil = block(RICH_SOIL);
        Block brownMushroomColony = block(BROWN_MUSHROOM_COLONY);
        Block redMushroomColony = block(RED_MUSHROOM_COLONY);
        if (richSoil == Blocks.AIR
                || brownMushroomColony == Blocks.AIR
                || redMushroomColony == Blocks.AIR) {
            MaidsoulKitchen.LOGGER.warn(
                    "Farmer's Delight is loaded, but its mushroom colony blocks are unavailable; "
                            + "skipping special crop registration"
            );
            return;
        }

        ISpecialCropHandler handler = new FarmersDelightMushroomColonyHandler(
                richSoil,
                brownMushroomColony,
                redMushroomColony
        );
        manager.addSeed(Items.BROWN_MUSHROOM, handler);
        manager.addSeed(Items.RED_MUSHROOM, handler);
        manager.addCrop(richSoil, handler);
        manager.addCrop(brownMushroomColony, handler);
        manager.addCrop(redMushroomColony, handler);

        MaidsoulKitchen.LOGGER.info(
                "Registered Farmer's Delight mushroom colony handlers through TLM's special-crop API"
        );
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("farmersdelight", path);
    }

    private static Block block(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        return block == null ? Blocks.AIR : block;
    }
}
