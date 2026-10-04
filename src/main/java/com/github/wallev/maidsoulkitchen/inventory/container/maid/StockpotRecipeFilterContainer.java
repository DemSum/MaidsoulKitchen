package com.github.wallev.maidsoulkitchen.inventory.container.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.StockpotTaskData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.RecipeOption;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.StockpotAdapter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import java.util.List;

public final class StockpotRecipeFilterContainer extends TaskConfigContainer {
    public static final MenuType<StockpotRecipeFilterContainer> TYPE = IMenuTypeExtension.create(
            (id, inventory, data) -> new StockpotRecipeFilterContainer(id, inventory, data.readInt()));
    public StockpotRecipeFilterContainer(int id, Inventory inventory, int maidId) { super(TYPE, id, inventory, maidId); }
    public StockpotTaskData getSettings() {
        EntityMaid maid = getMaid();
        return maid == null ? StockpotTaskData.DEFAULT
                : maid.getOrCreateData(DataRegister.KC_STOCKPOT, StockpotTaskData.DEFAULT);
    }
    public List<RecipeOption> getRecipeOptions() {
        return getMaid() == null ? List.of()
                : StockpotAdapter.getRecipeOptions(getMaid().level(), getSettings().allowFlexRecipes());
    }
    public int getMaidEntityId() { return getMaid() == null ? -1 : getMaid().getId(); }
}
