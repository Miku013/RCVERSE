package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * デカール作業台の編集をキャンセルした際、手持ちから投入された車両をプレイヤーへ返還するパケット (クライアント -> サーバー)
 */
public record CancelDecalPayload(
        BlockPos tablePos,
        boolean returnToPlayer
) implements CustomPacketPayload {

    public static final Type<CancelDecalPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "cancel_decal"));

    public static final StreamCodec<ByteBuf, CancelDecalPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                BlockPos.STREAM_CODEC.encode(buf, val.tablePos());
                buf.writeBoolean(val.returnToPlayer());
            },
            buf -> new CancelDecalPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
