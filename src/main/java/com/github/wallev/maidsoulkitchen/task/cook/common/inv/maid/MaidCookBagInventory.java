package com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid;

import com.github.wallev.maidsoulkitchen.inventory.container.item.BagType;
import com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Source: 58ec08ec task/cook/common/inv/maid/MaidCookBagInventory.java (MIT).
 * Keeps the upstream inventory view; NeoForge imports and component identity are the version boundary.
 * Replaces the corresponding beta ICookInventory view; no plans or work queue are stored here.
 */
public class MaidCookBagInventory extends IMaidCookInventory {
    private final ItemStack stack;
    private final boolean includeBackpack;
    private final Map<Item, Integer> inventoryItem = new HashMap<>();
    private final Map<Item, List<ItemStack>> inventoryStack = new HashMap<>();
    private final List<ItemStack> lastInvStack = new ArrayList<>();
    private Map<BagType, ItemStackHandler> containers;
    private Map<BagType, IItemHandlerModifiable> itemStackHandlers;

    private IItemHandlerModifiable inputInv;
    private IItemHandlerModifiable outputInv;
    private CompoundTag lastSerializedContainers;

    public MaidCookBagInventory(EntityMaid maid, ItemStack stack) {
        this(maid, stack, false);
    }
    /** Verified KC hub + backpack input view, moved from StockpotWorkStorage. Both components
     * are physical inventory views tracked and synchronized by the same manager-owned cookInv. */
    public MaidCookBagInventory(EntityMaid maid, ItemStack stack, boolean includeBackpack) {
        super(maid);
        this.stack = stack;
        this.includeBackpack = includeBackpack;
        this.initInvData();
    }

    @Override
    protected void initInvData() {
        this.containers = ItemCulinaryHub.getContainers(maid.registryAccess(), stack);
        this.lastSerializedContainers = serializedContainers();


        IItemHandlerModifiable inputInv = logicalInput(containers);
        if (includeBackpack) inputInv = new CombinedInvWrapper(inputInv, maid.getAvailableBackpackInv());
        Map<BagType, IItemHandlerModifiable> itemStackHandlers = new HashMap<>();
        for (BagType inputBagType : BagType.INPUT_VALS) {
            itemStackHandlers.put(inputBagType, inputInv);
        }
        ItemStackHandler outputInv = containers.get(BagType.OUTPUT);
        itemStackHandlers.put(BagType.OUTPUT, outputInv);
        this.itemStackHandlers = itemStackHandlers;

        this.inputInv = inputInv;
        this.outputInv = outputInv;
    }

    @Override
    public void refreshInv() {
        clearCacheStackInfo();
        // Keep the owned handlers until sync: re-deserializing here discarded unsaved transfers.
        // A menu/component edit between operations must still invalidate this derived inventory view.
        if (!serializedContainers().equals(lastSerializedContainers)) this.initInvData();
        IItemHandlerModifiable availableInv = itemStackHandlers.get(BagType.INGREDIENT);
        for (int i = 0; i < availableInv.getSlots(); i++) {
            ItemStack stack = availableInv.getStackInSlot(i);
            proseLastInvStack(i, stack);
            if (stack.isEmpty()) continue;
            add(stack);
            itemInventory.add(stack);
        }
    }

    @Override
    protected void proseLastInvStack(int index, ItemStack invStack) {
        if (index < lastInvStack.size()) {
            ItemStack cacheStack = lastInvStack.get(index);
            if (ItemStack.isSameItemSameComponents(cacheStack, invStack) && cacheStack != invStack) {
                cacheStack.setCount(invStack.getCount());
                return;
            }
        }
        lastInvStack.add(invStack.copy());
    }

    @Override
    protected void clearCacheStackInfo() {
        itemInventory.clear();
        inventoryItem.clear();
        inventoryStack.clear();
        lastInvStack.clear();
    }

    @Override
    protected void add(ItemStack stack) {
        if (!stack.isEmpty()) {
            Item item = stack.getItem();
            if (this.inventoryStack.get(item) == null) {
                List<ItemStack> stackList = new ArrayList<>();
                stackList.add(stack);
                this.inventoryStack.put(item, stackList);
            } else {
                this.inventoryStack.get(item).add(stack);
            }

            this.inventoryItem.merge(item, stack.getCount(), (a, b) -> a + b);
        }
    }

    @Override
    public Map<Item, List<ItemStack>> getInventoryStack() {
        return inventoryStack;
    }

    @Override
    public Map<Item, Integer> getInventoryItem() {
        return inventoryItem;
    }

    public ItemStack getStack() {
        return stack;
    }

    @Override
    public List<ItemStack> getLastInvStack() {
        return lastInvStack;
    }

    @Override
    public IItemHandlerModifiable getAvailableInv(BagType bagType) {
        return itemStackHandlers.get(bagType);
    }

    @Override
    public IItemHandlerModifiable getInputInv() {
        return this.inputInv;
    }

    @Override
    public IItemHandlerModifiable getOutputInv() {
        return this.outputInv;
    }

    @Override
    public void syncInv() {
        this.calcAvailableSlots();
        if (ItemCulinaryHub.getItem(maid) == stack) {
            ItemCulinaryHub.setContainer(maid.registryAccess(), stack, containers);
            lastSerializedContainers = serializedContainers();
        }
    }

    /** Existing c9273ce5 CookBagInventory.logicalInput: retain all four persisted input sections. */
    public static IItemHandlerModifiable logicalInput(Map<BagType, ItemStackHandler> containers) {
        ItemStackHandler[] handlers = new ItemStackHandler[BagType.INPUT_VALS.length];
        for (int i = 0; i < BagType.INPUT_VALS.length; i++) {
            BagType type = BagType.INPUT_VALS[i];
            handlers[i] = containers.getOrDefault(type, new ItemStackHandler(type.size * 9));
        }
        return new CombinedInvWrapper(handlers);
    }

    /** 1.21 component replacement detection; 1.20 upstream unconditionally reloaded NBT on refresh. */
    private CompoundTag serializedContainers() {
        return stack.getOrDefault(ItemCulinaryHub.STORAGE_DATA_TAG, new CompoundTag()).getCompound("container").copy();
    }
}
