package com.github.wallev.maidsoulkitchen.task.cook.common.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.wallev.maidsoulkitchen.api.task.IDataTask;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTargetTask;
import com.github.wallev.maidsoulkitchen.api.task.cook.ICookTask;
import com.github.wallev.maidsoulkitchen.entity.data.inner.task.KitchenData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import java.util.*;

/** Source: 58ec08ec TaskCook (MIT), same KitchenData UID -> CookTaskManager dispatch.
 * TLM BehaviorControl and the current KitchenData key replace the upstream version shims.
 * No work lives in this entry point. Legacy appliance UIDs retain the same Be/Rule chain. */
public final class TaskCook implements ICookTargetTask, IDataTask<KitchenData> {
    @Override public ResourceLocation getUid() { return com.github.wallev.maidsoulkitchen.task.TaskInfo.COOK.uid; }
    @Override public ItemStack getIcon() { return com.github.wallev.maidsoulkitchen.init.MkItems.CULINARY_HUB.get().getDefaultInstance(); }
    @Override public com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey<KitchenData> getCookDataKey() { return DataRegister.KITCHEN; }
    @Override public KitchenData getDefaultData() { return new KitchenData(); }
    /** Source: 58ec08ec ICookTask.isEnable/getEnableConditionDesc/hasEnoughFavor (MIT).
     * The single visible cooking entry owns the favour gate so TLM 1.5.3's native picker
     * and MaidTaskPackage apply the same lock to every device, including KC and legacy aliases. */
    @Override public boolean isEnable(EntityMaid maid) { return hasEnoughFavor(maid); }
    @Override public List<Pair<String, java.util.function.Predicate<EntityMaid>>> getEnableConditionDesc(EntityMaid maid) {
        return List.of(Pair.of("has_enough_favor", this::hasEnoughFavor));
    }
    private boolean hasEnoughFavor(EntityMaid maid) { return maid.getFavorabilityManager().getLevel() >= 1; }
    /** Source: 58ec08ec getTask dispatch and c9273ce5 appliance metadata. TLM 1.5.3 asks
     * the visible task for these values, so forward to the selected device without another state. */
    @Override public net.minecraft.sounds.SoundEvent getAmbientSound(EntityMaid maid) {
        return getTask(maid).map(task -> task.getAmbientSound(maid)).orElse(null);
    }
    @Override public boolean workPointTask(EntityMaid maid) {
        return getTask(maid).map(task -> task.workPointTask(maid)).orElse(false);
    }
    @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return getOrIdleTask(maid).createBrainTasks(maid);
    }
    public Optional<ICookTask<?, ?>> getTask(EntityMaid maid) { return CookTaskManager.findTask(getTaskData(maid).getCookName()); }
    public IMaidTask getOrIdleTask(EntityMaid maid) {
        return getTask(maid).map(task -> (IMaidTask) task).orElseGet(() -> {
            getTaskData(maid).setCookName(KitchenData.IDLE);
            com.github.wallev.maidsoulkitchen.task.cook.common.ai.CookTargetMemory.clear(maid);
            return TaskManager.getIdleTask();
        });
    }
    /** Source getTask, shared with existing legacy appliance saves and menu consumers. */
    public static Optional<ICookTask<?, ?>> resolve(EntityMaid maid) {
        return maid.getTask() instanceof TaskCook task ? task.getTask(maid)
                : maid.getTask() instanceof ICookTask<?, ?> task ? Optional.of(task) : Optional.empty();
    }
    @Override public net.minecraft.world.MenuProvider getTaskConfigGuiProvider(EntityMaid maid) {
        var selected = getTask(maid);
        if (selected.isPresent() && (selected.get() instanceof com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.TaskKcSteamer
                || selected.get() instanceof com.github.wallev.maidsoulkitchen.task.cook.kaleidoscopecookery.TaskKcStockpot))
            return selected.get().getTaskConfigGuiProvider(maid);
        return new net.minecraft.world.SimpleMenuProvider((id, inventory, player) ->
                new com.github.wallev.maidsoulkitchen.inventory.container.maid.CookConfigContainer(id, inventory, maid.getId()), getName());
    }
    /** Source KitchenData.setCookName. Rebuild TLM's brain so selecting a device retires the old
     * Rule and manager; the client cannot submit settings or a second work state in this action. */
    public static boolean select(EntityMaid maid, ResourceLocation uid) {
        if (!(maid.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        IMaidTask current = maid.getTask();
        if (!(current instanceof TaskCook) && !(current instanceof ICookTask<?, ?>)) return false;
        var unified = current instanceof TaskCook cook ? cook
                : TaskManager.findTask(com.github.wallev.maidsoulkitchen.task.TaskInfo.COOK.uid)
                .filter(TaskCook.class::isInstance).map(TaskCook.class::cast).orElse(null);
        if (unified == null || !unified.isEnable(maid)) return false;
        var task = CookTaskManager.findTask(uid);
        if (!uid.equals(KitchenData.IDLE) && (task.isEmpty() || !task.get().isEnable(maid))) return false;
        // TLM 1.5.3 keeps hidden legacy UIDs loadable. An explicit device choice migrates
        // that entry into upstream TaskCook, retaining its existing canonical/legacy filters.
        if (current instanceof ICookTask<?, ?> legacy) KitchenData.get(maid, legacy);
        var owner = (com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid) maid;
        if (owner.tlmk$getCookManager() != null) owner.tlmk$getCookManager().retire();
        owner.tlmk$setCookManager(null);
        KitchenData.get(maid).setCookName(uid); KitchenData.sync(maid);
        if (current == unified) maid.refreshBrain(level);
        else maid.setTask(unified);
        return true;
    }
}
