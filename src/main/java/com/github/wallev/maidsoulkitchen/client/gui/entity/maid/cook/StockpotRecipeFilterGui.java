package com.github.wallev.maidsoulkitchen.client.gui.entity.maid.cook;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.StockpotTaskData;
import com.github.wallev.maidsoulkitchen.inventory.container.maid.StockpotRecipeFilterContainer;
import com.github.wallev.maidsoulkitchen.network.NetworkHandler;
import com.github.wallev.maidsoulkitchen.network.message.SetStockpotFilterC2SPackage;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.RecipeOption;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.StockpotAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

public final class StockpotRecipeFilterGui extends RecipeFilterGui<StockpotRecipeFilterContainer> {
    private Boolean allowFlex;
    public StockpotRecipeFilterGui(StockpotRecipeFilterContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
    @Override protected List<RecipeOption> readRecipeOptions() {
        if (allowFlex == null) allowFlex = menu.getSettings().allowFlexRecipes();
        return menu.getMaid() == null ? List.of()
                : StockpotAdapter.getRecipeOptions(menu.getMaid().level(), allowFlex);
    }
    @Override protected net.minecraft.resources.ResourceLocation getCookTaskUid() {
        return com.github.wallev.maidsoulkitchen.task.TaskInfo.KC_STOCKPOT.uid;
    }
    @Override protected RecipeFilterData readFilter() { return menu.getSettings().filter(); }
    @Override protected void syncFilter() {
        if (menu.getMaidEntityId() >= 0) {
            NetworkHandler.sendToServer(new SetStockpotFilterC2SPackage(menu.getMaidEntityId(),
                    new StockpotTaskData(filterData, allowFlex)));
        }
    }
}
