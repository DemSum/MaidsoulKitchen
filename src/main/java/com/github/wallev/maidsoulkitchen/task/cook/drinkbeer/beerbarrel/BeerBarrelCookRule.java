package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer.beerbarrel;
import com.github.wallev.maidsoulkitchen.task.cook.common.cook.be.CookBeBase;
import com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager;
import com.github.wallev.maidsoulkitchen.task.cook.common.rule.cook.NormalCookRule;
import lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity;
import lekavar.lma.drinkbeer.recipes.BrewingRecipe;
import lekavar.lma.drinkbeer.registries.ItemRegistry;
/** Source: upstream NormalCookRule + verified c9273ce5 DrinkBeer task's native fixes.
 * 1.20 lacks the native 1.4.1 public state/partial-cup handling. These device-only actions retain
 * those local improvements while delegating the lifecycle to the original NormalCookRule.
 * Replaces the beta beer task/DrinkBeerBarrelInventory execution; owns no plans or inventory state. */
public class BeerBarrelCookRule extends NormalCookRule<BeerBarrelBlockEntity, BrewingRecipe> {
    @Override public boolean canMoveTo(CookBeBase<BeerBarrelBlockEntity> cookBe, MaidCookManager<BrewingRecipe> cm) {
        BeerBarrelBe barrel = (BeerBarrelBe) cookBe;
        return barrel.hasReturnedBucket() || barrel.needsCups()
                && !cm.getItem(stack -> stack.is(ItemRegistry.EMPTY_BEER_MUG.get())).isFail()
                || super.canMoveTo(cookBe, cm);
    }
    @Override public void cookMake(CookBeBase<BeerBarrelBlockEntity> cookBe, MaidCookManager<BrewingRecipe> cm) {
        BeerBarrelBe barrel = (BeerBarrelBe) cookBe;
        boolean changed = barrel.takeReturnedBuckets(cm);
        // Fill cups for already present ingredients, preserving an empty barrel's planned cup demand.
        if (barrel.hasInputs()) changed |= barrel.fillCups(cm);
        if (changed) barrel.markChanged();
        super.cookMake(cookBe, cm);
    }
}
