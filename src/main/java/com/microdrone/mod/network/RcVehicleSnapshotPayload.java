package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * サーバーから周囲のクライアントへ車両の物理状態（スナップショット）を一括配信するパケット
 * （第1段階：Snapshot Interpolation）
 */
public record RcVehicleSnapshotPayload(
        int entityId,
        int serverTick,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        float roll,
        float vx,
        float vy,
        float vz
) implements CustomPacketPayload {

    public static final Type<RcVehicleSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "vehicle_snapshot"));

    public static final StreamCodec<ByteBuf, RcVehicleSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeInt(val.entityId);
                buf.writeInt(val.serverTick);
                buf.writeDouble(val.x);
                buf.writeDouble(val.y);
                buf.writeDouble(val.z);
                buf.writeFloat(val.yaw);
                buf.writeFloat(val.pitch);
                buf.writeFloat(val.roll);
                buf.writeFloat(val.vx);
                buf.writeFloat(val.vy);
                buf.writeFloat(val.vz);
            },
            buf -> new RcVehicleSnapshotPayload(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
