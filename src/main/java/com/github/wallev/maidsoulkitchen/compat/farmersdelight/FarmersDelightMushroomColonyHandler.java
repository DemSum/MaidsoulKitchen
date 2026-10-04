package com.github.wallev.maidsoulkitchen.compat.farmersdelight;

import com.github.tartaricacid.touhoulittlemaid.api.task.ISpecialCropHandler;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Makes Farmer's Delight mushroom colonies available to TLM's normal farming
 * task without linking against Farmer's Delight implementation classes.
 *
 * <p>Adapted from MaidSoul Brewery Public commit f5e0fa9.</p>
 */
final class FarmersDelightMushroomColonyHandler implements ISpecialCropHandler {
    private static final String AGE_PROPERTY = "age";
    private static final TagKey<Item> FARMERS_DELIGHT_KNIVES = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("farmersdelight", "tools/knives")
    );

    private final Block richSoil;
    private final Block brownMushroomColony;
    private final Block redMushroomColony;

    FarmersDelightMushroomColonyHandler(
            Block richSoil,
            Block brownMushroomColony,
            Block redMushroomColony
    ) {
        this.richSoil = richSoil;
        this.brownMushroomColony = brownMushroomColony;
        this.redMushroomColony = redMushroomColony;
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        return stack.is(Items.BROWN_MUSHROOM) || stack.is(Items.RED_MUSHROOM);
    }

    @Override
    public boolean canHarvest(EntityMaid maid, BlockPos pos, BlockState state) {
        IntegerProperty age = ageProperty(state);
        return isMushroomColony(state)
                && age != null
                && MushroomColonyHarvestRules.isMature(state.getValue(age), maxAge(age));
    }

    @Override
    public void harvest(EntityMaid maid, BlockPos pos, BlockState state, boolean destroyMode) {
        IntegerProperty age = ageProperty(state);
        if (age == null) {
            return;
        }

        Level level = maid.level();
        ItemStack mainHand = maid.getMainHandItem();
        if (mainHand.is(FARMERS_DELIGHT_KNIVES)) {
            giveToMaidOrDrop(
                    maid,
                    pos,
                    new ItemStack(
                            mushroomItem(state),
                            MushroomColonyHarvestRules.dropCount(state.getValue(age), true)
                    )
            );
            if (!level.isClientSide) {
                mainHand.hurtAndBreak(1, maid, EquipmentSlot.MAINHAND);
            }
            level.levelEvent(2001, pos, Block.getId(state));
            level.setBlock(pos, state.setValue(age, 0), 3);
            level.gameEvent(maid, GameEvent.BLOCK_CHANGE, pos);
            return;
        }

        giveToMaidOrDrop(
                maid,
                pos,
                new ItemStack(
                        mushroomItem(state),
                        MushroomColonyHarvestRules.dropCount(state.getValue(age), false)
                )
        );
        level.levelEvent(2001, pos, Block.getId(state));
        level.setBlock(pos, mushroomBlock(state).defaultBlockState(), 3);
        level.gameEvent(maid, GameEvent.BLOCK_CHANGE, pos);
    }

    @Override
    public boolean canPlant(EntityMaid maid, BlockPos pos, BlockState state, ItemStack seed) {
        if (!state.is(richSoil) || !isSeed(seed)) {
            return false;
        }

        Level level = maid.level();
        BlockPos plantPos = pos.above();
        BlockState replacing = level.getBlockState(plantPos);
        if (!replacing.canBeReplaced() || !replacing.getFluidState().isEmpty()) {
            return false;
        }

        return mushroomBlock(seed).defaultBlockState().canSurvive(level, plantPos);
    }

    @Override
    public ItemStack plant(EntityMaid maid, BlockPos pos, BlockState state, ItemStack seed) {
        if (canPlant(maid, pos, state, seed)) {
            maid.placeItemBlock(pos.above(), seed);
        }
        return seed;
    }

    private boolean isMushroomColony(BlockState state) {
        return state.is(brownMushroomColony) || state.is(redMushroomColony);
    }

    private Item mushroomItem(BlockState state) {
        return state.is(redMushroomColony) ? Items.RED_MUSHROOM : Items.BROWN_MUSHROOM;
    }

    private Block mushroomBlock(BlockState state) {
        return state.is(redMushroomColony) ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
    }

    private static Block mushroomBlock(ItemStack seed) {
        return seed.is(Items.RED_MUSHROOM) ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
    }

    private static void giveToMaidOrDrop(EntityMaid maid, BlockPos pos, ItemStack stack) {
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(false), stack, false);
        if (!remaining.isEmpty()) {
            Block.popResource(maid.level(), pos, remaining);
        }
    }

    private static IntegerProperty ageProperty(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty
                    && AGE_PROPERTY.equals(property.getName())) {
                return integerProperty;
            }
        }
        return null;
    }

    private static int maxAge(IntegerProperty age) {
        int maxAge = 0;
        for (Integer value : age.getPossibleValues()) {
            maxAge = Math.max(maxAge, value);
        }
        return maxAge;
    }
}
