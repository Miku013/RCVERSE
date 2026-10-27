package com.microdrone.mod.chunk;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ドローン周辺の遠距離チャンク読み込みとクライアント配信を管理するマネージャー (Phase 5)
 */
public class DroneChunkManager {

    // ドローンUUID -> ロード中のチャンク座標セット
    private static final Map<UUID, Set<ChunkPos>> LOADED_CHUNKS = new ConcurrentHashMap<>();
    // ドローンUUID -> 前回のチャンクキャッシュ中心
    private static final Map<UUID, ChunkPos> LAST_CENTER_POS = new ConcurrentHashMap<>();

    // 読み込み目標半径（半径2チャンク = 5x5 = 25チャンク。前後左右約32〜40mの視界確保）
    public static final int CHUNK_RADIUS = 2;

    /**
     * ドローン周辺のチャンクを更新（ロード・クライアント配信・外れたチャンクの解放）
     */
    public static void updateDroneChunks(ServerPlayer pilot, DroneEntity drone) {
        if (pilot == null || drone == null || !(drone.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID droneUUID = drone.getUUID();
        ChunkPos centerPos = new ChunkPos(drone.blockPosition());
        Set<ChunkPos> currentTargetChunks = new HashSet<>();

        // 中心から半径 CHUNK_RADIUS チャンクの座標を収集 (3x3 = 9チャンク)
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                currentTargetChunks.add(new ChunkPos(centerPos.x + dx, centerPos.z + dz));
            }
        }

        // 3. チャンクキャッシュ中心の変更（変化時のみログ）
        ChunkPos prevCenter = LAST_CENTER_POS.put(droneUUID, centerPos);
        if (prevCenter == null || !prevCenter.equals(centerPos)) {
            MicroDroneMod.LOGGER.info("[SERVER] [CHUNK_CACHE_CENTER] DroneUUID: {}, PrevCenter: {}, NewCenter: [{}, {}]",
                    droneUUID, prevCenter != null ? "[" + prevCenter.x + ", " + prevCenter.z + "]" : "INIT", centerPos.x, centerPos.z);
        }

        // クライアントの描画・キャッシュ中心チャンクを機体位置にリアルタイム追従させる
        pilot.connection.send(new ClientboundSetChunkCacheCenterPacket(centerPos.x, centerPos.z));

        Set<ChunkPos> previousChunks = LOADED_CHUNKS.computeIfAbsent(droneUUID, k -> new HashSet<>());

        // 1. 新たに範囲内に入ったチャンクの読み込みと配信
        for (ChunkPos pos : currentTargetChunks) {
            if (!previousChunks.contains(pos)) {
                // サーバー側で強制ロード（チケット発行）
                serverLevel.setChunkForced(pos.x, pos.z, true);
                MicroDroneMod.LOGGER.info("[SERVER] [FORCE_CHUNK_ADD] Chunk: [{}, {}], Reason: TARGET_ENTERED, DroneUUID: {}",
                        pos.x, pos.z, droneUUID);

                // チャンクを即時取得できる場合は即時配信
                LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.x, pos.z);
                if (chunk != null) {
                    sendChunkToPlayer(pilot, serverLevel, chunk);
                } else {
                    // 非同期でロード完了を待機して配信
                    serverLevel.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true).thenAccept(result -> {
                        result.ifSuccess(chunkAccess -> {
                            if (chunkAccess instanceof LevelChunk lc) {
                                serverLevel.getServer().execute(() -> {
                                    if (drone.isPilotedBy(pilot)) {
                                        sendChunkToPlayer(pilot, serverLevel, lc);
                                    }
                                });
                            }
                        });
                    });
                }
            }
        }

        // 2. 範囲外になった古いチャンクの解放
        Set<ChunkPos> toRemove = new HashSet<>();
        for (ChunkPos oldPos : previousChunks) {
            if (!currentTargetChunks.contains(oldPos)) {
                toRemove.add(oldPos);

                // サーバー側強制ロード解除
                serverLevel.setChunkForced(oldPos.x, oldPos.z, false);
                MicroDroneMod.LOGGER.info("[SERVER] [FORCE_CHUNK_REMOVE] Chunk: [{}, {}], Reason: OUT_OF_RADIUS, DroneUUID: {}",
                        oldPos.x, oldPos.z, droneUUID);

                // プレイヤー本来の視界範囲外であればクライアントへアンロードを送信
                if (!isWithinPlayerNaturalView(pilot, oldPos)) {
                    pilot.connection.send(new ClientboundForgetLevelChunkPacket(oldPos));
                    MicroDroneMod.LOGGER.info("[SERVER] [PACKET_SEND] Type: ClientboundForgetLevelChunkPacket, Chunk: [{}, {}], TargetPlayer: {}, Reason: OUT_OF_VIEW_RADIUS",
                            oldPos.x, oldPos.z, pilot.getName().getString());
                }
            }
        }

        previousChunks.removeAll(toRemove);
        previousChunks.addAll(currentTargetChunks);
    }

    /**
     * 操縦終了・ドローン消滅時にすべての追加チャンクを安全に解放
     */
    public static void releaseDroneChunks(ServerPlayer pilot, DroneEntity drone) {
        if (drone == null) return;
        UUID droneUUID = drone.getUUID();
        Set<ChunkPos> chunks = LOADED_CHUNKS.remove(droneUUID);
        LAST_CENTER_POS.remove(droneUUID);

        if (chunks != null && drone.level() instanceof ServerLevel serverLevel) {
            for (ChunkPos pos : chunks) {
                // 強制ロード解除
                serverLevel.setChunkForced(pos.x, pos.z, false);

                // プレイヤーの視界外ならクライアント側もアンロード
                if (pilot != null && !isWithinPlayerNaturalView(pilot, pos)) {
                    pilot.connection.send(new ClientboundForgetLevelChunkPacket(pos));
                    MicroDroneMod.LOGGER.info("[SERVER] [PACKET_SEND] Type: ClientboundForgetLevelChunkPacket, Chunk: [{}, {}], TargetPlayer: {}, Reason: PILOT_STOP_UNLOAD",
                            pos.x, pos.z, pilot.getName().getString());
                }
            }
            MicroDroneMod.LOGGER.info("[SERVER] [FORCE_CHUNK_RELEASE_ALL] Released {} chunks for DroneUUID: {}, Reason: PILOT_STOP_OR_REMOVE",
                    chunks.size(), droneUUID);
        }

        // 操縦終了時にクライアントの描画・キャッシュ中心をプレイヤーの位置に復元
        if (pilot != null) {
            ChunkPos playerChunk = new ChunkPos(pilot.blockPosition());
            pilot.connection.send(new ClientboundSetChunkCacheCenterPacket(playerChunk.x, playerChunk.z));
            MicroDroneMod.LOGGER.info("[SERVER] [CHUNK_CACHE_CENTER_RESTORE] Pilot: {}, RestoredCenter: [{}, {}]",
                    pilot.getName().getString(), playerChunk.x, playerChunk.z);
        }
    }

    /**
     * サーバー停止・ワールド終了時の全解放
     */
    public static void releaseAll(ServerLevel serverLevel) {
        for (Map.Entry<UUID, Set<ChunkPos>> entry : LOADED_CHUNKS.entrySet()) {
            for (ChunkPos pos : entry.getValue()) {
                serverLevel.setChunkForced(pos.x, pos.z, false);
            }
        }
        LOADED_CHUNKS.clear();
        LAST_CENTER_POS.clear();
        MicroDroneMod.LOGGER.info("[SERVER] [FORCE_CHUNK_RELEASE_ALL] Cleared all forced drone chunks. Reason: SERVER_SHUTDOWN_OR_LEVEL_UNLOAD");
    }

    private static void sendChunkToPlayer(ServerPlayer player, ServerLevel level, LevelChunk chunk) {
        MicroDroneMod.LOGGER.info("[SERVER] [PACKET_SEND] Type: ClientboundLevelChunkWithLightPacket, Chunk: [{}, {}], TargetPlayer: {}, Reason: DRONE_CHUNK_STREAMING",
                chunk.getPos().x, chunk.getPos().z, player.getName().getString());
        player.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk,
                level.getLightEngine(),
                null,
                null
        ));
    }

    /**
     * プレイヤー本来の描画距離内にあるかどうか判定（プレイヤー自身の周囲の地形を消さないため）
     */
    private static boolean isWithinPlayerNaturalView(ServerPlayer player, ChunkPos pos) {
        ChunkPos playerChunk = new ChunkPos(player.blockPosition());
        int viewDistance = player.serverLevel().getServer().getPlayerList().getViewDistance();
        int dx = Math.abs(playerChunk.x - pos.x);
        int dz = Math.abs(playerChunk.z - pos.z);
        return dx <= viewDistance && dz <= viewDistance;
    }
}
