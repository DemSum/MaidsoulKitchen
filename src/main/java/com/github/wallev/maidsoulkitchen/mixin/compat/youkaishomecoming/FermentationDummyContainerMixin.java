package com.github.wallev.maidsoulkitchen.mixin.compat.youkaishomecoming;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskMixin;
import dev.xkmc.l2core.base.tile.BaseTank;
import dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationDummyContainer;
import dev.xkmc.youkaishomecoming.content.pot.ferment.FermentationItemContainer;
import net.minecraft.world.item.crafting.RecipeInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Version boundary for the upstream FermentationCookBe native recipe lookup (58ec08ec, MIT).
 * In 1.21 RecipeManager rejects RecipeInput.isEmpty before matching. YHC 3.0.6 inherits the
 * item-only default, rejecting fluid-only native recipes even when their matcher succeeds.
 * Include the actual tank in emptiness; native processing, Holder lookup and inventories remain
 * authoritative. Replaces the beta task's fluid lookup failure; owns no plan or cached state. */
@TaskMixin(TaskInfo.YHC_FERMENTATION_TANK)
@Mixin(FermentationDummyContainer.class)
public abstract class FermentationDummyContainerMixin implements RecipeInput {
    @Shadow public abstract FermentationItemContainer items();
    @Shadow public abstract BaseTank fluids();
    @Override public boolean isEmpty() { return items().isEmpty() && fluids().isEmpty(); }
}
