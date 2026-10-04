package com.github.wallev.maidsoulkitchen.entity.passive;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.util.FakePlayerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import static com.github.wallev.maidsoulkitchen.MaidsoulKitchen.LOGGER;

public interface IAddonMaid {
    /** Source ICookTask creates one manager with the maid's brain. TLM 1.5.3 exposes no task-context
     * accessor for UI/tests/native devices; this reference prevents beta getter-created owners.
     * No inventory or work is stored here: the referenced MaidCookManager owns all state. */
    com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<?> tlmk$getCookManager();
    void tlmk$setCookManager(com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<?> manager);
    Set<Block> BLACK_LIST = new HashSet<>();

    static ItemUseOutcome tryInteractUseOnBlockWithItem(EntityMaid maid, BlockPos blockPos, ItemStack itemStack) {
        WeakReference<FakePlayer> reference = ((IAddonMaid) maid).tlmk$getFakePlayer();
        FakePlayer fakePlayer = reference == null ? null : reference.get();
        if (fakePlayer == null) return new ItemUseOutcome(InteractionResult.FAIL, itemStack.copy());

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, itemStack.copy());
        try {
            InteractionResult result = FakePlayerUtil.interactUseOnBlock(
                    reference, maid.level(), blockPos, InteractionHand.MAIN_HAND, null);
            return new ItemUseOutcome(result, fakePlayer.getMainHandItem().copy());
        } catch (RuntimeException exception) {
            LOGGER.warn("Fake player item interaction failed at {}", blockPos, exception);
            // Native use may have consumed the input before throwing. Recover the actual hand,
            // never refund the pre-use copy as well as its native effect/container.
            return new ItemUseOutcome(InteractionResult.FAIL, fakePlayer.getMainHandItem().copy());
        } finally {
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    static ItemStack interactUseOnBlockWithoutItem(EntityMaid maid, BlockPos blockPos) {
        IAddonMaid addonMaid = (IAddonMaid) maid;
        WeakReference<FakePlayer> fakePlayer$tlma = addonMaid.tlmk$getFakePlayer();
        FakePlayer fakePlayer = fakePlayer$tlma.get();
        if (fakePlayer != null) {
            try {
                InteractionResult interactionResult = FakePlayerUtil.interactUseOnBlock(fakePlayer$tlma, maid.level(), blockPos, InteractionHand.MAIN_HAND, null);

                if (interactionResult == InteractionResult.PASS) {
                    BlockState blockState = maid.level().getBlockState(blockPos);
                    Block block = blockState.getBlock();
                    LOGGER.warn("FakePlayerUtil.interactUseOnBlock PASS: items:{} blockstate: {}", blockState, block);
                    BLACK_LIST.add(block);
                    LOGGER.warn(BLACK_LIST.toString());
                }

                if (interactionResult != InteractionResult.PASS) {
                    ItemStack itemInHandCopy = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND).copy();
                    ItemHandlerHelper.insertItemStacked(maid.getAvailableInv(true), itemInHandCopy, false);
                    fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    return itemInHandCopy;
                } else {
                    fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    return ItemStack.EMPTY;
                }

            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }

        return ItemStack.EMPTY;
    }

    WeakReference<FakePlayer> tlmk$getFakePlayer();

    void tlmk$initFakePlayer();

    static void pickupAction(EntityMaid maid) {
        maid.swing(InteractionHand.MAIN_HAND);
        maid.playSound(SoundEvents.ITEM_PICKUP, 1.0F, maid.getRandom().nextFloat() * 0.1F + 1.0F);
    }

    record ItemUseOutcome(InteractionResult result, ItemStack remainder) {
    }

}
