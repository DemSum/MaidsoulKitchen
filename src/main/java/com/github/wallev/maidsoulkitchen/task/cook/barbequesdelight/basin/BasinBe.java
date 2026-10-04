package com.github.wallev.maidsoulkitchen.task.cook.barbequesdelight.basin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.rec.MaidRec;
import com.mao.barbequesdelight.content.block.BasinBlockEntity;
import com.mao.barbequesdelight.content.recipe.SimpleSkeweringRecipe;
import com.mao.barbequesdelight.content.recipe.SkeweringInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/** Source: 58ec08ec BasinBe and BasinCookRule (MIT), native basin storage and SkeweringInput
 * assembly. Source Be was empty; L2Core's actual Container requires Neo InvWrapper. Source/beta
 * aliased hand materials, omitted native ingredient counts and consumed plans before assembly.
 * Manager extracts the entire physical unit, Be puts its ingredient in actual basin storage and
 * invokes the selected native Holder's consuming assembly. Deletes beta Basin Make mutations. */
public class BasinBe extends CookBeBase<BasinBlockEntity> {
    public BasinBe(EntityMaid maid) { super(maid); }
    @Override public boolean isCookBe(BlockEntity entity) { return entity instanceof BasinBlockEntity; }
    @Override public IItemHandlerModifiable getInv() { return new InvWrapper(be.items); }
    @Override public int getIngredientSize() { return be.items.getContainerSize(); }
    @Override public boolean hasResult() { return false; }
    @Override public int getResultSlot() { throw new UnsupportedOperationException("Basin outputs native assembled food directly"); }
    @Override public boolean cookStateMatch() { return !be.isRemoved(); }
    @Override public boolean recMatch() {
        return SkeweringRecSerializerManager.getInstance().getRecipes(serverLevel).stream().anyMatch(description -> description.rec() instanceof SimpleSkeweringRecipe recipe
                && recipe.ingredient.test(be.items.getItem(0)) && be.items.getItem(0).getCount() >= recipe.ingredientCount);
    }
    @Override public boolean insertInputs(MaidRec work, MaidCookManager<?> cm) {
        if (hasInputs() || work == null || !(work.recipe().value() instanceof SimpleSkeweringRecipe recipe)) return false;
        return cm.useItems(work, materials -> {
            if (materials.size() < 2 || materials.size() > 3) return ItemStack.EMPTY;
            var ingredient = materials.get(1);
            var remainder = be.items.addItem(ingredient.copy());
            ingredient.setCount(remainder.getCount()); markChanged();
            var input = new SkeweringInput(materials.getFirst(), be.items.getItem(0), materials.size() == 3 ? materials.get(2) : ItemStack.EMPTY);
            if (!recipe.matches(input, serverLevel)) return ItemStack.EMPTY;
            var result = recipe.assemble(input, serverLevel.registryAccess()); markChanged(); return result;
        });
    }
    @Override public void markChanged() { be.notifyTile(); }
}
