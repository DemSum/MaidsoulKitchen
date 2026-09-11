package com.github.wallev.maidsoulkitchen.inventory.container.item;

import com.github.wallev.maidsoulkitchen.init.MkItems;
import com.github.wallev.maidsoulkitchen.item.ItemCulinaryHub;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CookBagContainer extends CookBagAbstractContainer {
    public static final MenuType<CookBagContainer> TYPE = IMenuTypeExtension.create((windowId, inv, data) -> new CookBagContainer(windowId, inv, ItemStack.STREAM_CODEC.decode(data)));
    public final Map<BagType, ItemStackHandler> handlers;

    public CookBagContainer(int id, Inventory inventory, ItemStack cookBag) {
        super(TYPE, id, inventory, cookBag);
        this.handlers = ItemCulinaryHub.getContainers(inventory.player.registryAccess(), cookBag);
        this.addBagTypeSlots(handlers);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickTypeIn, Player player) {
        // 禁阻一切对当前手持物品的交互，防止刷物品 bug
        if (slotId == 27 + player.getInventory().selected) {
            return;
        }
        if (clickTypeIn == ClickType.SWAP) {
            return;
        }
        super.clicked(slotId, button, clickTypeIn, player);
        setContainer(player,slotId - 36, this.handlers);
    }

    protected void setContainer(Player player, int slotId, Map<BagType, ItemStackHandler> handlers) {
        for (BagType value : BagType.VALS) {
            if (slotId >= value.startIndex && slotId < value.endIndex) {
                ItemCulinaryHub.setContainer(player.registryAccess(), cookBag, handlers);
                break;
            }
        }
    }

    protected void addBagTypeSlots(Map<BagType, ItemStackHandler> handlers) {

        int yOffset = 23;
        for (BagType value : BagType.INPUT_VALS) {
            int slot = 0;
            ItemStackHandler input = handlers.getOrDefault(value, new ItemStackHandler(value.size * 9));
            for (int row = 0; row < value.size; row++, yOffset += 18) {
                for (int col = 0; col < 9; col++, slot++) {
                    this.addSlot(new SlotItemHandler(input, slot, 8 + col * 18, yOffset) {
                        @Override
                        public boolean mayPlace(@NotNull ItemStack stack) {
                            return super.mayPlace(stack) && stack.getItem().canFitInsideContainerItems() && !stack.is(MkItems.CULINARY_HUB.get());
                        }
                    });
                }
            }
        }

        yOffset += 11;
        int slot = 0;
        ItemStackHandler output = handlers.getOrDefault(BagType.OUTPUT_VAL, new ItemStackHandler(BagType.OUTPUT_VAL.size * 9));
        for (int row = 0; row < BagType.OUTPUT_VAL.size; row++, yOffset += 18) {
            for (int col = 0; col < 9; col++, slot++) {
                this.addSlot(new SlotItemHandler(output, slot, 8 + col * 18, yOffset) {
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return super.mayPlace(stack) && stack.getItem().canFitInsideContainerItems() && !stack.is(MkItems.CULINARY_HUB.get());
                    }
                });
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        ItemStack stack1 = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack2 = slot.getItem();
            stack1 = stack2.copy();
            if (index < 36) {
                if (!this.moveItemStackTo(stack2, 36, this.slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack2, 0, 36, false)) {
                return ItemStack.EMPTY;
            }
            if (stack2.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            // The hub slots start after the 36 player slots, so the old raw
            // index check skipped persistence for most Shift-clicked hub rows.
            // Persist the detached handlers after every successful transfer.
            ItemCulinaryHub.setContainer(playerIn.registryAccess(), cookBag, this.handlers);
        }
        return stack1;
    }
}
