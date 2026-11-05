package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * RCカーの車重チューニング用パケット (アプローチ3: プロポ送信機GUIスライダー調整型)
 */
public record RcCarTuningPayload(int carEntityId, float weightKg) implements CustomPacketPayload {

    public static final Type<RcCarTuningPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "rc_car_tuning"));

    public static final StreamCodec<ByteBuf, RcCarTuningPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeInt(val.carEntityId);
                buf.writeFloat(val.weightKg);
            },
            buf -> new RcCarTuningPayload(
                    buf.readInt(),
                    buf.readFloat()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

