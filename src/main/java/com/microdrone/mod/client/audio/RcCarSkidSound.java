package com.microdrone.mod.client.audio;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.RcCarEntity;
import com.microdrone.mod.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * マイクロRCカーのタイヤスキール音（ドリフト・急制動・横滑り時の摩擦音）を制御するTickableSoundInstance
 */
public class RcCarSkidSound extends AbstractTickableSoundInstance {

    private final RcCarEntity car;

    public RcCarSkidSound(RcCarEntity car) {
        super(ModSounds.RC_CAR_SKID.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F; // ドリフト発生まで消音
        this.pitch = 1.0F;
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

        // 接地状態と速度
        boolean isGrounded = this.car.onGround() || this.car.isWheelGrounded();
        float speed = Math.abs(this.car.getCurrentSpeed());

        // ドリフト状態の判定:
        // 1. レースモード: driftDirection != 0
        // 2. リアルモード: realPhysics.isDrifting()
        // 3. ハンドブレーキ急旋回: inputHandbrake && ステアリング入力あり
        boolean isRaceDrift = this.car.getDriftDirection() != 0;
        boolean isRealDrift = this.car.isRealMode() && this.car.getRealPhysics() != null && this.car.getRealPhysics().isDrifting();
        boolean isHandbrakeSlide = this.car.inputHandbrake && Math.abs(this.car.inputSteer) > 0.25F && speed > 0.10F;

        boolean isSkidding = isGrounded && speed > 0.08F && (isRaceDrift || isRealDrift || isHandbrakeSlide);

        if (isSkidding) {
            // スキール音量: 速度および横滑りに応じて 0.28F 〜 0.65F
            float targetVolume = Mth.clamp(0.28F + speed * 0.40F, 0.25F, 0.65F);
            // ピッチ: 速度に応じて甲高いスキール音へ変化 (0.95F 〜 1.25F)
            float targetPitch = Mth.clamp(0.95F + speed * 0.30F, 0.90F, 1.25F);

            // 水没時はスキール音無効化
            if (this.car.isInWater()) {
                targetVolume = 0.0F;
            }

            this.volume = Mth.lerp(0.25F, this.volume, targetVolume);
            this.pitch = Mth.lerp(0.20F, this.pitch, targetPitch);
        } else {
            // ドリフト解除・グリップ復帰時は素早くフェードアウト
            this.volume = Mth.lerp(0.35F, this.volume, 0.0F);
            if (this.volume < 0.01F) {
                this.volume = 0.0F;
            }
        }
    }
}
