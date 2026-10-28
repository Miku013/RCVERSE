package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DronePilotStatePayload(int droneId, boolean isPiloting, double startX, double startY, double startZ) implements CustomPacketPayload {

    public static final Type<DronePilotStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "drone_pilot_state"));

    public static final StreamCodec<ByteBuf, DronePilotStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DronePilotStatePayload::droneId,
            ByteBufCodecs.BOOL, DronePilotStatePayload::isPiloting,
            ByteBufCodecs.DOUBLE, DronePilotStatePayload::startX,
            ByteBufCodecs.DOUBLE, DronePilotStatePayload::startY,
            ByteBufCodecs.DOUBLE, DronePilotStatePayload::startZ,
            DronePilotStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
