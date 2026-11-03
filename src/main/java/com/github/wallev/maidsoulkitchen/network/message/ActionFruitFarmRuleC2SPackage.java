package com.github.wallev.maidsoulkitchen.network.message;

import com.github.wallev.maidsoulkitchen.entity.data.inner.task.FruitData;
import com.github.wallev.maidsoulkitchen.init.touhoulittlemaid.DataRegister;
import com.github.wallev.maidsoulkitchen.task.farm.TaskFruitFarm;
import com.github.wallev.maidsoulkitchen.task.farm.handler.fruit.FruitHandlerManager;
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

public record ActionFruitFarmRuleC2SPackage(int entityId, ResourceLocation dataKey, String rec) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ActionFruitFarmRuleC2SPackage> TYPE = new CustomPacketPayload.Type<>(getResourceLocation("action_fruit_farm_rule_c2s"));
    public static final StreamCodec<ByteBuf, ActionFruitFarmRuleC2SPackage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            ActionFruitFarmRuleC2SPackage::entityId,
            ResourceLocation.STREAM_CODEC,
            ActionFruitFarmRuleC2SPackage::dataKey,
            ByteBufCodecs.STRING_UTF8,
            ActionFruitFarmRuleC2SPackage::rec,
            ActionFruitFarmRuleC2SPackage::new
    );

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ActionFruitFarmRuleC2SPackage message, IPayloadContext context) {
        if (context.flow().isServerbound()) {
            context.enqueueWork(() -> {
                ServerPlayer sender = (ServerPlayer) context.player();
                Entity entity = sender.level.getEntity(message.entityId);
                if (entity instanceof EntityMaid maid && maid.isOwnedBy(sender)
                        && maid.getTask() instanceof TaskFruitFarm task
                        && DataRegister.FRUIT_FARM.getKey().equals(message.dataKey)
                        && sender.containerMenu instanceof com.github.wallev.maidsoulkitchen.inventory.container.maid.FruitFarmConfigContainer menu
                        && menu.getMaid() == maid && menu.stillValid(sender)
                        && java.util.Arrays.stream(FruitHandlerManager.values()).map(FruitHandlerManager::getFarmHandler)
                        .anyMatch(handler -> handler.canLoad() && handler.getUid().toString().equals(message.rec))) {
                    FruitData current = task.getTaskData(maid);
                    FruitData fruitData = new FruitData(current.searchYOffset(), current.rules());
                    fruitData.addOrRemoveRule(message.rec);
                    maid.setAndSyncData(DataRegister.FRUIT_FARM, fruitData);
                }
            });
        }
    }
}
