package com.microdrone.mod.event;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.chunk.DroneChunkManager;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = MicroDroneMod.MODID)
public class ServerEvents {

    /**
     * プレイヤーがログアウト・切断した際、操縦中ドローンのチャンクを安全に解放
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_LOGOUT] Player: {} ({}), Pos: [{}, {}, {}]",
                    player.getName().getString(), player.getUUID(),
                    String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()));
            ServerLevel level = player.serverLevel();
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof DroneEntity drone && drone.isPilotedBy(player)) {
                    MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_LOGOUT_CLEANUP] Stopping drone {} piloted by {}",
                            drone.getUUID(), player.getName().getString());
                    drone.stopPiloting(player);
                }
            }
        }
    }

    /**
     * ワールドアンロード時、すべての強制ロードチャンクを解放（サーバー負荷・メモリリーク防止）
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            MicroDroneMod.LOGGER.info("[SERVER] [LEVEL_UNLOAD] Level: {}", serverLevel.dimension().location());
            DroneChunkManager.releaseAll(serverLevel);
        }
    }
}

