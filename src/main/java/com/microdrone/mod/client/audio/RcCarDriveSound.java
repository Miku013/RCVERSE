package com.microdrone.mod.client.audio;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.RcCarEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * マイクロRCカーのブラシレスモーター回転・走行音を制御するTickableSoundInstance
 * ドローンと同様にBEE_LOOPを高ピッチ変調し、本物のブラシレスESC＆高回転モーター音を再現
 */
public class RcCarDriveSound extends AbstractTickableSoundInstance {

    private final RcCarEntity car;

    public RcCarDriveSound(RcCarEntity car) {
        super(SoundEvents.BEE_LOOP, SoundSource.PLAYERS, RandomSource.create());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.10F; // フェードイン開始
        this.pitch = 0.85F;
        this.x = car.getX();
        this.y = car.getY();
        this.z = car.getZ();
    }

    public void stopSound() {
        this.stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        // 車両が存在しない、破壊された、または操縦終了した場合は停止
        if (this.car.isRemoved() || !this.car.isAlive() || !ClientDroneInputHandler.isPiloting() || !this.car.isLocallyControlled()) {
            this.stop();
            return;
        }

        // 位置追従
        this.x = this.car.getX();
        this.y = this.car.getY();
        this.z = this.car.getZ();

        // 速度とスロットル入力の取得
        float speed = Math.abs(this.car.getCurrentSpeed());
        float throttle = Math.abs(this.car.inputForward);

        // 走行・回転数に応じたピッチ制御
        // アイドリング時: 約0.85F (低めのブラシレス通電唸り)
        // スロットル入力時のレスポンス感 (入力直後に跳ね上がり)
        // 最高速時: 最大 1.95F 〜 2.0F (甲高いブラシレス高回転モーター音)
        float speedRatio = Mth.clamp(speed / 0.85F, 0.0F, 1.0F);
        float effectiveRatio = Mth.clamp(speedRatio * 0.70F + throttle * 0.30F, 0.0F, 1.0F);
        float targetPitch = Mth.lerp(effectiveRatio, 0.85F, 1.95F);
        this.pitch = Mth.lerp(0.18F, this.pitch, targetPitch);

        // 音量制御:
        // アイドリング時: 0.18F
        // 全開走行時: 0.42F
        float targetVolume = Mth.lerp(effectiveRatio, 0.18F, 0.42F);

        // 水没時は音がこもる
        if (this.car.isInWater()) {
            targetVolume *= 0.3F;
            this.pitch *= 0.7F;
        }

        this.volume = Mth.lerp(0.12F, this.volume, targetVolume);
    }
}
