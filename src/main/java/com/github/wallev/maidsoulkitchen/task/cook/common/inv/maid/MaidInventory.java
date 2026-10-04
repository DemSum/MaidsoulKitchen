package com.github.wallev.maidsoulkitchen.task.cook.common.inv.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.inventory.container.item.BagType;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Source: 58ec08ec task/cook/common/inv/maid/MaidInventory.java (MIT).
 * Keeps the upstream inventory view; NeoForge imports and component identity are the version boundary.
 * Replaces the corresponding beta ICookInventory view; no plans or work queue are stored here.
 */
public class MaidInventory extends IMaidCookInventory {
    private final Map<Item, Integer> inventoryItem = new HashMap<>();
    private final Map<Item, List<ItemStack>> inventoryStack = new HashMap<>();
    private final List<ItemStack> lastInvStack = new ArrayList<>();
    private IItemHandlerModifiable inv;
    private final boolean includeHands;

    public MaidInventory(EntityMaid maid) {
        this(maid, true);
    }
    /** Verified KC direct-LivingEntity interactions borrow the main hand temporarily. Keep that
     * physical equipment outside the device's source/destination view, as the old KC Storage did. */
    public MaidInventory(EntityMaid maid, boolean includeHands) {
        super(maid);
        this.includeHands = includeHands;
        this.initInvData();
    }

    @Override
    protected void initInvData() {
        this.inv = includeHands ? maid.getAvailableInv(true) : maid.getAvailableBackpackInv();
    }

    public void refreshInv() {
        clearCacheStackInfo();
        this.initInvData();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
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

    @Override
    public List<ItemStack> getLastInvStack() {
        return lastInvStack;
    }

    @Override
    public IItemHandlerModifiable getAvailableInv(BagType bagType) {
        return inv;
    }

    @Override
    public IItemHandlerModifiable getInputInv() {
        return this.inv;
    }

    @Override
    public IItemHandlerModifiable getOutputInv() {
        return this.inv;
    }

    @Override
    public void syncInv() {
        this.calcAvailableSlots();
    }
}
