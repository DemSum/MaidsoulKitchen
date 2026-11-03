package com.github.wallev.maidsoulkitchen.task.farm.handler.berry;


import com.github.wallev.maidsoulkitchen.task.farm.handler.IFarmHandlerManager;

import java.util.function.Supplier;

public enum BerryHandlerManager implements IFarmHandlerManager<BerryHandler> {

    MINECRAFT(VanillaBerryHandler::new),
    L2_HARVESTER(L2BerryHandler::new),
    COMPAT(CompatBerryHandler::new);

    private final Supplier<BerryHandler> handlerFactory;

    BerryHandlerManager(Supplier<BerryHandler> handlerFactory) {
        this.handlerFactory = handlerFactory;
    }

    public BerryHandler getFarmHandler() {
        return handlerFactory.get();
    }
}
