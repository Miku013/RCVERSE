package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DroneControlPayload(
        int droneId,
        float forward,
        float strafe,
        float vertical,
        float yaw,
        float pitch,
        boolean stop,
        boolean hasClientPos,
        double clientX,
        double clientY,
        double clientZ,
        float clientSpeed
) implements CustomPacketPayload {

    public DroneControlPayload(int droneId, float forward, float strafe, float vertical, float yaw, float pitch, boolean stop) {
        this(droneId, forward, strafe, vertical, yaw, pitch, stop, false, 0.0, 0.0, 0.0, 0.0f);
    }

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
                buf.writeBoolean(val.hasClientPos);
                if (val.hasClientPos) {
                    buf.writeDouble(val.clientX);
                    buf.writeDouble(val.clientY);
                    buf.writeDouble(val.clientZ);
                    buf.writeFloat(val.clientSpeed);
                }
            },
            buf -> {
                int droneId = buf.readInt();
                float forward = buf.readFloat();
                float strafe = buf.readFloat();
                float vertical = buf.readFloat();
                float yaw = buf.readFloat();
                float pitch = buf.readFloat();
                boolean stop = buf.readBoolean();
                boolean hasClientPos = buf.readBoolean();
                double clientX = 0.0;
                double clientY = 0.0;
                double clientZ = 0.0;
                float clientSpeed = 0.0f;
                if (hasClientPos) {
                    clientX = buf.readDouble();
                    clientY = buf.readDouble();
                    clientZ = buf.readDouble();
                    clientSpeed = buf.readFloat();
                }
                return new DroneControlPayload(
                        droneId, forward, strafe, vertical, yaw, pitch, stop,
                        hasClientPos, clientX, clientY, clientZ, clientSpeed
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
