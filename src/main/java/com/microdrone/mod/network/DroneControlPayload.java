package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DroneControlPayload(int droneId, float forward, float strafe, float vertical, float yaw, float pitch, boolean stop)
        implements CustomPacketPayload {

    public static final Type<DroneControlPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "drone_control"));

    public static final StreamCodec<ByteBuf, DroneControlPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeInt(val.droneId);
                buf.writeFloat(val.forward);
                buf.writeFloat(val.strafe);
                buf.writeFloat(val.vertical);
                buf.writeFloat(val.yaw);
                buf.writeFloat(val.pitch);
                buf.writeBoolean(val.stop);
            },
            buf -> new DroneControlPayload(
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
