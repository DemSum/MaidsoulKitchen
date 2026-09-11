package com.github.wallev.maidsoulkitchen.inventory.container.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.task.TaskConfigContainer;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.RecipeFilterData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.RecipeOption;
import com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.SteamerAdapter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import java.util.List;

public final class SteamerRecipeFilterContainer extends TaskConfigContainer {
    public static final MenuType<SteamerRecipeFilterContainer> TYPE = IMenuTypeExtension.create(
            (windowId, inventory, data) -> new SteamerRecipeFilterContainer(windowId, inventory, data));

    public SteamerRecipeFilterContainer(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readInt());
    }

    public SteamerRecipeFilterContainer(int containerId, Inventory inventory, int maidId) {
        super(TYPE, containerId, inventory, maidId);
    }

    public List<RecipeOption> getRecipeOptions() {
        EntityMaid maid = getMaid();
        return maid == null ? List.of() : SteamerAdapter.getRecipeOptions(maid.level());
    }

    public RecipeFilterData getFilterData() {
        EntityMaid maid = getMaid();
        return maid == null
                ? RecipeFilterData.DEFAULT
                : maid.getOrCreateData(DataRegister.KC_STEAMER, RecipeFilterData.DEFAULT);
    }

    public int getMaidEntityId() {
        EntityMaid maid = getMaid();
        return maid == null ? -1 : maid.getId();
    }
}
