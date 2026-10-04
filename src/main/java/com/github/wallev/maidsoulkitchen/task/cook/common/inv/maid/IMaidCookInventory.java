package com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid;

import com.github.wallev.maidsoulkitchen.inventory.container.item.BagType;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemInventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.*;

/**
 * Source: 58ec08ec task/cook/common/inv/maid/IMaidCookInventory.java (MIT).
 * Keeps the upstream inventory view; NeoForge imports and component identity are the version boundary.
 * Replaces the corresponding beta ICookInventory view; no plans or work queue are stored here.
 */
public abstract class IMaidCookInventory {
    protected final EntityMaid maid;
    protected final ItemInventory itemInventory = new ItemInventory();
    protected int inputAvailableSlots = 0;
    protected int outputAvailableSlots = 0;

    protected boolean hasInputAvailableSlot = false;
    protected boolean hasOutputAvailableSlot = false;

    protected IMaidCookInventory(EntityMaid maid) {
        this.maid = maid;
    }

    protected abstract void initInvData();

    public abstract void refreshInv();

    protected abstract void proseLastInvStack(int index, ItemStack invStack);

    protected abstract void clearCacheStackInfo();

    protected abstract void add(ItemStack stack);

    public abstract Map<Item, List<ItemStack>> getInventoryStack();

    public Map<Item, LinkedList<ItemStack>> getInventoryStackQueue() {
        LinkedHashMap<Item, LinkedList<ItemStack>> itemQueueLinkedHashMap = new LinkedHashMap<>();
        getInventoryStack().forEach((item, itemStacks) -> {
            itemQueueLinkedHashMap.computeIfAbsent(item, k -> new LinkedList<>()).addAll(itemStacks);
        });
        return itemQueueLinkedHashMap;
    }

    public abstract Map<Item, Integer> getInventoryItem();

    public abstract List<ItemStack> getLastInvStack();

    public abstract IItemHandlerModifiable getAvailableInv(BagType bagType);

    public abstract IItemHandlerModifiable getInputInv();

    public abstract IItemHandlerModifiable getOutputInv();

    public abstract void syncInv();

    public ItemInventory getItemInventory() {
        return itemInventory;
    }

    public void calcAvailableSlots() {
        int a = 0;

        IItemHandlerModifiable inputInv = this.getInputInv();
        this.hasInputAvailableSlot = false;
        for (int i = 0; i < inputInv.getSlots(); i++) {
            if (inputInv.getStackInSlot(i).isEmpty()) {
                this.hasInputAvailableSlot = true;
                a++;
            }
        }
        this.inputAvailableSlots = a;
        a = 0;

        IItemHandlerModifiable outputInv = this.getOutputInv();
        this.hasOutputAvailableSlot = false;
        for (int i = 0; i < outputInv.getSlots(); i++) {
            if (outputInv.getStackInSlot(i).isEmpty()) {
                this.hasOutputAvailableSlot = true;
                a++;
            }
        }
        this.outputAvailableSlots = a;
        a = 0;
    }

    public int getInputAvailableSlots() {
        return inputAvailableSlots;
    }

    public int getOutputAvailableSlots() {
        return outputAvailableSlots;
    }

    public boolean hasInputAvailableSlot() {
        return hasInputAvailableSlot;
    }

    public boolean hasOutputAvailableSlot() {
        return hasOutputAvailableSlot;
    }
}
