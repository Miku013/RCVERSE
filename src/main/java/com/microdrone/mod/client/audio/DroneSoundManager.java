package com.microdrone.mod.client.audio;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * ドローンのモーター音および電波警告ビープ音を集中管理するサウンドマネージャー (Step 4 / Audio)
 */
public class DroneSoundManager {

    private static DroneSoundManager INSTANCE;
    private static DroneFlightSound activeFlightSound = null;
    private static int warningTimer = 0;

    public static void register() {
        if (INSTANCE == null) {
            INSTANCE = new DroneSoundManager();
            NeoForge.EVENT_BUS.register(INSTANCE);
        }
    }

    /**
     * 操縦開始時のサウンド初期化（モーター回転音 ＋ 接続確認チャイム）
     */
    public static void startPilotingSound(DroneEntity drone) {
        Minecraft mc = Minecraft.getInstance();

        // 1. 接続確認チャイム（ピロリン♪）
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6F, 0.5F));

        // 2. モーター飛行ループ音の再生開始
        stopPilotingSound(); // 既存があれば停止
        activeFlightSound = new DroneFlightSound(drone);
        mc.getSoundManager().play(activeFlightSound);

        warningTimer = 0;
    }

    /**
     * 操縦終了時のサウンド停止（モーター音停止 ＋ 切断音）
     */
    public static void stopPilotingSound() {
        Minecraft mc = Minecraft.getInstance();

        if (activeFlightSound != null) {
            activeFlightSound.stopSound();
            activeFlightSound = null;
            // 切断確認音（ポン）
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F, 0.4F));
        }

        warningTimer = 0;
    }

    /**
     * 毎tickの電波警告ビープ音処理
     */
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        if (!ClientDroneInputHandler.isPiloting()) {
            if (activeFlightSound != null) {
                stopPilotingSound();
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(ClientDroneInputHandler.getActiveDroneId());
        if (!(entity instanceof DroneEntity drone)) return;

        // モーター音が何らかの理由で停止または未開始なら再開
        if (activeFlightSound == null || activeFlightSound.isStopped()) {
            activeFlightSound = new DroneFlightSound(drone);
            mc.getSoundManager().play(activeFlightSound);
        }

        // 初期位置との直線距離を計算
        double sx = ClientDroneInputHandler.getStartX();
        double sy = ClientDroneInputHandler.getStartY();
        double sz = ClientDroneInputHandler.getStartZ();
        double dist = Math.sqrt(drone.distanceToSqr(sx, sy, sz));

        warningTimer++;

        // 1. 電波切断・ロスト（250m超）: 高速緊急アラート（6 ticksごと）
        if (dist > DroneEntity.MAX_SIGNAL_RANGE) {
            if (warningTimer % 6 == 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HARP.value(), 2.0F, 0.6F));
            }
        }
        // 2. 最大限界接近（225m〜250m）: テンポの速い警告ビープ（10 ticksごと）
        else if (dist > DroneEntity.CRITICAL_SIGNAL_RANGE) {
            if (warningTimer % 10 == 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.8F, 0.5F));
            }
        }
        // 3. 電波減衰警告（175m〜225m）: 控えめな注意ビープ（35 ticksごと）
        else if (dist > DroneEntity.WARN_SIGNAL_RANGE) {
            if (warningTimer % 35 == 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.6F, 0.35F));
            }
        } else {
            // 良好範囲内
            warningTimer = 0;
        }
    }
}
