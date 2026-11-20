package com.microdrone.mod.entity.spider;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.entity.ModEntities;
import com.microdrone.mod.entity.RcControllable;
import com.microdrone.mod.entity.RcVehicleManager;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * 6本足の多脚ロボット（RCヘキサポッド）
 * - プロシージャル歩行IK（下向きレイキャストによる個別接地）
 * - 三脚歩行（トライポッド・ゲイト：交互3本ステップ）
 * - 1ブロック段差の自動乗り越え（高い悪路走破性）
 * - 360度独立旋回タレット & 射撃アクション
 * - プロポ（送信機）による遠隔操縦 & FPVカメラ
 */
public class RcSpiderEntity extends Entity implements GeoEntity, RcControllable {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === 同期用 EntityData ===
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> DATA_MOTOR_TIER =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_MOTOR =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.ITEM_STACK);

    // 砲塔ヨー角・リコイル
    private static final EntityDataAccessor<Float> DATA_TURRET_YAW =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_RECOIL_TICKS =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.INT);

    // 車体ピッチ・ロール角
    private static final EntityDataAccessor<Float> DATA_PITCH_ANGLE =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_ANGLE =
            SynchedEntityData.defineId(RcSpiderEntity.class, EntityDataSerializers.FLOAT);

    // 操縦者UUID
    private UUID pilotUUID = null;

    // 操縦入力値
    public float inputThrottle = 0.0f;
    public float inputSteer = 0.0f;
    public float inputStrafe = 0.0f;
    public boolean inputFire = false;
    public float targetTurretYaw = 0.0f;

    // クライアント補間用
    public float clientTurretYaw = 0.0f;
    public float prevTurretYaw = 0.0f;
    public float pitchAngle = 0.0f;
    public float prevPitchAngle = 0.0f;
    public float rollAngle = 0.0f;
    public float prevRollAngle = 0.0f;

    // 射撃クールダウン
    private int shootCooldown = 0;

    // === プロシージャル歩行IKデータ ===
    public static final int LEG_COUNT = 6;
    // 脚インデックス定数
    public static final int LEG_FL = 0; // 左前
    public static final int LEG_ML = 1; // 左中
    public static final int LEG_RL = 2; // 左後
    public static final int LEG_FR = 3; // 右前
    public static final int LEG_MR = 4; // 右中
    public static final int LEG_RR = 5; // 右後

    /**
     * 各脚のプロシージャル歩行状態
     */
    public static class LegState {
        public final int index;
        public final int group; // 0=グループA(FL, MR, RL), 1=グループB(FR, ML, RR)
        public final Vec3 localBaseOffset;   // 胴体中心からの付け根位置 (ブロック単位)
        public final Vec3 localFootDefault;  // 胴体中心からの標準足先位置 (ブロック単位)

        // ワールド座標系での足先位置
        public Vec3 currentWorldFootPos = Vec3.ZERO;
        public Vec3 targetWorldFootPos = Vec3.ZERO;
        public Vec3 stepStartWorldPos = Vec3.ZERO;

        public boolean isStepping = false;
        public float stepProgress = 1.0f; // 0.0f -> 1.0f

        // モデルレンダラーへの受け渡し用（胴体基準のローカル相対変位）
        public float animLiftY = 0.0f;
        public float animSwingX = 0.0f;
        public float animSwingZ = 0.0f;

        public LegState(int index, int group, Vec3 localBaseOffset, Vec3 localFootDefault) {
            this.index = index;
            this.group = group;
            this.localBaseOffset = localBaseOffset;
            this.localFootDefault = localFootDefault;
        }
    }

    public final LegState[] legs = new LegState[LEG_COUNT];
    private int activeStepGroup = 0; // 現在ステップ可能なグループ (0 または 1)
    private float stepCycleTimer = 0.0f;

    public RcSpiderEntity(EntityType<? extends RcSpiderEntity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;

        // 脚の初期化 (16単位 = 1m に換算)
        // 左側: X > 0, 右側: X < 0
        legs[LEG_FL] = new LegState(LEG_FL, 0, new Vec3(0.5, 1.06, -0.44), new Vec3(1.5, 0.0, -0.7));
        legs[LEG_ML] = new LegState(LEG_ML, 1, new Vec3(0.5, 1.06, 0.0),   new Vec3(1.6, 0.0, 0.0));
        legs[LEG_RL] = new LegState(LEG_RL, 0, new Vec3(0.5, 1.06, 0.44),  new Vec3(1.5, 0.0, 0.7));

        legs[LEG_FR] = new LegState(LEG_FR, 1, new Vec3(-0.5, 1.06, -0.44), new Vec3(-1.5, 0.0, -0.7));
        legs[LEG_MR] = new LegState(LEG_MR, 0, new Vec3(-0.5, 1.06, 0.0),   new Vec3(-1.6, 0.0, 0.0));
        legs[LEG_RR] = new LegState(LEG_RR, 1, new Vec3(-0.5, 1.06, 0.44),  new Vec3(-1.5, 0.0, 0.7));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HAS_BATTERY, false);
        builder.define(DATA_BATTERY_REMAINING, 0);
        builder.define(DATA_BATTERY_MAX, 12000);
        builder.define(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);

        builder.define(DATA_MOTOR_TIER, 1);
        builder.define(DATA_INSTALLED_MOTOR, ItemStack.EMPTY);

        builder.define(DATA_TURRET_YAW, 0.0f);
        builder.define(DATA_RECOIL_TICKS, 0);
        builder.define(DATA_PITCH_ANGLE, 0.0f);
        builder.define(DATA_ROLL_ANGLE, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();

        this.prevPitchAngle = this.pitchAngle;
        this.prevRollAngle = this.rollAngle;
        this.prevTurretYaw = this.clientTurretYaw;

        if (this.level().isClientSide) {
            this.clientTurretYaw = Mth.rotLerp(0.3f, this.clientTurretYaw, this.entityData.get(DATA_TURRET_YAW));
            this.pitchAngle = Mth.rotLerp(0.2f, this.pitchAngle, this.entityData.get(DATA_PITCH_ANGLE));
            this.rollAngle = Mth.rotLerp(0.2f, this.rollAngle, this.entityData.get(DATA_ROLL_ANGLE));
        }

        // リコイルタイマー減衰
        int recoil = this.entityData.get(DATA_RECOIL_TICKS);
        if (recoil > 0) {
            this.entityData.set(DATA_RECOIL_TICKS, recoil - 1);
        }

        if (shootCooldown > 0) {
            shootCooldown--;
        }

        if (!this.level().isClientSide) {
            serverTickPhysics();
        }

        // プロシージャル歩行IKの更新（クライアント・サーバー両方で計算して足位置を追従）
        updateProceduralLegs();
    }

    /**
     * サーバー側物理・移動処理
     */
    private void serverTickPhysics() {
        boolean hasBattery = this.entityData.get(DATA_HAS_BATTERY);
        int battery = this.entityData.get(DATA_BATTERY_REMAINING);

        if (!hasBattery || battery <= 0) {
            this.inputThrottle = 0.0f;
            this.inputSteer = 0.0f;
            this.inputStrafe = 0.0f;
        }

        float motorSpeedMultiplier = 1.0f + (this.entityData.get(DATA_MOTOR_TIER) - 1) * 0.25f;

        // 旋回処理 (Yaw)
        float turnSpeed = 3.5f * motorSpeedMultiplier;
        if (Math.abs(this.inputSteer) > 0.05f) {
            this.setYRot(this.getYRot() + this.inputSteer * turnSpeed);
        }

        // 前後・左右の移動ベクトル
        float speed = 0.12f * motorSpeedMultiplier;
        float forwardMove = this.inputThrottle * speed;
        float strafeMove = this.inputStrafe * (speed * 0.7f);

        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double motionX = -Mth.sin(yawRad) * forwardMove + Mth.cos(yawRad) * strafeMove;
        double motionZ = Mth.cos(yawRad) * forwardMove + Mth.sin(yawRad) * strafeMove;

        // 重力
        Vec3 currentDelta = this.getDeltaMovement();
        double motionY = currentDelta.y - 0.08;
        if (this.onGround()) {
            motionY = 0.0;
        }

        // 移動適用
        this.setDeltaMovement(new Vec3(motionX, motionY, motionZ));
        this.move(MoverType.SELF, this.getDeltaMovement());

        // バッテリー消費
        if (isPiloted() && hasBattery && (Math.abs(inputThrottle) > 0.05f || Math.abs(inputSteer) > 0.05f || Math.abs(inputStrafe) > 0.05f)) {
            if (this.tickCount % 20 == 0) {
                consumeBattery(1);
            }
        }

        // 射撃処理
        if (inputFire && shootCooldown <= 0 && hasBattery && battery > 0) {
            fireCannon();
            shootCooldown = 20; // 1秒間隔
        }

        // タレット角度同期
        this.entityData.set(DATA_TURRET_YAW, this.targetTurretYaw);
    }

    /**
     * プロシージャル歩行IK（地形レイキャスト＆三脚歩行追従）
     */
    private void updateProceduralLegs() {
        Vec3 bodyPos = this.position();
        float bodyYaw = this.getYRot();
        float yawRad = bodyYaw * Mth.DEG_TO_RAD;

        boolean isMoving = Math.abs(inputThrottle) > 0.05f || Math.abs(inputSteer) > 0.05f || Math.abs(inputStrafe) > 0.05f || this.getDeltaMovement().horizontalDistanceSqr() > 0.001;

        if (isMoving) {
            stepCycleTimer += 0.2f;
            if (stepCycleTimer >= 1.0f) {
                stepCycleTimer = 0.0f;
                activeStepGroup = 1 - activeStepGroup; // グループA <-> グループB を交互に切り替え
            }
        }

        for (int i = 0; i < LEG_COUNT; i++) {
            LegState leg = legs[i];

            // 胴体座標から理想のデフォルト足先位置をワールド座標系に変換
            Vec3 defaultOffset = leg.localFootDefault;
            double rotatedOffsetX = defaultOffset.x * Mth.cos(yawRad) - defaultOffset.z * Mth.sin(yawRad);
            double rotatedOffsetZ = defaultOffset.x * Mth.sin(yawRad) + defaultOffset.z * Mth.cos(yawRad);

            // 移動先予測（移動方向へ少し先出し）
            Vec3 vel = this.getDeltaMovement();
            double leadX = vel.x * 4.0;
            double leadZ = vel.z * 4.0;

            Vec3 idealWorldPos = new Vec3(bodyPos.x + rotatedOffsetX + leadX, bodyPos.y, bodyPos.z + rotatedOffsetZ + leadZ);

            // 地面への下向きレイキャスト
            Vec3 rayStart = new Vec3(idealWorldPos.x, bodyPos.y + 1.2, idealWorldPos.z);
            Vec3 rayEnd = new Vec3(idealWorldPos.x, bodyPos.y - 1.5, idealWorldPos.z);
            BlockHitResult hit = this.level().clip(new ClipContext(rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

            double groundY = (hit.getType() == HitResult.Type.BLOCK) ? hit.getLocation().y : bodyPos.y;
            Vec3 targetGroundPos = new Vec3(idealWorldPos.x, groundY, idealWorldPos.z);

            // 初回初期化
            if (leg.currentWorldFootPos == Vec3.ZERO) {
                leg.currentWorldFootPos = targetGroundPos;
                leg.targetWorldFootPos = targetGroundPos;
            }

            // ステップ判定: 自分のグループの番で、目標位置から離れたら踏み出す
            double distToTarget = leg.currentWorldFootPos.distanceTo(targetGroundPos);
            if (!leg.isStepping && leg.group == activeStepGroup && distToTarget > 0.35 && isMoving) {
                leg.isStepping = true;
                leg.stepProgress = 0.0f;
                leg.stepStartWorldPos = leg.currentWorldFootPos;
                leg.targetWorldFootPos = targetGroundPos;
            }

            // ステップ実行中アニメーション補間
            if (leg.isStepping) {
                leg.stepProgress += 0.25f;
                if (leg.stepProgress >= 1.0f) {
                    leg.stepProgress = 1.0f;
                    leg.isStepping = false;
                    leg.currentWorldFootPos = leg.targetWorldFootPos;

                    // 着地時の微小土煙パーティクル
                    if (this.level().isClientSide) {
                        this.level().addParticle(ParticleTypes.POOF, leg.currentWorldFootPos.x, leg.currentWorldFootPos.y + 0.05, leg.currentWorldFootPos.z, 0, 0.02, 0);
                    }
                } else {
                    // 放物線状に持ち上げて着地
                    Vec3 horizLerp = leg.stepStartWorldPos.lerp(leg.targetWorldFootPos, leg.stepProgress);
                    double liftHeight = Math.sin(leg.stepProgress * Math.PI) * 0.35; // 35cm 持ち上げる
                    leg.currentWorldFootPos = new Vec3(horizLerp.x, horizLerp.y + liftHeight, horizLerp.z);
                }
            }

            // モデルレンダラー用のローカル変位計算
            double relY = leg.currentWorldFootPos.y - bodyPos.y;
            leg.animLiftY = (float) relY;
        }

        // 6本の足の接地高さから車体のピッチ・ロール傾斜角を計算
        calculateBodyTilt();
    }

    /**
     * 接地脚の高さから車体の傾斜（ピッチ・ロール）を計算
     */
    private void calculateBodyTilt() {
        double frontY = (legs[LEG_FL].currentWorldFootPos.y + legs[LEG_FR].currentWorldFootPos.y) * 0.5;
        double rearY = (legs[LEG_RL].currentWorldFootPos.y + legs[LEG_RR].currentWorldFootPos.y) * 0.5;
        double leftY = (legs[LEG_FL].currentWorldFootPos.y + legs[LEG_ML].currentWorldFootPos.y + legs[LEG_RL].currentWorldFootPos.y) / 3.0;
        double rightY = (legs[LEG_FR].currentWorldFootPos.y + legs[LEG_MR].currentWorldFootPos.y + legs[LEG_RR].currentWorldFootPos.y) / 3.0;

        float targetPitch = (float) Math.toDegrees(Math.atan2(rearY - frontY, 1.4));
        float targetRoll = (float) Math.toDegrees(Math.atan2(leftY - rightY, 1.6));

        this.pitchAngle = Mth.rotLerp(0.15f, this.pitchAngle, targetPitch);
        this.rollAngle = Mth.rotLerp(0.15f, this.rollAngle, targetRoll);

        if (!this.level().isClientSide) {
            this.entityData.set(DATA_PITCH_ANGLE, this.pitchAngle);
            this.entityData.set(DATA_ROLL_ANGLE, this.rollAngle);
        }
    }

    /**
     * 主砲射撃
     */
    private void fireCannon() {
        float turretYaw = this.entityData.get(DATA_TURRET_YAW);
        float totalYawRad = (this.getYRot() + turretYaw) * Mth.DEG_TO_RAD;

        double dirX = -Mth.sin(totalYawRad);
        double dirY = 0.05;
        double dirZ = Mth.cos(totalYawRad);

        Vec3 muzzlePos = this.position().add(dirX * 0.9, 0.7, dirZ * 0.9);

        Vec3 velocity = new Vec3(dirX, dirY, dirZ).normalize().scale(1.2);
        SmallFireball fireball = new SmallFireball(this.level(), muzzlePos.x, muzzlePos.y, muzzlePos.z, velocity);
        fireball.setDeltaMovement(velocity);
        if (this.pilotUUID != null) {
            Player pilot = this.level().getPlayerByUUID(this.pilotUUID);
            if (pilot != null) {
                fireball.setOwner(pilot);
            }
        }
        this.level().addFreshEntity(fireball);

        // 反動トリガー
        this.entityData.set(DATA_RECOIL_TICKS, 5);

        // サウンド & マズルフラッシュ
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.8f, 1.5f);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH, muzzlePos.x, muzzlePos.y, muzzlePos.z, 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzlePos.x, muzzlePos.y, muzzlePos.z, 4, 0.1, 0.1, 0.1, 0.02);
        }

        consumeBattery(2);
    }

    // === バッテリー・モーター管理 ===
    public boolean installBattery(ItemStack batteryItem, Player player) {
        if (!(batteryItem.getItem() instanceof DroneBatteryItem item)) return false;
        if (this.entityData.get(DATA_HAS_BATTERY)) return false;

        ItemStack installed = batteryItem.split(1);
        int max = item.getMaxFlightBlocks();
        int cur = item.getRemainingBlocks(installed);

        this.entityData.set(DATA_HAS_BATTERY, true);
        this.entityData.set(DATA_BATTERY_MAX, max);
        this.entityData.set(DATA_BATTERY_REMAINING, cur);
        this.entityData.set(DATA_INSTALLED_BATTERY, installed);

        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.NEUTRAL, 0.8f, 1.2f);
        return true;
    }

    public void removeBattery(Player player) {
        if (!this.entityData.get(DATA_HAS_BATTERY)) return;

        ItemStack battery = this.entityData.get(DATA_INSTALLED_BATTERY);
        if (!battery.isEmpty() && battery.getItem() instanceof DroneBatteryItem item) {
            int remaining = this.entityData.get(DATA_BATTERY_REMAINING);
            battery.setDamageValue(Math.max(0, item.getMaxFlightBlocks() - remaining));
            ItemEntity drop = new ItemEntity(this.level(), getX(), getY() + 0.3, getZ(), battery);
            this.level().addFreshEntity(drop);
        }

        this.entityData.set(DATA_HAS_BATTERY, false);
        this.entityData.set(DATA_BATTERY_REMAINING, 0);
        this.entityData.set(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);

        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    public boolean installMotor(ItemStack motorItem, Player player) {
        if (!(motorItem.getItem() instanceof DroneMotorItem item)) return false;
        if (!this.entityData.get(DATA_INSTALLED_MOTOR).isEmpty()) return false;

        ItemStack installed = motorItem.split(1);
        this.entityData.set(DATA_MOTOR_TIER, item.getTier());
        this.entityData.set(DATA_INSTALLED_MOTOR, installed);

        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.5f, 1.5f);
        return true;
    }

    public void removeMotor(Player player) {
        ItemStack motor = this.entityData.get(DATA_INSTALLED_MOTOR);
        if (motor.isEmpty()) return;

        ItemEntity drop = new ItemEntity(this.level(), getX(), getY() + 0.3, getZ(), motor);
        this.level().addFreshEntity(drop);

        this.entityData.set(DATA_MOTOR_TIER, 1);
        this.entityData.set(DATA_INSTALLED_MOTOR, ItemStack.EMPTY);

        this.level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    public void consumeBattery(int amount) {
        int cur = this.entityData.get(DATA_BATTERY_REMAINING);
        int next = Math.max(0, cur - amount);
        this.entityData.set(DATA_BATTERY_REMAINING, next);
        if (next <= 0) {
            this.inputThrottle = 0;
            this.inputSteer = 0;
            this.inputStrafe = 0;
        }
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);

        // 1. スニーク右クリック: 機体回収
        if (player.isShiftKeyDown() && !isPiloted()) {
            dropVehicle(player);
            this.level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            return InteractionResult.SUCCESS;
        }

        // 2. バッテリー装着
        if (held.getItem() instanceof DroneBatteryItem batteryItem) {
            if (this.entityData.get(DATA_HAS_BATTERY)) {
                player.sendSystemMessage(Component.translatable("message.microdrone.battery_already_installed"));
                return InteractionResult.CONSUME;
            } else {
                if (installBattery(held, player)) {
                    ItemStack installed = this.entityData.get(DATA_INSTALLED_BATTERY);
                    player.sendSystemMessage(Component.translatable("message.microdrone.battery_installed",
                            batteryItem.getRemainingBlocks(installed), batteryItem.getMaxFlightBlocks()));
                    return InteractionResult.SUCCESS;
                }
            }
        }

        // 3. モーター装着
        if (held.getItem() instanceof DroneMotorItem motorItem) {
            if (installMotor(held, player)) {
                player.sendSystemMessage(Component.translatable("message.microdrone.motor_installed", motorItem.getTier()));
                return InteractionResult.SUCCESS;
            }
        }

        // 4. 手ぶら右クリック: バッテリー取り出し
        if (held.isEmpty() && this.entityData.get(DATA_HAS_BATTERY)) {
            removeBattery(player);
            player.sendSystemMessage(Component.translatable("message.microdrone.battery_removed"));
            return InteractionResult.SUCCESS;
        }

        // 5. 送信機を持っていれば操縦開始
        if (held.getItem() == ModItems.DRONE_CONTROLLER.get() && player instanceof ServerPlayer sp) {
            if (startPiloting(sp)) return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /**
     * 機体をアイテムとして回収
     */
    public void dropVehicle(Player player) {
        if (!this.level().isClientSide && !this.isRemoved()) {
            this.removeBattery(player);
            this.removeMotor(player);
            this.spawnAtLocation(new ItemStack(ModItems.RC_SPIDER_ITEM.get()));
            this.discard();
        }
    }

    // === RcControllable 実装 ===
    @Override
    public boolean isPiloted() {
        return this.pilotUUID != null;
    }

    @Override
    public boolean isPilotedBy(ServerPlayer player) {
        return this.pilotUUID != null && this.pilotUUID.equals(player.getUUID());
    }

    @Override
    public UUID getPilotUUID() {
        return this.pilotUUID;
    }

    @Override
    public boolean startPiloting(ServerPlayer player) {
        if (isPiloted()) return false;
        this.pilotUUID = player.getUUID();
        RcVehicleManager.registerPiloting(this.pilotUUID, this);
        PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), true, player.getX(), player.getY(), player.getZ()));
        player.sendSystemMessage(Component.translatable("message.microdrone.pilot_started_robot", this.getName()));
        return true;
    }

    @Override
    public void stopPiloting(ServerPlayer player) {
        if (isPilotedBy(player)) {
            UUID oldPilot = this.pilotUUID;
            this.pilotUUID = null;
            this.inputThrottle = 0;
            this.inputSteer = 0;
            this.inputStrafe = 0;
            RcVehicleManager.unregisterPiloting(oldPilot);
            PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(-1, false, 0, 0, 0));
            player.sendSystemMessage(Component.translatable("message.microdrone.pilot_stopped"));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        dropVehicle(null);
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        this.entityData.set(DATA_HAS_BATTERY, compound.getBoolean("HasBattery"));
        this.entityData.set(DATA_BATTERY_REMAINING, compound.getInt("BatteryRemaining"));
        this.entityData.set(DATA_BATTERY_MAX, compound.getInt("BatteryMax"));
        if (compound.contains("InstalledBattery")) {
            this.entityData.set(DATA_INSTALLED_BATTERY, ItemStack.parseOptional(this.level().registryAccess(), compound.getCompound("InstalledBattery")));
        }
        this.entityData.set(DATA_MOTOR_TIER, compound.getInt("MotorTier"));
        if (compound.contains("InstalledMotor")) {
            this.entityData.set(DATA_INSTALLED_MOTOR, ItemStack.parseOptional(this.level().registryAccess(), compound.getCompound("InstalledMotor")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        compound.putBoolean("HasBattery", this.entityData.get(DATA_HAS_BATTERY));
        compound.putInt("BatteryRemaining", this.entityData.get(DATA_BATTERY_REMAINING));
        compound.putInt("BatteryMax", this.entityData.get(DATA_BATTERY_MAX));
        ItemStack battery = this.entityData.get(DATA_INSTALLED_BATTERY);
        if (!battery.isEmpty()) {
            compound.put("InstalledBattery", battery.save(this.level().registryAccess()));
        }
        compound.putInt("MotorTier", this.entityData.get(DATA_MOTOR_TIER));
        ItemStack motor = this.entityData.get(DATA_INSTALLED_MOTOR);
        if (!motor.isEmpty()) {
            compound.put("InstalledMotor", motor.save(this.level().registryAccess()));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
