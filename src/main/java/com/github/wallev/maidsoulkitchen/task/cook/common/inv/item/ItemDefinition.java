package com.github.wallev.maidsoulkitchen.task.cook.common.inv.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;

/**
 * Source: 58ec08ec task/cook/common/inv/item/ItemDefinition.java (MIT).
 * NBT moved to data components. Upstream toStack mutates a shared stack and equals ignores some tags;
 * immutable component snapshots and symmetric equality prevent inventory aliases and map-key corruption.
 * P2 replaces beta maps keyed only by Item; serialization stays outside runtime work plans.
 */
public final class ItemDefinition {
    public static final ItemDefinition EMPTY = new ItemDefinition(ItemStack.EMPTY);
    private final ItemStack stack;
    private ItemDefinition(ItemStack stack) { this.stack = stack.copyWithCount(stack.isEmpty() ? 0 : 1); }
    public static ItemDefinition of(ItemStack stack) { return new ItemDefinition(stack); }
    public static ItemDefinition of(Item item) { return of(item.getDefaultInstance()); }
    public ItemStack stack() { return stack.copy(); }
    public ItemStack toStack(int count) { return count <= 0 || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(count); }
    public ItemStack toStack(long count) { return toStack((int) Math.min(Integer.MAX_VALUE, Math.max(0, count))); }
    public Item item() { return stack.getItem(); }
    public int getMaxStackSize() { return stack.getMaxStackSize(); }
    public boolean isStackable() { return stack.isStackable(); }
    public boolean isEmpty() { return stack.isEmpty(); }
    public boolean is(ItemStack other) {
        return stack.isEmpty() ? other.isEmpty() : !other.isEmpty() && ItemStack.isSameItemSameComponents(stack, other);
    }
    public boolean is(ItemDefinition other) { return is(other.stack); }
    public boolean is(Item item) { return !stack.isEmpty() && stack.is(item); }
    @Override public boolean equals(Object other) { return other instanceof ItemDefinition definition && is(definition); }
    @Override public int hashCode() { return stack.isEmpty() ? 0 : Objects.hash(stack.getItem(), stack.getComponents()); }
}
