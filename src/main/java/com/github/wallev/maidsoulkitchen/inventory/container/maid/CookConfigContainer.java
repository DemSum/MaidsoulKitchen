package com.github.wallev.maidsoulkitchen.inventory.container.maid;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

public class CookConfigContainer extends TaskConfigContainer {
    public static final MenuType<CookConfigContainer> TYPE = IMenuTypeExtension.create((windowId, inv, data) -> {
        int maidId = data.readInt();
        return new CookConfigContainer(windowId, inv, maidId, data.isReadable() && data.readBoolean());
    });
    public final boolean chooseDevices;

    public CookConfigContainer(int id, Inventory inventory, int entityId) {
        this(id, inventory, entityId, false);
    }
    public CookConfigContainer(int id, Inventory inventory, int entityId, boolean chooseDevices) {
        super(TYPE, id, inventory, entityId);
        this.chooseDevices = chooseDevices;
    }
}
