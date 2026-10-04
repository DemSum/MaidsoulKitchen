package com.github.wallev.maidsoulkitchen.client.gui.entity.maid.cook;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.inventory.container.maid.SteamerRecipeFilterContainer;
import com.github.wallev.maidsoulkitchen.network.NetworkHandler;
import com.github.wallev.maidsoulkitchen.network.message.SetSteamerFilterC2SPackage;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.RecipeOption;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

public final class SteamerRecipeFilterGui extends RecipeFilterGui<SteamerRecipeFilterContainer> {
    public SteamerRecipeFilterGui(SteamerRecipeFilterContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
    @Override protected List<RecipeOption> readRecipeOptions() { return menu.getRecipeOptions(); }
    @Override protected net.minecraft.resources.ResourceLocation getCookTaskUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.KC_STEAMER.uid;
    }
    @Override protected RecipeFilterData readFilter() { return menu.getFilterData(); }
    @Override protected void syncFilter() {
        if (menu.getMaidEntityId() >= 0) {
            NetworkHandler.sendToServer(new SetSteamerFilterC2SPackage(menu.getMaidEntityId(), filterData));
        }
    }
}
