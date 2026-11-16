package com.microdrone.mod.client.audio;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * マイクロRCカーのモーター回転・走行音を制御するTickableSoundInstance
 */
public class RcCarDriveSound extends AbstractTickableSoundInstance {

    private final RcCarEntity car;

    public RcCarDriveSound(RcCarEntity car) {
        super(ModSounds.RC_CAR_DRIVE.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05F; // フェードイン開始
        this.pitch = 0.8F;
        this.x = car.getX();
        this.y = car.getY();
        this.z = car.getZ();
    }

    public void stopSound() {
        this.stop();
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
        // アイドリング時: 約0.8F
        // スロットル入力と実速度の合算により、踏み込み時のレスポンスと最高速時の高回転モーター音（最大1.85F）を表現
        float speedRatio = Mth.clamp(speed / 0.85F, 0.0F, 1.0F);
        float effectiveRatio = Mth.clamp(speedRatio * 0.75F + throttle * 0.25F, 0.0F, 1.0F);
        float targetPitch = Mth.lerp(effectiveRatio, 0.78F, 1.85F);
        this.pitch = Mth.lerp(0.18F, this.pitch, targetPitch);

        // 音量制御:
        // アイドリング時: 0.20F（待機音）
        // 走行時: 0.45F 〜 0.55F
        float targetVolume = Mth.lerp(effectiveRatio, 0.20F, 0.50F);

        // 水没時は音がこもる
        if (this.car.isInWater()) {
            targetVolume *= 0.3F;
            this.pitch *= 0.7F;
        }

        this.volume = Mth.lerp(0.12F, this.volume, targetVolume);
    }
}
