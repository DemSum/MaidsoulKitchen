package com.github.wallev.maidsoulkitchen.task.cook.drinkbeer;

import lekavar.lma.drinkbeer.blockentities.BeerBarrelBlockEntity;

/**
 * Isolates the Drink Beer Refill API used by the maid task.
 *
 * <p>Drink Beer Refill 1.4.1 exposes barrel state and persistence through public
 * methods. Keeping those calls here avoids mixins against private barrel internals
 * and makes a future API migration local to one class.</p>
 */
final class DrinkBeerBarrelAdapter {
    private DrinkBeerBarrelAdapter() {
    }

    static boolean canModifyInputs(BeerBarrelBlockEntity barrel) {
        return barrel.canModifyInputs();
    }

    static boolean isBrewing(BeerBarrelBlockEntity barrel) {
        return !barrel.canModifyInputs() && !barrel.isOutputReady();
    }

    static boolean isOutputReady(BeerBarrelBlockEntity barrel) {
        return barrel.isOutputReady();
    }

    static void markChanged(BeerBarrelBlockEntity barrel) {
        barrel.updateBE();
    }
}
