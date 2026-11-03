package com.github.wallev.maidsoulkitchen.network.message;


import com.github.wallev.maidsoulkitchen.entity.data.inner.task.BerryData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.farm.TaskBerryFarm;
import com.github.wallev.maidsoulkitchen.task.farm.handler.berry.BerryHandlerManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import static com.github.tartaricacid.touhoulittlemaid.util.ResourceLocationUtil.getResourceLocation;

public record ActionBerryFarmRuleC2SPackage(int entityId, ResourceLocation dataKey, String rec) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ActionBerryFarmRuleC2SPackage> TYPE = new CustomPacketPayload.Type<>(getResourceLocation("berry_farm_rule_c2s"));
    public static final StreamCodec<ByteBuf, ActionBerryFarmRuleC2SPackage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ActionBerryFarmRuleC2SPackage::entityId,
            ResourceLocation.STREAM_CODEC,
            ActionBerryFarmRuleC2SPackage::dataKey,
            ByteBufCodecs.STRING_UTF8,
            ActionBerryFarmRuleC2SPackage::rec,
            ActionBerryFarmRuleC2SPackage::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public static void handle(ActionBerryFarmRuleC2SPackage message, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> {
                ServerPlayer sender = (ServerPlayer) context.player();
                Entity entity = sender.level.getEntity(message.entityId);
                if (entity instanceof EntityMaid maid && maid.isOwnedBy(sender)
                        && maid.getTask() instanceof TaskBerryFarm
                        && DataRegister.BERRY_FARM.getKey().equals(message.dataKey)
                        && sender.containerMenu instanceof com.github.wallev.maidsoulkitchen.inventory.container.maid.BerryFarmConfigContainer menu
                        && menu.getMaid() == maid && menu.stillValid(sender)
                        && java.util.Arrays.stream(BerryHandlerManager.values()).map(BerryHandlerManager::getFarmHandler)
                        .anyMatch(handler -> handler.canLoad() && handler.getUid().toString().equals(message.rec))) {
                    BerryData berryData = new BerryData(((TaskBerryFarm) maid.getTask()).getTaskData(maid).rules());
                    berryData.addOrRemoveRule(message.rec);
                    maid.setAndSyncData(DataRegister.BERRY_FARM, berryData);
                }
            });
        }
    }



}
