package com.microdrone.mod.client.audio;

import com.microdrone.mod.client.input.ClientDroneInputHandler;
import com.microdrone.mod.entity.DroneEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * ドローンの飛行・モーター回転音をリアルタイムに制御するTickableSoundInstance (Step 4 / Audio)
 */
public class DroneFlightSound extends AbstractTickableSoundInstance {

    private final DroneEntity drone;

    public DroneFlightSound(DroneEntity drone) {
        super(SoundEvents.BEE_LOOP, SoundSource.PLAYERS, RandomSource.create());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.05F; // フェードイン開始
        this.pitch = 1.2F;
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
    }

    public void stopSound() {
        this.stop();
    }

    @Override
    public void tick() {
        // ドローンが存在しない、または操縦終了した場合は停止
        if (this.drone.isRemoved() || !ClientDroneInputHandler.isPiloting()) {
            this.stop();
            return;
        }

        // 位置追従
        this.x = this.drone.getX();
        this.y = this.drone.getY();
        this.z = this.drone.getZ();

        // ドローンの速度（スロットル）に応じたピッチ・音量変化
        Vec3 vel = this.drone.getDeltaMovement();
        double speed = Math.sqrt(vel.x * vel.x + vel.y * vel.y + vel.z * vel.z);

        // ホバリング時のピッチ: 約 1.3F (アイドリング)
        // フルスピード時のピッチ: 約 2.0F (高回転ブラシレスモーターのキーン音)
        float targetPitch = (float) Mth.clamp(1.3F + speed * 1.8F, 1.2F, 2.0F);
        this.pitch = Mth.lerp(0.15F, this.pitch, targetPitch);

        // 音量: 0.2F 〜 0.45F の間で滑らかに変化
        float targetVolume = (float) Mth.clamp(0.22F + speed * 0.4F, 0.2F, 0.45F);
        this.volume = Mth.lerp(0.1F, this.volume, targetVolume);
    }
}
