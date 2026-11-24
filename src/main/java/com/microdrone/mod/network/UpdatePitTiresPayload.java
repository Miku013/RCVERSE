package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * RCピット作業台でのタイヤセッティング（前後タイヤ選択）をサーバーへ反映するパケット (クライアント -> サーバー)
 */
public record UpdatePitTiresPayload(
        BlockPos stationPos,
        String frontTireId,
        String rearTireId,
        boolean takeOut
) implements CustomPacketPayload {

    public static final Type<UpdatePitTiresPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "update_pit_tires"));

    public static final StreamCodec<ByteBuf, UpdatePitTiresPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                BlockPos.STREAM_CODEC.encode(buf, val.stationPos());
                ByteBufCodecs.STRING_UTF8.encode(buf, val.frontTireId());
                ByteBufCodecs.STRING_UTF8.encode(buf, val.rearTireId());
                buf.writeBoolean(val.takeOut());
            },
            buf -> new UpdatePitTiresPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
