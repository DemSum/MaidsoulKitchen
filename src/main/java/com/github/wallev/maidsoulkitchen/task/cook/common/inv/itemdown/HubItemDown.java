package com.github.wallev.maidsoulkitchen.task.cook.common.inv.itemdown;

import com.github.wallev.maidsoulkitchen.task.cook.common.inv.item.ItemDefinition;
import com.github.wallev.maidsoulkitchen.task.cook.common.inventory.CookInventoryTransactions;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.*;

/**
 * Source: 58ec08ec task/cook/common/inv/itemdown/HubItemDown.java (MIT), same read/clear contract.
 * Upstream's speculative slot counter changed on rejected reservations and miscounted merged stacks.
 * Replace that confirmed defect with existing canFitAll simulation, retaining only accepted resource
 * reservations. NeoForge handlers supply real slot limits; existing hub inputs reduce chest demand.
 * This replaces beta mapChestIngredient capacity guessing when MaidCookManager is wired.
 */
public class HubItemDown extends IItemDown {
    private IItemHandler input;
    private Map<ItemDefinition, Long> existing = Map.of();

    public void init(IItemHandler input, Map<ItemDefinition, Long> existing) {
        clear();
        this.input = input;
        this.existing = Map.copyOf(existing);
    }

    @Override public boolean read(RecDataUse recDataUse) {
        Map<ItemDefinition, Integer> candidate = new HashMap<>(useItemDef);
        recDataUse.getItemUse().forEach((definition, amount) -> {
            if (amount.isTool()) candidate.merge(definition, 1, Math::max);
            else candidate.merge(definition, Math.multiplyExact(recDataUse.getRecipeRepeat(),
                    Math.multiplyExact(amount.needCount(), amount.getRecAmount())), Math::addExact);
        });
        List<ItemStack> deficits = new ArrayList<>();
        candidate.forEach((definition, count) -> {
            long missing = count - existing.getOrDefault(definition, 0L);
            if (missing > 0) deficits.add(definition.toStack(missing));
        });
        if (!CookInventoryTransactions.canFitAll(input, deficits)) return false;
        useItemDef.clear(); useItemDef.putAll(candidate);
        recLimitIndex += recDataUse.getRecipeRepeat();
        return true;
    }
}
