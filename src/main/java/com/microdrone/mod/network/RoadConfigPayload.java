package com.microdrone.mod.network;

import com.microdrone.mod.MicroDroneMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 道路・ヘアピンカーブ生成設定同期パケット (クライアント -> サーバー)
 */
public record RoadConfigPayload(
        BlockPos startPos,
        int roadMode,
        int surfaceType,
        float roadWidth,
        float maxBankAngle,
        int turnDirection,
        float controlPointDistance,
        int segments,
        boolean arcFitMode,
        float loopHeight,
        float loopBoostMultiplier,
        boolean isPreview,
        boolean isGenerated,
        int action // 0=パラメータ更新, 1=自動探索リトライ, 2=確定生成, 3=削除
) implements CustomPacketPayload {

    public static final Type<RoadConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MicroDroneMod.MODID, "road_config"));

    public static final StreamCodec<ByteBuf, RoadConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, val) -> {
                BlockPos.STREAM_CODEC.encode(buf, val.startPos());
                buf.writeInt(val.roadMode());
                buf.writeInt(val.surfaceType());
                buf.writeFloat(val.roadWidth());
                buf.writeFloat(val.maxBankAngle());
                buf.writeInt(val.turnDirection());
                buf.writeFloat(val.controlPointDistance());
                buf.writeInt(val.segments());
                buf.writeBoolean(val.arcFitMode());
                buf.writeFloat(val.loopHeight());
                buf.writeFloat(val.loopBoostMultiplier());
                buf.writeBoolean(val.isPreview());
                buf.writeBoolean(val.isGenerated());
                buf.writeInt(val.action());
            },
            buf -> new RoadConfigPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
