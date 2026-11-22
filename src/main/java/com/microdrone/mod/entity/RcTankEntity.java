package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.DronePilotStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * プロポで遠隔操縦可能な小型RC戦車（マイクロタンク）
 * - 左右履帯の独立駆動・超信地旋回（Pivot Turn）
 * - 砲塔の360度独立旋回 & 砲身仰俯角
 * - 主砲発射・マズルフラッシュ・反動（リコイル）
 * - バッテリー・モーター換装スロット
 */
public class RcTankEntity extends Entity implements GeoEntity, RcControllable, com.microdrone.mod.decal.RcDecalHolder, com.microdrone.mod.sync.ISnapshotInterpolated {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === マルチプレイ同期用 スナップショット補間バッファ（第1段階） ===
    private final com.microdrone.mod.sync.VehicleSnapshotBuffer snapshotBuffer = new com.microdrone.mod.sync.VehicleSnapshotBuffer();

    @Override
    public com.microdrone.mod.sync.VehicleSnapshotBuffer getSnapshotBuffer() {
        return this.snapshotBuffer;
    }

    // === 同期用 EntityData ===
    private static final EntityDataAccessor<CompoundTag> DATA_DECALS =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final java.util.List<com.microdrone.mod.decal.CarDecalData> decals = new java.util.ArrayList<>();

    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> DATA_MOTOR_TIER =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_MOTOR =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.ITEM_STACK);

    // 足回り回転（左履帯・右履帯）
    private static final EntityDataAccessor<Float> DATA_LEFT_TRACK_ROT =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RIGHT_TRACK_ROT =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.FLOAT);

    // 砲塔・砲身・射撃
    private static final EntityDataAccessor<Float> DATA_TURRET_YAW =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CANNON_PITCH =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RECOIL =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_RELOAD_TICKS =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PITCH_ANGLE =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_ANGLE =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.FLOAT);

    // 機体バリアントID（外部コンフィグ用）
    private static final EntityDataAccessor<String> DATA_VARIANT_ID =
            SynchedEntityData.defineId(RcTankEntity.class, EntityDataSerializers.STRING);

    // 4点接地による車体傾斜角 (度)
    private float pitchAngle = 0.0f;
    public float prevPitchAngle = 0.0f;
    private float rollAngle = 0.0f;
    public float prevRollAngle = 0.0f;

    // 操縦者UUID
    private UUID pilotUUID = null;
    private double startX, startY, startZ;
    private java.util.UUID dummyEntityUUID = null;
    private RcPilotDummyEntity dummyEntityRef = null;
    private net.minecraft.world.level.ChunkPos lastChunkPos = null;

    // クライアント側ローカル操縦フラグ
    private boolean locallyControlled = false;
    public float clientTurretYaw = 0.0f;
    public float clientCannonPitch = 0.0f;

    // 実装アイテムスタック保持
    private ItemStack installedBattery = ItemStack.EMPTY;
    private ItemStack installedMotor = ItemStack.EMPTY;

    // 操縦入力（W/S: 前進後退, A/D: 超信地旋回/ステア, Space: 主砲発射）
    public float inputForward = 0.0f;
    public float inputSteer = 0.0f;
    public boolean inputFire = false;
    public float targetTurretYaw = 0.0f;
    public float targetCannonPitch = 0.0f;

    // 撮影モード（Cinematic）同調フラグ
    private int cinematicTicks = 0;
    public void setCinematicActive(boolean active) { this.cinematicTicks = active ? 4 : 0; }
    public boolean isCinematicActive() { return this.cinematicTicks > 0; }

    // 射撃クールダウン (ticks)
    private int fireCooldown = 0;

    // 物理シミュレーション用
    private float currentSpeed = 0.0f;
    private float leftTrackSpeed = 0.0f;
    private float rightTrackSpeed = 0.0f;

    public RcTankEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    public float maxUpStep() {
        return 0.6f; // ハーフブロックや1段差をスムーズに踏破
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    public void setLocallyControlled(boolean controlled) {
        this.locallyControlled = controlled;
    }

    public boolean isLocallyControlled() {
        if (this.level().isClientSide && com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            return false;
        }
        return this.locallyControlled;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DECALS, new CompoundTag());
        builder.define(DATA_HAS_BATTERY, false);
        builder.define(DATA_BATTERY_REMAINING, 0);
        builder.define(DATA_BATTERY_MAX, 1200);
        builder.define(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);

        builder.define(DATA_MOTOR_TIER, 1);
        builder.define(DATA_INSTALLED_MOTOR, ItemStack.EMPTY);

        builder.define(DATA_LEFT_TRACK_ROT, 0.0f);
        builder.define(DATA_RIGHT_TRACK_ROT, 0.0f);
        builder.define(DATA_TURRET_YAW, 0.0f);
        builder.define(DATA_CANNON_PITCH, 0.0f);
        builder.define(DATA_RECOIL, 0.0f);
        builder.define(DATA_RELOAD_TICKS, 0);
        builder.define(DATA_PITCH_ANGLE, 0.0f);
        builder.define(DATA_ROLL_ANGLE, 0.0f);
        builder.define(DATA_VARIANT_ID, "default");
    }

    // === Getter / Setter ===
    public boolean hasBattery() { return entityData.get(DATA_HAS_BATTERY); }
    public int getBatteryRemaining() { return entityData.get(DATA_BATTERY_REMAINING); }
    public int getBatteryMax() { return entityData.get(DATA_BATTERY_MAX); }
    public ItemStack getInstalledBattery() { return entityData.get(DATA_INSTALLED_BATTERY); }
    public int getMotorTier() { return entityData.get(DATA_MOTOR_TIER); }
    public ItemStack getInstalledMotor() { return entityData.get(DATA_INSTALLED_MOTOR); }

    public float getLeftTrackRotation() { return entityData.get(DATA_LEFT_TRACK_ROT); }
    public float getRightTrackRotation() { return entityData.get(DATA_RIGHT_TRACK_ROT); }
    public float getTurretYaw() {
        return (level().isClientSide && this.locallyControlled) ? this.clientTurretYaw : entityData.get(DATA_TURRET_YAW);
    }
    public float getCannonPitch() {
        return (level().isClientSide && this.locallyControlled) ? this.clientCannonPitch : entityData.get(DATA_CANNON_PITCH);
    }
    public float getRecoil() { return entityData.get(DATA_RECOIL); }
    public int getReloadTicks() { return entityData.get(DATA_RELOAD_TICKS); }
    public float getPitchAngle() { return this.pitchAngle; }
    public float getRollAngle() { return this.rollAngle; }
    public String getVariantId() { return entityData.get(DATA_VARIANT_ID); }
    public void setVariantId(String variantId) { entityData.set(DATA_VARIANT_ID, variantId != null ? variantId : "default"); }

    private void updateBatterySyncData() {
        if (!this.installedBattery.isEmpty() && this.installedBattery.getItem() instanceof DroneBatteryItem bat) {
            this.entityData.set(DATA_HAS_BATTERY, true);
            this.entityData.set(DATA_BATTERY_MAX, bat.getMaxFlightBlocks());
            int remaining = bat.getRemainingBlocks(this.installedBattery);
            this.entityData.set(DATA_BATTERY_REMAINING, Math.max(0, remaining));
            this.entityData.set(DATA_INSTALLED_BATTERY, this.installedBattery.copy());
        } else {
            this.entityData.set(DATA_HAS_BATTERY, false);
            this.entityData.set(DATA_BATTERY_MAX, 0);
            this.entityData.set(DATA_BATTERY_REMAINING, 0);
            this.entityData.set(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (!level().isClientSide) {
            entityData.set(DATA_RELOAD_TICKS, fireCooldown);
            entityData.set(DATA_PITCH_ANGLE, this.pitchAngle);
            entityData.set(DATA_ROLL_ANGLE, this.rollAngle);
        } else if (!this.locallyControlled) {
            this.prevPitchAngle = this.pitchAngle;
            this.prevRollAngle = this.rollAngle;
            this.pitchAngle = entityData.get(DATA_PITCH_ANGLE);
            this.rollAngle = entityData.get(DATA_ROLL_ANGLE);
        }

        // リコイル反動の減衰
        float currentRecoil = getRecoil();
        if (currentRecoil > 0.01f) {
            float newRecoil = currentRecoil * 0.75f;
            if (newRecoil < 0.01f) newRecoil = 0.0f;
            if (!level().isClientSide) {
                entityData.set(DATA_RECOIL, newRecoil);
            }
        }

        // サーバー側監視（電波距離・チャンク維持・バッテリー）
        if (!level().isClientSide && isPiloted()) {
            ServerPlayer player = (ServerPlayer) level().getPlayerByUUID(pilotUUID);
            if (player != null) {
                // 電波距離監視 (250m)
                double distToStart = Math.sqrt(this.distanceToSqr(this.startX, this.startY, this.startZ));
                if (distToStart > 250.0) {
                    player.sendSystemMessage(Component.translatable("message.microdrone.signal_lost_failsafe"));
                    this.inputForward = 0.0f;
                    this.inputSteer = 0.0f;
                    stopPiloting(player);
                    return;
                }

                // チャンク配信
                net.minecraft.world.level.ChunkPos currentChunk = new net.minecraft.world.level.ChunkPos(this.blockPosition());
                if (!currentChunk.equals(this.lastChunkPos)) {
                    this.lastChunkPos = currentChunk;
                    com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(player, this);
                }

                // バッテリー切れフェイルセーフ
                if (!hasBattery() || getBatteryRemaining() <= 0) {
                    player.sendSystemMessage(Component.translatable("message.microdrone.battery_empty_failsafe"));
                    stopPiloting(player);
                    return;
                }
            } else {
                stopPiloting(null);
                return;
            }
        }

        if (this.cinematicTicks > 0) {
            this.cinematicTicks--;
        }

        if ((this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) || (!level().isClientSide && (isPiloted() || isCinematicActive()))) {
            tickTankPhysics();
        } else if (!level().isClientSide && !isPiloted() && !isCinematicActive()) {
            // 自然落下重力
            if (!this.onGround()) {
                Vec3 currentDelta = this.getDeltaMovement();
                this.setDeltaMovement(currentDelta.x * 0.9, currentDelta.y - 0.08, currentDelta.z * 0.9);
                this.move(MoverType.SELF, this.getDeltaMovement());
            }
        }
    }

    /**
     * 戦車の履帯走行・超信地旋回・射撃物理
     */
    private void tickTankPhysics() {
        boolean hasPower = (hasBattery() && getBatteryRemaining() > 0) || isCinematicActive();
        int tier = getMotorTier();

        // 外部コンフィグから機体パラメータを取得
        com.microdrone.mod.config.tank.TankConfig tankConfig = com.microdrone.mod.config.tank.TankConfigManager.getConfig(getVariantId());
        com.microdrone.mod.config.tank.TankConfig.TankProperties props = tankConfig.properties;

        // 速度パラメータ（実機ラジコン戦車の重厚感を再現 + コンフィグ倍率適用）
        float maxSpeed = (0.06f + (tier * 0.012f)) * props.maxSpeedMultiplier;
        float accel = (0.007f + (tier * 0.002f)) * props.accelMultiplier;
        float pivotTurnSpeed = (1.2f + (tier * 0.18f)) * props.turnSpeedMultiplier; // 超信地旋回速度 (度/tick)

        float targetSpeed = 0.0f;
        if (hasPower) {
            if (inputForward > 0.1f) {
                targetSpeed = maxSpeed * inputForward;
            } else if (inputForward < -0.1f) {
                targetSpeed = -maxSpeed * 0.6f * Math.abs(inputForward); // バック
            }
        }

        // 加減速
        if (currentSpeed < targetSpeed) {
            currentSpeed = Math.min(currentSpeed + accel, targetSpeed);
        } else if (currentSpeed > targetSpeed) {
            currentSpeed = Math.max(currentSpeed - (accel * 1.5f), targetSpeed);
        }

        float yaw = this.getYRot();

        // === 超信地旋回（Pivot Turn）& ステアリング ===
        // Aキー: leftImpulse > 0 (左), Dキー: leftImpulse < 0 (右)
        if (hasPower && Math.abs(inputSteer) > 0.1f) {
            if (Math.abs(currentSpeed) < 0.02f) {
                // 【超信地旋回】: その場でピボット回転！
                float turnDeg = pivotTurnSpeed * inputSteer;
                yaw -= turnDeg;
                leftTrackSpeed = -turnDeg * 2.5f;
                rightTrackSpeed = turnDeg * 2.5f;
            } else {
                // 【走行中旋回】
                float turnFactor = 1.0f * (currentSpeed > 0 ? 1.0f : -1.0f);
                float turnDeg = turnFactor * inputSteer;
                yaw -= turnDeg;
                leftTrackSpeed = currentSpeed * 25.0f - (turnDeg * 1.5f);
                rightTrackSpeed = currentSpeed * 25.0f + (turnDeg * 1.5f);
            }
        } else {
            leftTrackSpeed = currentSpeed * 25.0f;
            rightTrackSpeed = currentSpeed * 25.0f;
        }

        this.setYRot(yaw);
        this.setYHeadRot(yaw);

        // 移動ベクトル計算
        double rad = Math.toRadians(yaw);
        double mx = -Math.sin(rad) * currentSpeed;
        double mz = Math.cos(rad) * currentSpeed;

        double my = this.getDeltaMovement().y;
        if (!this.onGround()) {
            my -= 0.08;
        } else {
            my = 0.0;
        }

        this.setDeltaMovement(mx, my, mz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 履帯・ギアの回転角を更新
        float newLeftRot = (getLeftTrackRotation() + leftTrackSpeed * 3.5f) % 360.0f;
        float newRightRot = (getRightTrackRotation() + rightTrackSpeed * 3.5f) % 360.0f;

        // 砲塔と砲身の補間（プレイヤーの照準へスムーズに追従）
        float curTurretYaw = getTurretYaw();
        float curCannonPitch = getCannonPitch();
        float newTurretYaw = curTurretYaw + Mth.wrapDegrees(targetTurretYaw - curTurretYaw) * 0.35f;
        float newCannonPitch = Mth.clamp(curCannonPitch + (targetCannonPitch - curCannonPitch) * 0.35f, -15.0f, 35.0f);

        if (level().isClientSide) {
            this.clientTurretYaw = newTurretYaw;
            this.clientCannonPitch = newCannonPitch;
        } else {
            entityData.set(DATA_LEFT_TRACK_ROT, newLeftRot);
            entityData.set(DATA_RIGHT_TRACK_ROT, newRightRot);
            entityData.set(DATA_TURRET_YAW, newTurretYaw);
            entityData.set(DATA_CANNON_PITCH, newCannonPitch);

            // バッテリー消費
            if (hasPower && (Math.abs(currentSpeed) > 0.01f || Math.abs(inputSteer) > 0.1f)) {
                if (tickCount % 20 == 0) {
                    consumeBattery(1);
                }
            }

            // === 主砲射撃 ===
            if (inputFire && fireCooldown == 0 && hasPower) {
                fireCannon();
                fireCooldown = props.reloadTicks;
            }
        }

        // 【接地ポイント4点方式】地形の凹凸・傾斜に合わせたリアルなピッチ＆ロール傾斜物理
        updateGroundTiltPhysics();

        // マルチプレイ同期: Snapshot Interpolation用パケットを周囲のプレイヤーへ配信（第1段階）
        if (!this.level().isClientSide) {
            boolean isMoving = this.isPiloted() || Math.abs(this.currentSpeed) > 0.005f || Math.abs(this.inputSteer) > 0.1f;
            if (isMoving || (this.tickCount % 4 == 0)) {
                PacketDistributor.sendToPlayersTrackingEntity(this, new com.microdrone.mod.network.RcVehicleSnapshotPayload(
                        this.getId(),
                        this.tickCount,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        this.getYRot(),
                        this.getPitchAngle(),
                        this.getRollAngle(),
                        (float) this.getDeltaMovement().x,
                        (float) this.getDeltaMovement().y,
                        (float) this.getDeltaMovement().z
                ));
            }
        }
    }

    private void updateGroundTiltPhysics() {
        this.prevPitchAngle = this.pitchAngle;
        this.prevRollAngle = this.rollAngle;

        if (!this.onGround()) {
            this.pitchAngle = Mth.lerp(0.15f, this.pitchAngle, 0.0f);
            this.rollAngle = Mth.lerp(0.15f, this.rollAngle, 0.0f);
            return;
        }

        float yaw = this.getYRot();
        double rad = Math.toRadians(yaw);
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = -Math.cos(rad);
        double rightZ = -Math.sin(rad);

        double halfL = 0.22; // 前後スパン
        double halfW = 0.16; // 左右スパン

        double px = getX();
        double py = getY();
        double pz = getZ();

        double hFL = sampleGroundY(px + fwdX * halfL - rightX * halfW, py, pz + fwdZ * halfL - rightZ * halfW);
        double hFR = sampleGroundY(px + fwdX * halfL + rightX * halfW, py, pz + fwdZ * halfL + rightZ * halfW);
        double hRL = sampleGroundY(px - fwdX * halfL - rightX * halfW, py, pz - fwdZ * halfL - rightZ * halfW);
        double hRR = sampleGroundY(px - fwdX * halfL + rightX * halfW, py, pz - fwdZ * halfL + rightZ * halfW);

        double hFront = (hFL + hFR) * 0.5;
        double hRear = (hRL + hRR) * 0.5;
        double deltaPitch = hRear - hFront;
        float targetPitch = (float) Math.toDegrees(Math.atan2(deltaPitch, halfL * 2.0));

        double hLeft = (hFL + hRL) * 0.5;
        double hRight = (hFR + hRR) * 0.5;
        double deltaRoll = hLeft - hRight;
        float targetRoll = (float) Math.toDegrees(Math.atan2(deltaRoll, halfW * 2.0));

        targetPitch = Mth.clamp(targetPitch, -28.0f, 28.0f);
        targetRoll = Mth.clamp(targetRoll, -28.0f, 28.0f);

        this.pitchAngle = Mth.lerp(0.35f, this.pitchAngle, targetPitch);
        this.rollAngle = Mth.lerp(0.35f, this.rollAngle, targetRoll);
    }

    private double sampleGroundY(double x, double y, double z) {
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(x, y + 0.35, z);
        net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(x, y - 0.55, z);
        net.minecraft.world.phys.BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this
        ));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return hit.getLocation().y;
        }
        return y;
    }

    /**
     * 主砲発射処理
     */
    public void fireCannon() {
        if (level().isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) level();

        // 砲身の方向ベクトル（車体Yaw + 砲塔Yaw, CannonPitch）
        // Minecraft標準仕様: 上向きが負(マイナス)、下向きが正(プラス)
        float totalYaw = this.getYRot() + getTurretYaw();
        float pitch = getCannonPitch();

        double yawRad = Math.toRadians(totalYaw);
        double pitchRad = Math.toRadians(pitch);

        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad); // pitchが負（上向き）のとき -sin(負) = 正（上向きベクトル）
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);

        // 砲口の位置（車体Hitbox外側へしっかり離し、自爆や地面接触を完全防止）
        double muzzleDist = 0.65;
        double muzzleX = getX() + dirX * muzzleDist;
        double muzzleY = getY() + 0.38 + dirY * muzzleDist;
        double muzzleZ = getZ() + dirZ * muzzleDist;

        // 【カスタム発砲音】迫真の戦車主砲発射サウンドを広域再生（可聴半径256m）
        // volume=16.0f により、最大電波範囲(250m)のどこに戦車がいてもサウンドパケットが操縦者へ確実に到達！
        // - 車載チェイス/ガンナー視点: クライアント側リスナーが戦車至近(0〜2m)にあるため、常に100%の大迫力至近距離音で再生
        // - 地上プロポ視点: リスナーがプレイヤー位置にあるため、OpenALにより戦車との距離に応じて自然に減衰（遠くなら小さく）
        serverLevel.playSound(null, getX(), getY(), getZ(), com.microdrone.mod.sound.ModSounds.TANK_CANNON_FIRE.get(), SoundSource.NEUTRAL, 16.0f, 1.0f);

        // マズルフラッシュ・煙
        serverLevel.sendParticles(ParticleTypes.FLASH, muzzleX, muzzleY, muzzleZ, 1, 0, 0, 0, 0);
        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzleX, muzzleY, muzzleZ, 4, dirX * 0.1, dirY * 0.1, dirZ * 0.1, 0.05);

        // 【衝撃波演出】車体直下の地面における砂埃・土煙パーティクル
        BlockPos groundPos = BlockPos.containing(getX(), getY() - 0.1, getZ());
        BlockState groundState = serverLevel.getBlockState(groundPos);
        if (groundState.isAir()) {
            groundPos = groundPos.below();
            groundState = serverLevel.getBlockState(groundPos);
        }
        if (!groundState.isAir()) {
            // 接地ブロックに応じたリアルな砂埃・土煙（足元から放射状に低く拡散）
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                    getX(), getY() + 0.05, getZ(),
                    10, 0.35, 0.02, 0.35, 0.08
            );
        }
        // 低空をふわりと漂う衝撃波スモーク
        serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.05, getZ(), 4, 0.25, 0.01, 0.25, 0.03);

        // 砲口（マズル）直下の地面にも衝撃風による砂埃を生成
        BlockPos muzzleGroundPos = BlockPos.containing(muzzleX, getY() - 0.1, muzzleZ);
        BlockState muzzleGroundState = serverLevel.getBlockState(muzzleGroundPos);
        if (muzzleGroundState.isAir()) {
            muzzleGroundState = serverLevel.getBlockState(muzzleGroundPos.below());
        }
        if (!muzzleGroundState.isAir()) {
            serverLevel.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, muzzleGroundState),
                    muzzleX, getY() + 0.05, muzzleZ,
                    6, 0.25, 0.02, 0.25, 0.06
            );
        }

        // 反動（リコイル）
        entityData.set(DATA_RECOIL, 1.0f);

        // 車体のノックバック（重厚な戦車に合わせて微小な揺れに抑制）
        this.setDeltaMovement(this.getDeltaMovement().add(-dirX * 0.04, 0.02, -dirZ * 0.04));

        // 砲弾スポーン（小型火球）
        Vec3 velocity = new Vec3(dirX, dirY, dirZ).scale(1.2);
        SmallFireball fireball = new SmallFireball(serverLevel, muzzleX, muzzleY, muzzleZ, velocity);
        fireball.setDeltaMovement(velocity);
        // 【自爆防止】戦車自身および操縦者をOwnerにして直後の衝突・誤爆を完全防止！
        if (pilotUUID != null) {
            Player pilot = level().getPlayerByUUID(pilotUUID);
            if (pilot != null) {
                fireball.setOwner(pilot);
            } else {
                fireball.setOwner(this);
            }
        } else {
            fireball.setOwner(this);
        }
        serverLevel.addFreshEntity(fireball);

        // 発砲時バッテリー消費
        consumeBattery(5);
    }

    public void consumeBattery(int drain) {
        if (!this.installedBattery.isEmpty() && this.installedBattery.getItem() instanceof DroneBatteryItem bat) {
            bat.consumeFlightDistance(this.installedBattery, drain);
            updateBatterySyncData();

            if (getBatteryRemaining() <= 0 && isPiloted()) {
                ServerPlayer player = (ServerPlayer) level().getPlayerByUUID(pilotUUID);
                stopPiloting(player);
            }
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);

        // 0. ハサミでステッカー全剥がし
        ItemStack heldItem = player.getItemInHand(hand);
        if (heldItem.is(net.minecraft.world.item.Items.SHEARS)) {
            if (!this.getDecals().isEmpty()) {
                this.clearDecals();
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.car_decal_cleared"));
                return InteractionResult.SUCCESS;
            }
        }

        // 1. スニーク右クリック: 回収
        if (player.isShiftKeyDown() && !isPiloted()) {
            dropVehicle(player.getAbilities().instabuild);
            level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            return InteractionResult.SUCCESS;
        }

        // 2. バッテリー装着
        if (held.getItem() instanceof DroneBatteryItem batteryItem) {
            if (this.installedBattery.isEmpty()) {
                this.installedBattery = held.split(1);
                updateBatterySyncData();

                level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.7f, 1.6f);
                player.sendSystemMessage(Component.translatable("message.microdrone.battery_installed",
                        batteryItem.getRemainingBlocks(this.installedBattery), batteryItem.getMaxFlightBlocks()));
                return InteractionResult.SUCCESS;
            } else {
                player.sendSystemMessage(Component.translatable("message.microdrone.battery_already_installed"));
                return InteractionResult.CONSUME;
            }
        }

        // 3. モーター装着
        if (held.getItem() instanceof DroneMotorItem motorItem) {
            this.installedMotor = held.split(1);
            entityData.set(DATA_MOTOR_TIER, motorItem.getTier());
            entityData.set(DATA_INSTALLED_MOTOR, this.installedMotor.copy());

            level().playSound(null, blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5f, 1.8f);
            player.sendSystemMessage(Component.translatable("message.microdrone.motor_installed", motorItem.getTier()));
            return InteractionResult.SUCCESS;
        }

        // 4. 手ぶら右クリック: バッテリー取り出し
        if (held.isEmpty() && !this.installedBattery.isEmpty()) {
            if (!player.getInventory().add(this.installedBattery)) {
                level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), this.installedBattery));
            }
            this.installedBattery = ItemStack.EMPTY;
            updateBatterySyncData();

            level().playSound(null, blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.PLAYERS, 0.7f, 1.2f);
            player.sendSystemMessage(Component.translatable("message.microdrone.battery_removed"));
            return InteractionResult.SUCCESS;
        }

        return super.interact(player, hand);
    }

    // === RcControllable 実装 ===
    @Override
    public boolean isPiloted() { return this.pilotUUID != null; }

    @Override
    public boolean isPilotedBy(ServerPlayer player) {
        return player != null && player.getUUID().equals(this.pilotUUID);
    }

    @Override
    public UUID getPilotUUID() { return this.pilotUUID; }

    @Override
    public boolean startPiloting(ServerPlayer player) {
        if (this.pilotUUID != null) return false;
        if (!hasBattery() || getBatteryRemaining() <= 0) {
            player.sendSystemMessage(Component.translatable("message.microdrone.no_battery"));
            return false;
        }

        this.pilotUUID = player.getUUID();
        this.startX = player.getX();
        this.startY = player.getY();
        this.startZ = player.getZ();

        if (com.microdrone.mod.cinematic.CinematicRecordingManager.isSpawnDummy()) {
            ServerLevel serverLevel = player.serverLevel();
            RcPilotDummyEntity dummy = new RcPilotDummyEntity(ModEntities.RC_PILOT_DUMMY.get(), serverLevel);
            dummy.moveTo(this.startX, this.startY, this.startZ, player.getYRot(), player.getXRot());
            dummy.setupFromPlayer(player);
            serverLevel.addFreshEntity(dummy);
            this.dummyEntityRef = dummy;
            this.dummyEntityUUID = dummy.getUUID();
            RcVehicleManager.registerDummy(dummy.getUUID(), this);
        }

        RcVehicleManager.registerPiloting(this.pilotUUID, this);

        PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), true, startX, startY, startZ));
        level().playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f);
        return true;
    }

    @Override
    public void stopPiloting(ServerPlayer player) {
        if (this.pilotUUID == null) return;
        UUID oldPilotUUID = this.pilotUUID;
        this.pilotUUID = null;

        RcVehicleManager.unregisterPiloting(oldPilotUUID);

        if (this.dummyEntityRef != null && !this.dummyEntityRef.isRemoved()) {
            this.dummyEntityRef.discard();
            this.dummyEntityRef = null;
        } else if (this.dummyEntityUUID != null && player != null && player.level() instanceof ServerLevel sl) {
            net.minecraft.world.entity.Entity e = sl.getEntity(this.dummyEntityUUID);
            if (e != null) e.discard();
        }
        if (this.dummyEntityUUID != null) {
            RcVehicleManager.unregisterDummy(this.dummyEntityUUID);
            this.dummyEntityUUID = null;
        }

        if (player != null) {
            PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(-1, false, 0, 0, 0));
        }

        this.inputForward = 0.0f;
        this.inputSteer = 0.0f;
        this.inputFire = false;
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains("VariantId", 8)) {
            setVariantId(compound.getString("VariantId"));
        }
        if (compound.contains("InstalledBattery", 10)) {
            this.installedBattery = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledBattery")).orElse(ItemStack.EMPTY);
            updateBatterySyncData();
        }
        if (compound.contains("InstalledMotor", 10)) {
            this.installedMotor = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledMotor")).orElse(ItemStack.EMPTY);
            if (this.installedMotor.getItem() instanceof DroneMotorItem motorItem) {
                entityData.set(DATA_MOTOR_TIER, motorItem.getTier());
                entityData.set(DATA_INSTALLED_MOTOR, this.installedMotor.copy());
            }
        }
        if (compound.contains("Decals", 10)) {
            setDecals(com.microdrone.mod.decal.CarDecalData.deserializeDecals(compound.getCompound("Decals")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putString("VariantId", getVariantId());
        if (!this.installedBattery.isEmpty()) {
            compound.put("InstalledBattery", this.installedBattery.save(this.registryAccess()));
        }
        if (!this.installedMotor.isEmpty()) {
            compound.put("InstalledMotor", this.installedMotor.save(this.registryAccess()));
        }
        if (!this.decals.isEmpty()) {
            compound.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals));
        }
    }

    @Override
    public boolean fireImmune() {
        // 重装甲RC戦車は火炎・延焼・地面の火に対して完全耐性を持つ（自弾の着火による即死を防止）
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        // 壁埋まり（サフォケーション）ダメージは無効化
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) {
            return false;
        }
        if (!level().isClientSide && !this.isRemoved()) {
            if (this.pilotUUID != null && this.level() instanceof ServerLevel sl) {
                ServerPlayer p = (ServerPlayer) sl.getPlayerByUUID(this.pilotUUID);
                if (p != null) {
                    this.stopPiloting(p);
                }
            }
            dropVehicle(source.getEntity() instanceof Player p && p.getAbilities().instabuild);
            return true;
        }
        return false;
    }

    public void dropVehicle(boolean isCreativePlayer) {
        if (!level().isClientSide && !this.isRemoved()) {
            if (!isCreativePlayer) {
                ItemStack dropStack = new ItemStack(ModItems.RC_TANK_ITEM.get());
                if (!"default".equalsIgnoreCase(getVariantId())) {
                    net.minecraft.world.item.component.CustomData.update(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            dropStack,
                            tag -> tag.putString("Variant", getVariantId())
                    );
                    var config = com.microdrone.mod.config.tank.TankConfigManager.getConfig(getVariantId());
                    if (config != null && config.displayName != null) {
                        dropStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(config.displayName));
                    }
                }
                if (!this.decals.isEmpty()) {
                    net.minecraft.world.item.component.CustomData.update(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            dropStack,
                            tag -> tag.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals))
                    );
                }
                ItemEntity drop = new ItemEntity(level(), getX(), getY() + 0.2, getZ(), dropStack);
                level().addFreshEntity(drop);

                // バッテリーとモーターも返却
                if (!this.installedBattery.isEmpty()) {
                    level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 0.2, getZ(), this.installedBattery));
                    this.installedBattery = ItemStack.EMPTY;
                }
                if (!this.installedMotor.isEmpty()) {
                    level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 0.2, getZ(), this.installedMotor));
                    this.installedMotor = ItemStack.EMPTY;
                }
            }
            level().playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1.0f);
            this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.pilotUUID != null) {
            ServerPlayer pilot = this.getServer() != null ? this.getServer().getPlayerList().getPlayer(this.pilotUUID) : null;
            if (pilot != null) {
                this.stopPiloting(pilot);
            } else {
                RcVehicleManager.unregisterPiloting(this.pilotUUID);
                if (this.dummyEntityUUID != null) {
                    RcVehicleManager.unregisterDummy(this.dummyEntityUUID);
                }
            }
        }
        super.remove(reason);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // === デカール (ステッカー) 管理 ===
    @Override
    public java.util.List<com.microdrone.mod.decal.CarDecalData> getDecals() {
        if (!this.decals.isEmpty()) {
            return this.decals;
        }
        if (this.level().isClientSide) {
            CompoundTag syncTag = this.entityData.get(DATA_DECALS);
            java.util.List<com.microdrone.mod.decal.CarDecalData> list = com.microdrone.mod.decal.CarDecalData.deserializeDecals(syncTag);
            if (!list.isEmpty()) {
                this.decals.clear();
                this.decals.addAll(list);
            }
            return this.decals;
        }
        return this.decals;
    }

    @Override
    public void setDecals(java.util.List<com.microdrone.mod.decal.CarDecalData> newDecals) {
        this.decals.clear();
        if (newDecals != null) {
            this.decals.addAll(newDecals);
        }
        this.entityData.set(DATA_DECALS, com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals));
    }

    @Override
    public void addDecal(com.microdrone.mod.decal.CarDecalData decal) {
        this.decals.add(decal);
        this.entityData.set(DATA_DECALS, com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals));
    }

    @Override
    public void clearDecals() {
        this.decals.clear();
        this.entityData.set(DATA_DECALS, new CompoundTag());
    }
}
