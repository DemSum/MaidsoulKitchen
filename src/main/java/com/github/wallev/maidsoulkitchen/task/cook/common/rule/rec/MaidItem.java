package com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;

/**
 * Source: 58ec08ec task/cook/common/rule/rec/MaidItem.java (MIT).
 * Retains item/count; explicit roles replace beta positional conventions and describe new KC resources.
 * No CODEC: runtime queues rebuild after load. P2 removes beta live inventory stack references.
 */
public record MaidItem(ItemDefinition item, int count, Role role) {
    public enum Role { INGREDIENT, TOOL, CONTAINER, FUEL, OIL, WATER, FLUID }
    public static final MaidItem EMPTY = new MaidItem(ItemDefinition.EMPTY, 0, Role.INGREDIENT);
    public MaidItem(ItemDefinition item, int count) { this(item, count, Role.INGREDIENT); }
    public MaidItem {
        java.util.Objects.requireNonNull(item);
        java.util.Objects.requireNonNull(role);
        if (count < 0 || (count > 0 && item.isEmpty())) throw new IllegalArgumentException("Invalid material count");
    }
    public boolean isEmpty() { return count == 0 || item.isEmpty(); }
}
