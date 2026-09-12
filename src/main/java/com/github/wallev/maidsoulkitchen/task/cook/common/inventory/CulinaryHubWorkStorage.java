package com.github.wallev.maidsoulkitchen.task.cook.common.inventory;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IChestType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.chest.ChestManager;
import com.github.wallev.maidsoulkitchen.inventory.container.item.BagType;
import com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Transaction boundary between appliance tasks and a maid's Culinary Hub.
 * Bound chests are only touched while loaded, in range and closed.
 *
 * <p>Native port of Maidsoul-Brewery-Public commit de47e15.</p>
 */
public final class CulinaryHubWorkStorage {
    private final EntityMaid maid;
    private final ServerLevel level;
    private final ItemStack hubStack;
    private final Map<BagType, ItemStackHandler> containers;
    private final Map<BagType, List<BlockPos>> bindings;

    private CulinaryHubWorkStorage(EntityMaid maid, ServerLevel level, ItemStack hubStack) {
        this.maid = maid;
        this.level = level;
        this.hubStack = hubStack;
        this.containers = ItemCulinaryHub.getContainers(maid.registryAccess(), hubStack);
        this.bindings = ItemCulinaryHub.getBindPoses(hubStack);
    }

    public static Optional<CulinaryHubWorkStorage> open(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        ItemStack stack = maid.getMaidInv().getStackInSlot(ItemCulinaryHub.INV_SLOT);
        if (!(stack.getItem() instanceof ItemCulinaryHub)) {
            return Optional.empty();
        }
        return Optional.of(new CulinaryHubWorkStorage(maid, level, stack));
    }

    public IItemHandlerModifiable ingredients() {
        return container(BagType.INGREDIENT);
    }

    public IItemHandlerModifiable outputs() {
        return container(BagType.OUTPUT);
    }

    public boolean hasIngredient(Predicate<ItemStack> predicate) {
        if (findMatchingSlot(ingredients(), predicate) >= 0) {
            return true;
        }
        for (BoundInventory inventory : boundInventories(BagType.INGREDIENT)) {
            IItemHandler source = inventory.handler();
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack stack = source.getStackInSlot(slot);
                if (stack.isEmpty() || !predicate.test(stack)) {
                    continue;
                }
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                        ingredients(), stack.copyWithCount(1), true);
                if (remainder.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean prepareIngredient(Predicate<ItemStack> predicate, int maximumCount) {
        if (findMatchingSlot(ingredients(), predicate) >= 0) {
            return true;
        }
        if (maximumCount <= 0) {
            return false;
        }

        for (BoundInventory inventory : boundInventories(BagType.INGREDIENT)) {
            IItemHandler source = inventory.handler();
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack available = source.getStackInSlot(slot);
                if (available.isEmpty() || !predicate.test(available)) {
                    continue;
                }

                int requested = Math.min(maximumCount, available.getCount());
                ItemStack candidate = available.copyWithCount(requested);
                ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(ingredients(), candidate, true);
                int transferable = requested - simulatedRemainder.getCount();
                if (transferable <= 0) {
                    continue;
                }

                ItemStack extracted = source.extractItem(slot, transferable, false);
                if (extracted.isEmpty()) {
                    continue;
                }
                int extractedCount = extracted.getCount();
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(ingredients(), extracted, false);
                if (!remainder.isEmpty()) {
                    remainder = ItemHandlerHelper.insertItemStacked(source, remainder, false);
                }
                if (!remainder.isEmpty()) {
                    remainder = ItemHandlerHelper.insertItemStacked(
                            maid.getAvailableBackpackInv(), remainder, false);
                }
                if (!remainder.isEmpty()) {
                    maid.spawnAtLocation(remainder);
                }

                markChanged(inventory.blockEntity());
                sync();
                return extractedCount > remainder.getCount();
            }
        }
        return false;
    }

    public boolean canAcceptOutputs(List<ItemStack> incoming) {
        List<ItemStack> pending = new ArrayList<>();
        for (int slot = 0; slot < outputs().getSlots(); slot++) {
            ItemStack stack = outputs().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                pending.add(stack.copy());
            }
        }
        incoming.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).forEach(pending::add);
        return canFitAll(boundInventories(BagType.OUTPUT), pending);
    }

    public void flushOutputs() {
        List<BoundInventory> destinations = boundInventories(BagType.OUTPUT);
        if (destinations.isEmpty()) {
            return;
        }

        IItemHandlerModifiable outputBuffer = outputs();
        boolean changed = false;
        for (int slot = 0; slot < outputBuffer.getSlots(); slot++) {
            ItemStack buffered = outputBuffer.getStackInSlot(slot);
            if (buffered.isEmpty()) {
                continue;
            }
            ItemStack remainder = buffered.copy();
            for (BoundInventory destination : destinations) {
                if (remainder.isEmpty()) {
                    break;
                }
                int before = remainder.getCount();
                remainder = ItemHandlerHelper.insertItemStacked(destination.handler(), remainder, false);
                if (remainder.getCount() != before) {
                    changed = true;
                    markChanged(destination.blockEntity());
                }
            }
            int moved = buffered.getCount() - remainder.getCount();
            if (moved > 0) {
                outputBuffer.extractItem(slot, moved, false);
            }
        }
        if (changed) {
            sync();
        }
    }

    public void sync() {
        ItemStack current = maid.getMaidInv().getStackInSlot(ItemCulinaryHub.INV_SLOT);
        if (current == hubStack && current.getItem() instanceof ItemCulinaryHub) {
            ItemCulinaryHub.setContainer(maid.registryAccess(), hubStack, containers);
        }
    }

    private IItemHandlerModifiable container(BagType type) {
        return containers.computeIfAbsent(type, ignored -> new ItemStackHandler(type.size * 9));
    }

    private List<BoundInventory> boundInventories(BagType type) {
        List<BoundInventory> result = new ArrayList<>();
        for (BlockPos pos : bindings.getOrDefault(type, List.of())) {
            if (!level.isLoaded(pos) || !withinWorkRange(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || !isStorageAccessible(pos, blockEntity)) {
                continue;
            }
            IItemHandler handler = ItemCulinaryHub.getBeInv(level, blockEntity);
            if (handler != null) {
                result.add(new BoundInventory(blockEntity, handler));
            }
        }
        return result;
    }

    private boolean isStorageAccessible(BlockPos pos, BlockEntity blockEntity) {
        for (IChestType type : ChestManager.getAllChestTypes()) {
            if (type.isChest(blockEntity)) {
                return type.getOpenCount(level, pos, blockEntity) <= 0;
            }
        }
        return true;
    }

    private boolean withinWorkRange(BlockPos pos) {
        float radius = maid.getRestrictRadius() * ItemCulinaryHub.WORK_RANGE;
        return maid.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= radius * radius;
    }

    private void markChanged(BlockEntity blockEntity) {
        blockEntity.setChanged();
        level.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
    }

    private static int findMatchingSlot(IItemHandler inventory, Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean canFitAll(List<BoundInventory> inventories, List<ItemStack> stacks) {
        if (inventories.isEmpty()) {
            return false;
        }
        List<VirtualSlot> slots = new ArrayList<>();
        for (BoundInventory inventory : inventories) {
            for (int slot = 0; slot < inventory.handler().getSlots(); slot++) {
                slots.add(new VirtualSlot(
                        inventory.handler(),
                        slot,
                        inventory.handler().getStackInSlot(slot).copy()
                ));
            }
        }

        for (ItemStack source : stacks) {
            int remaining = source.getCount();
            for (VirtualSlot slot : slots) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack present = slot.stack();
                if (present.isEmpty() || !ItemStack.isSameItemSameComponents(present, source)) {
                    continue;
                }
                int limit = Math.min(slot.handler().getSlotLimit(slot.index()), present.getMaxStackSize());
                int inserted = Math.min(remaining, Math.max(0, limit - present.getCount()));
                present.grow(inserted);
                remaining -= inserted;
            }
            for (VirtualSlot slot : slots) {
                if (remaining <= 0) {
                    break;
                }
                if (!slot.stack().isEmpty() || !slot.handler().isItemValid(slot.index(), source)) {
                    continue;
                }
                int limit = Math.min(slot.handler().getSlotLimit(slot.index()), source.getMaxStackSize());
                int inserted = Math.min(remaining, limit);
                slot.setStack(source.copyWithCount(inserted));
                remaining -= inserted;
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private record BoundInventory(BlockEntity blockEntity, IItemHandler handler) {
    }

    private static final class VirtualSlot {
        private final IItemHandler handler;
        private final int index;
        private ItemStack stack;

        private VirtualSlot(IItemHandler handler, int index, ItemStack stack) {
            this.handler = handler;
            this.index = index;
            this.stack = stack;
        }

        private IItemHandler handler() {
            return handler;
        }

        private int index() {
            return index;
        }

        private ItemStack stack() {
            return stack;
        }

        private void setStack(ItemStack stack) {
            this.stack = stack;
        }
    }
}
