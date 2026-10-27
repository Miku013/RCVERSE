package com.microdrone.mod.event;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.chunk.DroneChunkManager;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
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

    /**
     * ドローン操縦中、プレイヤー本体または身代わりダミーがダメージを受けた際のフェイルセーフ（緊急脱出）
     */
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        // 1. 身代わりダミー人形が攻撃・ダメージを受けた場合
        DroneEntity dummyDrone = DroneEntity.getByDummy(event.getEntity().getUUID());
        if (dummyDrone != null) {
            // ダミー自体の破損・破壊を防止するためにダメージをキャンセル
            event.setCanceled(true);

            java.util.UUID pilotUUID = dummyDrone.getPilotUUID();
            if (pilotUUID != null && event.getEntity().getServer() != null) {
                ServerPlayer pilot = event.getEntity().getServer().getPlayerList().getPlayer(pilotUUID);
                if (pilot != null) {
                    MicroDroneMod.LOGGER.info("[SERVER] [PILOT_FAILSAFE_DAMAGE] Dummy took incoming damage ({}), emergency restoring player {}",
                            event.getSource().getMsgId(), pilot.getName().getString());
                    dummyDrone.stopPiloting(pilot);
                    pilot.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.pilot_aborted_damage"));
                    pilot.level().playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(),
                            net.minecraft.sounds.SoundEvents.PLAYER_HURT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
            return;
        }

        // 2. 操縦中のプレイヤー本体が直接ダメージを受けた場合
        if (event.getEntity() instanceof ServerPlayer player) {
            DroneEntity pilotDrone = DroneEntity.getByPilot(player.getUUID());
            if (pilotDrone != null) {
                MicroDroneMod.LOGGER.info("[SERVER] [PILOT_FAILSAFE_DAMAGE] Pilot player took incoming damage ({}), emergency stopping drone",
                        event.getSource().getMsgId());
                pilotDrone.stopPiloting(player);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.pilot_aborted_damage"));
            }
        }
    }

    /**
     * 身代わりダミー人形が他プレイヤー等から直接攻撃された場合のフェイルセーフ
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        DroneEntity dummyDrone = DroneEntity.getByDummy(event.getTarget().getUUID());
        if (dummyDrone != null) {
            event.setCanceled(true); // ダミー破壊を即座にブロック

            java.util.UUID pilotUUID = dummyDrone.getPilotUUID();
            if (pilotUUID != null && event.getTarget().getServer() != null) {
                ServerPlayer pilot = event.getTarget().getServer().getPlayerList().getPlayer(pilotUUID);
                if (pilot != null) {
                    MicroDroneMod.LOGGER.info("[SERVER] [PILOT_FAILSAFE_ATTACK] Dummy attacked by {}, emergency restoring player {}",
                            event.getEntity().getName().getString(), pilot.getName().getString());
                    dummyDrone.stopPiloting(pilot);
                    pilot.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.pilot_aborted_damage"));
                    pilot.level().playSound(null, pilot.getX(), pilot.getY(), pilot.getZ(),
                            net.minecraft.sounds.SoundEvents.PLAYER_HURT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        }
    }
}

