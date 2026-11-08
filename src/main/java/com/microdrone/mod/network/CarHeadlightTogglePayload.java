package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * RCカーのヘッドライトON/OFFトグル用クライアント->サーバーパケット
 */
public record CarHeadlightTogglePayload(int carId) implements CustomPacketPayload {

    public static final Type<CarHeadlightTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "car_headlight_toggle"));

    public static final StreamCodec<ByteBuf, CarHeadlightTogglePayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> buf.writeInt(val.carId),
            buf -> new CarHeadlightTogglePayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
