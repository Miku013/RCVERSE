package com.microdrone.mod.client.chunk;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.client.camera.DroneCameraController;
import com.microdrone.mod.client.input.ClientDroneInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * クライアント側でのチャンク受信（ロード）および破棄（アンロード）を監視・記録するイベントリスナー
 */
public class ClientChunkEvents {

    public static void register() {
        NeoForge.EVENT_BUS.register(new ClientChunkEvents());
    }

    /**
     * クライアント側でチャンクがロード（受信・登録）された時
     */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide()) return;

        // ドローン操縦中、またはドローン視点中のみ記録（通常プレイ時の過剰ログを防止）
        if (ClientDroneInputHandler.isPiloting() || DroneCameraController.isViewing()) {
            ChunkPos pos = event.getChunk().getPos();
            Minecraft mc = Minecraft.getInstance();
            Entity camera = mc.getCameraEntity();
            String cameraInfo = camera != null
                    ? String.format("%s at [%.1f, %.1f, %.1f]", camera.getType().getDescriptionId(), camera.getX(), camera.getY(), camera.getZ())
                    : "unknown";

            MicroDroneMod.LOGGER.debug("[CLIENT] [CHUNK_LOAD] Chunk: [{}, {}], Camera: {}",
                    pos.x, pos.z, cameraInfo);
        }
    }

    /**
     * クライアント側でチャンクがアンロード（破棄・キャッシュ除外）された時
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;

        // ドローン操縦中、またはドローン視点中のみ記録
        if (ClientDroneInputHandler.isPiloting() || DroneCameraController.isViewing()) {
            ChunkPos pos = event.getChunk().getPos();
            Minecraft mc = Minecraft.getInstance();
            Entity camera = mc.getCameraEntity();
            String cameraInfo = camera != null
                    ? String.format("%s at [%.1f, %.1f, %.1f]", camera.getType().getDescriptionId(), camera.getX(), camera.getY(), camera.getZ())
                    : "unknown";

            MicroDroneMod.LOGGER.debug("[CLIENT] [CHUNK_UNLOAD] Chunk: [{}, {}], Camera: {}",
                    pos.x, pos.z, cameraInfo);
        }
    }
}
