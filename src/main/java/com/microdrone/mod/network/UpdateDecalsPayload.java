package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * デカール作業台でのステッカー編集結果をサーバーへ反映するパケット (クライアント -> サーバー)
 */
public record UpdateDecalsPayload(
        BlockPos tablePos,
        CompoundTag decalsTag,
        boolean takeOut
) implements CustomPacketPayload {

    public static final Type<UpdateDecalsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "update_decals"));

    public static final StreamCodec<ByteBuf, UpdateDecalsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                BlockPos.STREAM_CODEC.encode(buf, val.tablePos());
                ByteBufCodecs.COMPOUND_TAG.encode(buf, val.decalsTag());
                buf.writeBoolean(val.takeOut());
            },
            buf -> new UpdateDecalsPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.COMPOUND_TAG.decode(buf),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

