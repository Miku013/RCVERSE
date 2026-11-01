package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlaneModeTogglePayload(int planeId, int mode) implements CustomPacketPayload {

    public static final Type<PlaneModeTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "plane_mode_toggle"));

    public static final StreamCodec<ByteBuf, PlaneModeTogglePayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeInt(val.planeId);
                buf.writeInt(val.mode);
            },
            buf -> new PlaneModeTogglePayload(
                    buf.readInt(),
                    buf.readInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
