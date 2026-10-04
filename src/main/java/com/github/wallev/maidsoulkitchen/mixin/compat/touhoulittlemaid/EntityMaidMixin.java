package com.github.wallev.maidsoulkitchen.mixin.compat.touhoulittlemaid;

import com.github.wallev.maidsoulkitchen.entity.passive.IAddonMaid;
import com.github.wallev.maidsoulkitchen.util.FakePlayerUtil;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.ref.WeakReference;

@Mixin(value = EntityMaid.class)
public abstract class EntityMaidMixin extends TamableAnimal implements CrossbowAttackMob, IMaid, IAddonMaid {

    @SuppressWarnings("all")
    private WeakReference<FakePlayer> fakePlayer;
    @org.spongepowered.asm.mixin.Unique
    private com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<?> tlmk$cookManager;

    @Override public com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<?> tlmk$getCookManager() { return tlmk$cookManager; }
    @Override public void tlmk$setCookManager(com.github.wallev.maidsoulkitchen.task.cook.common.manager.MaidCookManager<?> manager) { tlmk$cookManager = manager; }
    /** Source task/Rule stop boundary. TLM replaces its brain without exposing the outgoing
     * cooking context; retire before that replacement, including changes to non-cooking tasks. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "setTask", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void tlmk$retireCookingTask(com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask next,
                                        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!level().isClientSide && ((EntityMaid) (Object) this).getTask() != next && tlmk$cookManager != null) {
            tlmk$cookManager.retire(); tlmk$cookManager = null;
        }
    }
    @org.spongepowered.asm.mixin.injection.Inject(method = "die", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void tlmk$retireCookingOnDeath(net.minecraft.world.damagesource.DamageSource source,
                                           org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!level().isClientSide && tlmk$cookManager != null) {
            tlmk$cookManager.retire(); tlmk$cookManager = null;
        }
    }

    protected EntityMaidMixin(EntityType<? extends TamableAnimal> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Override
    public @NotNull WeakReference<FakePlayer> tlmk$getFakePlayer() {
        this.tlmk$initFakePlayer();
        return fakePlayer;
    }

    @Override
    public void tlmk$initFakePlayer() {
        if (fakePlayer == null) {
            this.fakePlayer = FakePlayerUtil.setupBeforeTrigger((ServerLevel) level(), this.getName().getString(), this);
        }
    }
}
