package com.github.wallev.maidsoulkitchen.init.touhoulittlemaid;

import com.github.wallev.maidsoulkitchen.modclazzchecker.manager.TaskInfo;
import com.github.wallev.maidsoulkitchen.task.farm.*;
import com.github.wallev.maidsoulkitchen.task.other.TaskFeedAnimalT;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

public final class TaskRegister {
    private TaskRegister() {
    }

    public static void init(TaskManager manager) {
        if (TaskInfo.COMPAT_MELON_FARM.canLoad()) {
            manager.add(new TaskCompatMelonFarm());
        }
        if (TaskInfo.BERRY_FARM.canLoad()) {
            manager.add(new TaskBerryFarm());
        }
        if (TaskInfo.FRUIT_FARM.canLoad()) {
            manager.add(new TaskFruitFarm());
        }
        if (TaskInfo.FEED_ANIMAL_T.canLoad()) {
            manager.add(new TaskFeedAnimalT());
        }

        if (TaskInfo.SERENESEASONS_FARM.canLoad()) {
            manager.add(new TaskSsFarm());
        }
        if (TaskInfo.ECLIPTICSSEASONS_FARM.canLoad()) {
            manager.add(new TaskEsFarm());
        }

        com.github.wallev.maidsoulkitchen.task.cook.common.task.CookTaskManager.init();
        com.github.wallev.maidsoulkitchen.task.cook.common.task.CookTaskManager.getTaskIndex().forEach(manager::add);
    }
}
