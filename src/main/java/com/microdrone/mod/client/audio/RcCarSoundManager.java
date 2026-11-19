package com.microdrone.mod.client.audio;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * マイクロRCカーのモーター走行音およびタイヤスキール音を集中管理するサウンドマネージャー
 */
public class RcCarSoundManager {

    private static RcCarSoundManager INSTANCE;
    private static RcCarDriveSound activeDriveSound = null;
    private static RcCarSkidSound activeSkidSound = null;

    public static void register() {
        if (INSTANCE == null) {
            INSTANCE = new RcCarSoundManager();
            NeoForge.EVENT_BUS.register(INSTANCE);
        }
    }

    /**
     * 操縦開始時のサウンド初期化（モーター回転音 ＋ ESC起動チャイム）
     */
    public static void startPilotingSound(RcCarEntity car) {
        Minecraft mc = Minecraft.getInstance();

        // 1. 接続確認チャイム（プロポ電源ON・ESCビープ）
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F, 0.4F));

        // 既存サウンドがあれば停止
        stopPilotingSound();

        // 2. モーター走行音のループ再生開始
        activeDriveSound = new RcCarDriveSound(car);
        mc.getSoundManager().play(activeDriveSound);
    }

    /**
     * 操縦終了時のサウンド停止
     */
    public static void stopPilotingSound() {
        if (activeDriveSound != null) {
            activeDriveSound.stopSound();
            activeDriveSound = null;
        }
        if (activeSkidSound != null) {
            activeSkidSound.stopSound();
            activeSkidSound = null;
        }
    }

    /**
     * 毎tickのサウンド状態監視・フェイルセーフおよびスキール音の動的トリガー
     */
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Pre event) {
        if (!ClientDroneInputHandler.isRcCarPiloting()) {
            if (activeDriveSound != null || activeSkidSound != null) {
                stopPilotingSound();
            }
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Entity entity = mc.level.getEntity(ClientDroneInputHandler.getActiveDroneId());
        if (!(entity instanceof RcCarEntity car) || car.isRemoved() || !car.isAlive()) {
            stopPilotingSound();
            return;
        }

        // 1. モーター音が何らかの理由で停止していた場合は再開
        if (activeDriveSound == null || activeDriveSound.isStopped()) {
            activeDriveSound = new RcCarDriveSound(car);
            mc.getSoundManager().play(activeDriveSound);
        }

        // 2. ドリフト状態の判定（スキール音のオンデマンド再生）
        boolean isGrounded = car.onGround() || car.isWheelGrounded();
        float speed = Math.abs(car.getCurrentSpeed());
        boolean isRaceDrift = car.getDriftDirection() != 0;
        boolean isRealDrift = car.isRealMode() && car.getRealPhysics() != null && car.getRealPhysics().isDrifting();
        boolean isHandbrakeSlide = car.inputHandbrake && Math.abs(car.inputSteer) > 0.20F && speed > 0.10F;
        boolean isSkidding = isGrounded && speed > 0.08F && (isRaceDrift || isRealDrift || isHandbrakeSlide) && !car.isInWater();

        if (isSkidding) {
            if (activeSkidSound == null || activeSkidSound.isStopped()) {
                activeSkidSound = new RcCarSkidSound(car);
                mc.getSoundManager().play(activeSkidSound);
            }
        }
    }
}
