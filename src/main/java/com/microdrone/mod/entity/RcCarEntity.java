package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.DronePilotStatePayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class RcCarEntity extends Entity implements GeoEntity, RcControllable {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === 同期用 EntityData ===
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> DATA_MOTOR_TIER =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_MOTOR =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.ITEM_STACK);

    // 車輪回転角 & ステアリング操舵角（クライアント描画用）
    private static final EntityDataAccessor<Float> DATA_WHEEL_ROTATION =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER_ANGLE =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_ANGLE =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH_ANGLE =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_FL =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_FR =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_RL =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_RR =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);

    // 操縦者UUID
    private UUID pilotUUID = null;
    private double startX, startY, startZ;
    private UUID dummyEntityUUID = null;
    private RcPilotDummyEntity dummyEntityRef = null;
    private ChunkPos lastChunkPos = null;

    // 装着中のアイテム
    private ItemStack installedBattery = ItemStack.EMPTY;
    private ItemStack installedMotor = ItemStack.EMPTY;
    private double accumulatedDistance = 0.0;

    // 操作入力（サーバー/クライアント共用）
    public float inputForward = 0.0f;  // 前進(+1.0) / 後退(-1.0)
    public float inputSteer = 0.0f;    // 左ステアリング(-1.0) / 右ステアリング(+1.0)

    // 内部物理パラメータ
    private float currentSpeed = 0.0f;
    private float wheelRotation = 0.0f;
    private float steerAngle = 0.0f;
    private boolean locallyControlled = false;

    // クライアント側ソフト位置補正用フィールド (Phase 6 位置同期平滑化)
    private double serverTargetX = 0;
    private double serverTargetY = 0;
    private double serverTargetZ = 0;
    private float serverTargetYRot = 0;
    private boolean hasServerTarget = false;

    // ドリフト走行パラメータ
    // Mario Kart Drift System
    public boolean inputHandbrake = false; // Space key: Drift button
    private Vec3 carVelocity = Vec3.ZERO;  // Inertial movement vector
    private int driftDirection = 0;        // 0: normal, -1: drift left, +1: drift right
    private int driftTicks = 0;            // Drift duration ticks
    private int turboBoostTicks = 0;       // Mini-turbo boost remaining ticks
    private float rollAngle = 0.0f;        // Body roll tilt angle (degrees)
    public float prevRollAngle = 0.0f;     // Previous tick roll for smooth lerp
    private float pitchAngle = 0.0f;       // Body pitch tilt angle (degrees)
    public float prevPitchAngle = 0.0f;    // Previous tick pitch for smooth lerp

    // 4輪サスペンションのストローク伸縮量 (ブロック単位, 正=縮み, 負=伸び)
    private float suspFL = 0.0f;
    private float suspFR = 0.0f;
    private float suspRL = 0.0f;
    private float suspRR = 0.0f;
    private boolean wheelGrounded = false; // 仮想サスペンション（タイヤ）の接地判定
    private int collisionCooldown = 0;    // 壁衝突SEの連打防止クールダウン (ticks)

    public RcCarEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
        
    }

    public void setLocallyControlled(boolean locallyControlled) {
        this.locallyControlled = locallyControlled;
    }

    public boolean isLocallyControlled() {
        return this.locallyControlled;
    }

    @Override
    public float maxUpStep() {
        return 0.6f; // 小型RCカー向け段差走破性 (ハーフブロックや草ブロックの段差を乗り越え可能)
    }

    /**
     * サーバーからの位置同期パケット受信時の処理。
     * 自プレイヤー操縦中は、サーバーからの遅延同期で画面がカクつくのを防ぐため、
     * 大幅なズレ（壁埋まりなど）を除きクライアント側の予測移動を優先。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            // 4m 超過の致命的な位置ズレは即時スナップ補正
            if (distSq > 16.0) {
                MicroDroneMod.LOGGER.warn("[POSITION_CORRECTION] Reason: 4m_THRESHOLD_EXCEEDED, VehicleUUID: {}, ClientPos: [{}, {}, {}], ServerTargetPos: [{}, {}, {}], DeltaDist: {}m, CurrentSpeed: {}",
                        this.getUUID(), String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                        String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z),
                        String.format("%.2f", Math.sqrt(distSq)), this.currentSpeed);
                super.lerpTo(x, y, z, yRot, xRot, steps);
                this.hasServerTarget = false;
            } else {
                // 0.05m 以上の微小〜中程度のズレは、毎Tick滑らかに引き寄せるためのターゲットとして保持 (ソフトLerp)
                this.serverTargetX = x;
                this.serverTargetY = y;
                this.serverTargetZ = z;
                this.serverTargetYRot = yRot;
                this.hasServerTarget = true;

                if (distSq > 0.25) {
                    MicroDroneMod.LOGGER.debug("[POSITION_CORRECTION_DELTA] VehicleUUID: {}, DeltaDist: {}m, ClientPos: [{}, {}, {}], ServerTargetPos: [{}, {}, {}], Speed: {}",
                            this.getUUID(), String.format("%.2f", Math.sqrt(distSq)),
                            String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                            String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z), this.currentSpeed);
                }
            }
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HAS_BATTERY, false);
        builder.define(DATA_BATTERY_REMAINING, 0);
        builder.define(DATA_BATTERY_MAX, 0);
        builder.define(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);
        builder.define(DATA_MOTOR_TIER, 1);
        builder.define(DATA_INSTALLED_MOTOR, ItemStack.EMPTY);
        builder.define(DATA_WHEEL_ROTATION, 0.0f);
        builder.define(DATA_STEER_ANGLE, 0.0f);
        builder.define(DATA_ROLL_ANGLE, 0.0f);
        builder.define(DATA_PITCH_ANGLE, 0.0f);
        builder.define(DATA_SUSP_FL, 0.0f);
        builder.define(DATA_SUSP_FR, 0.0f);
        builder.define(DATA_SUSP_RL, 0.0f);
        builder.define(DATA_SUSP_RR, 0.0f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // === RcControllable 実装 ===
    @Override
    public boolean isPiloted() {
        return this.pilotUUID != null;
    }

    @Override
    public boolean isPilotedBy(ServerPlayer player) {
        return this.isPiloted() && this.pilotUUID.equals(player.getUUID());
    }

    public boolean isPilotedBy(Player player) {
        return player != null && this.pilotUUID != null && this.pilotUUID.equals(player.getUUID());
    }

    @Override
    public UUID getPilotUUID() {
        return this.pilotUUID;
    }

    @Override
    public boolean startPiloting(ServerPlayer player) {
        if (this.isPiloted()) {
            player.sendSystemMessage(Component.translatable("message.microdrone.already_piloted"));
            return false;
        }

        if (!this.hasBattery() || this.getBatteryRemaining() <= 0) {
            player.sendSystemMessage(Component.translatable("message.microdrone.no_battery"));
            return false;
        }

        this.pilotUUID = player.getUUID();
        this.startX = player.getX();
        this.startY = player.getY();
        this.startZ = player.getZ();

        // 本物のラジコン感覚: 操縦者プレイヤーの身代わりクローン（RcPilotDummyEntity）の配置
        ServerLevel serverLevel = player.serverLevel();
        RcPilotDummyEntity dummy = new RcPilotDummyEntity(ModEntities.RC_PILOT_DUMMY.get(), serverLevel);
        dummy.moveTo(this.startX, this.startY, this.startZ, player.getYRot(), player.getXRot());
        dummy.setupFromPlayer(player);
        serverLevel.addFreshEntity(dummy);
        this.dummyEntityRef = dummy;
        this.dummyEntityUUID = dummy.getUUID();
        RcVehicleManager.registerDummy(dummy.getUUID(), this);

        RcVehicleManager.registerPiloting(player.getUUID(), this);

        PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), true, this.startX, this.startY, this.startZ));
        player.sendSystemMessage(Component.translatable("message.microdrone.pilot_start"));

        this.lastChunkPos = new ChunkPos(this.blockPosition());
        com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(player, this);

        return true;
    }

    @Override
    public void stopPiloting(ServerPlayer player) {
        if (this.pilotUUID != null && this.pilotUUID.equals(player.getUUID())) {
            ServerLevel serverLevel = player.serverLevel();

            UUID targetDummyUUID = this.dummyEntityUUID;
            RcPilotDummyEntity targetDummyRef = this.dummyEntityRef;
            this.dummyEntityRef = null;
            this.dummyEntityUUID = null;

            if (targetDummyUUID != null) {
                RcVehicleManager.unregisterDummy(targetDummyUUID);
            }

            if (targetDummyRef != null) {
                targetDummyRef.discard();
            }
            if (targetDummyUUID != null) {
                Entity dummy = serverLevel.getEntity(targetDummyUUID);
                if (dummy != null) dummy.discard();
            }

            player.setCamera(player);
            player.setInvisible(false);
            player.teleportTo(this.startX, this.startY, this.startZ);

            if (targetDummyUUID != null) {
                Entity dummyRetry = serverLevel.getEntity(targetDummyUUID);
                if (dummyRetry != null) dummyRetry.discard();
            }

            PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), false, this.startX, this.startY, this.startZ));
            player.sendSystemMessage(Component.translatable("message.microdrone.pilot_stop"));

            com.microdrone.mod.chunk.DroneChunkManager.releaseDroneChunks(player, this);

            RcVehicleManager.unregisterPiloting(player.getUUID());
            this.pilotUUID = null;
            this.inputForward = 0.0f;
            this.inputSteer = 0.0f;
        }
    }

    // === 自動車物理計算 (Tick) ===
    @Override
    public void tick() {
        super.tick();

        if (this.collisionCooldown > 0) {
            this.collisionCooldown--;
        }

        if (!this.level().isClientSide) {
            ServerPlayer pilot = this.pilotUUID != null ? (ServerPlayer) this.level().getPlayerByUUID(this.pilotUUID) : null;

            if (pilot != null) {
                // 電波距離監視 (250m)
                double distToStart = Math.sqrt(this.distanceToSqr(this.startX, this.startY, this.startZ));
                if (distToStart > 250.0) {
                    this.inputForward = 0.0f;
                    this.inputSteer = 0.0f;
                    this.stopPiloting(pilot);
                    return;
                }

                // チャンク配信
                ChunkPos currentChunk = new ChunkPos(this.blockPosition());
                if (!currentChunk.equals(this.lastChunkPos)) {
                    this.lastChunkPos = currentChunk;
                    com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(pilot, this);
                }

                // Battery empty failsafe (checked every server tick)
                if (!this.hasBattery() || this.getBatteryRemaining() <= 0) {
                    MicroDroneMod.LOGGER.warn("[SERVER] [BATTERY_EMPTY_FAILSAFE] Car {} ran out of battery. Stopping piloting.", this.getUUID());
                    pilot.sendSystemMessage(Component.translatable("message.microdrone.battery_empty_failsafe"));
                    this.inputForward = 0.0f;
                    this.inputSteer = 0.0f;
                    this.stopPiloting(pilot);
                    return;
                }

// プレイヤーは立ち位置に静止
            }

            // サーバー側物理処理
            long startNanos = System.nanoTime();
            tickCarPhysics();
            long elapsedUs = (System.nanoTime() - startNanos) / 1000;
            if (elapsedUs > 5000) {
                MicroDroneMod.LOGGER.warn("[VEHICLE_TICK_TIME] SERVER car physics took {} us (>5ms) for {}", elapsedUs, this.getUUID());
            }

            if (this.tickCount % 20 == 0 && this.isPiloted()) {
                MicroDroneMod.LOGGER.debug("[SERVER_POSITION] VehicleUUID: {}, Tick: {}, Pos: [{}, {}, {}], Speed: {}, Velocity: [{}, {}]",
                        this.getUUID(), this.tickCount,
                        String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                        String.format("%.2f", this.currentSpeed),
                        String.format("%.2f", this.carVelocity.x), String.format("%.2f", this.carVelocity.z));
            }

            // クライアント同期用角度・サスペンション
            this.entityData.set(DATA_WHEEL_ROTATION, this.wheelRotation);
            this.entityData.set(DATA_STEER_ANGLE, this.steerAngle);
            this.entityData.set(DATA_ROLL_ANGLE, this.rollAngle);
            this.entityData.set(DATA_PITCH_ANGLE, this.pitchAngle);
            this.entityData.set(DATA_SUSP_FL, this.suspFL);
            this.entityData.set(DATA_SUSP_FR, this.suspFR);
            this.entityData.set(DATA_SUSP_RL, this.suspRL);
            this.entityData.set(DATA_SUSP_RR, this.suspRR);
        } else {
            // クライアント側処理
            if (this.locallyControlled) {
                // ローカル操縦中はクライアント側でも即座に物理演算を行い、遅延ゼロ＆カクツキゼロを実現
                long startNanos = System.nanoTime();
                tickCarPhysics();

                // サーバー確定位置へのソフト補間（Smooth Damp / Exponential Smoothing）
                // 操作の即応性（遅延ゼロ）を100%保ちつつ、ズレを自然にゼロへ収束させる
                if (this.hasServerTarget) {
                    double curX = this.getX();
                    double curY = this.getY();
                    double curZ = this.getZ();
                    double dx = this.serverTargetX - curX;
                    double dy = this.serverTargetY - curY;
                    double dz = this.serverTargetZ - curZ;
                    double distSq = dx * dx + dy * dy + dz * dz;

                    if (distSq > 0.0025) { // 0.05m以上のズレがある場合
                        // 水平方向は毎tick 10%ずつサーバー位置に滑らかに引き寄せる
                        double smoothFactor = 0.10;
                        double newX = curX + dx * smoothFactor;
                        double newZ = curZ + dz * smoothFactor;

                        // Y軸は段差・接地との競合を防ぐため、Yズレが大きい場合（0.15m以上）のみ緩やかに補正
                        double newY = curY;
                        if (Math.abs(dy) > 0.15) {
                            newY = curY + dy * 0.08;
                        }

                        this.setPos(newX, newY, newZ);
                    } else {
                        this.hasServerTarget = false;
                    }
                }

                long elapsedUs = (System.nanoTime() - startNanos) / 1000;
                if (elapsedUs > 5000) {
                    MicroDroneMod.LOGGER.warn("[VEHICLE_TICK_TIME] CLIENT car physics took {} us (>5ms) for {}", elapsedUs, this.getUUID());
                }

                if (this.tickCount % 20 == 0) {
                    MicroDroneMod.LOGGER.debug("[CLIENT_POSITION] VehicleUUID: {}, Tick: {}, Pos: [{}, {}, {}], Speed: {}, Velocity: [{}, {}]",
                            this.getUUID(), this.tickCount,
                            String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                            String.format("%.2f", this.currentSpeed),
                            String.format("%.2f", this.carVelocity.x), String.format("%.2f", this.carVelocity.z));
                }
            } else {
                // 他プレイヤーが操縦中または放置中はサーバー同期値を使用
                this.wheelRotation = this.entityData.get(DATA_WHEEL_ROTATION);
                this.steerAngle = this.entityData.get(DATA_STEER_ANGLE);
                this.rollAngle = this.entityData.get(DATA_ROLL_ANGLE);
                this.pitchAngle = this.entityData.get(DATA_PITCH_ANGLE);
                this.suspFL = this.entityData.get(DATA_SUSP_FL);
                this.suspFR = this.entityData.get(DATA_SUSP_FR);
                this.suspRL = this.entityData.get(DATA_SUSP_RL);
                this.suspRR = this.entityData.get(DATA_SUSP_RR);
            }
        }
    }

    private void tickCarPhysics() {
        DroneMotorItem motor = getActiveMotorItem();
        // 車用に速度を 1/4 に調整 (blocks/tick)
        // Tier 1 (0.35) -> 0.19 blocks/tick (約 3.8 m/s = 14 km/h)
        // Tier 4 (0.95) -> 0.52 blocks/tick (約 10.4 m/s = 38 km/h)
        double maxSpeed = motor.getMaxHorizontalSpeed() * 0.55;
        double accel = 0.025 + (motor.getTier() * 0.008); // 速度に合わせた滑らかで扱いやすい加速
        double friction = 0.90; // 地面摩擦減速

        // ステアリング操舵 (最大35度)
        float targetSteer = this.inputSteer * 35.0f;
        this.steerAngle = Mth.lerp(0.40f, this.steerAngle, targetSteer);

        // ③ 接触時の出力抑制 (壁・障害物に接触/密着している状態での前進アクセルを大幅カットし、ゴリ押し抜けを完全防止)
        boolean wallAhead = this.horizontalCollision || (this.inputForward > 0.05f && isObstacleAhead(0.12));
        if (wallAhead && this.inputForward > 0.05f) {
            this.currentSpeed = Math.min(this.currentSpeed, 0.015f); // ほぼ停止速度に制限
            accel *= 0.08; // 加速を90%以上カット
        }

        // 前進 / 後退 / ESCブレーキ処理 (実機ホビーラジコンESC仕様: 前進中のS入力は急ブレーキ、停止後にバック)
        // Forward / Reverse / Brake processing with Mario Kart drift speed maintenance
        if (this.inputForward > 0.05f) {
            if (this.currentSpeed < -0.01f) {
                this.currentSpeed += (float) (accel * 2.5f);
                if (this.currentSpeed > 0.0f) this.currentSpeed = 0.0f;
            } else {
                float forwardAccel = (float) (accel * this.inputForward);
                if (this.driftDirection != 0) {
                    forwardAccel *= 1.30f; // Maintain high speed during drift (long slide)
                }
                this.currentSpeed += forwardAccel;
            }
        } else if (this.inputForward < -0.05f) {
            if (this.currentSpeed > 0.01f) {
                this.currentSpeed -= (float) (accel * 2.5f);
                if (this.currentSpeed < 0.0f) this.currentSpeed = 0.0f;
            } else {
                this.currentSpeed += (float) (accel * this.inputForward * 0.65f);
            }
        } else {
            this.currentSpeed *= friction;
            if (Math.abs(this.currentSpeed) < 0.001f) {
                this.currentSpeed = 0.0f;
            }
        }

        double currentMax = (this.turboBoostTicks > 0) ? (maxSpeed * 1.30) : maxSpeed;
        this.currentSpeed = (float) Mth.clamp(this.currentSpeed, -maxSpeed * 0.45, currentMax);

        // === Mario Kart Drift State Machine ===
        // inputSteer > 0: A key (Left), inputSteer < 0: D key (Right)
        boolean wantsDrift = this.inputHandbrake && (this.currentSpeed > 0.03f || this.driftDirection != 0);

        if (wantsDrift) {
            // Start drift lock
            if (this.driftDirection == 0) {
                if (this.inputSteer > 0.05f) {
                    this.driftDirection = -1; // A key: Drift Left
                    this.driftTicks = 0;
                    if (this.onGround() || this.wheelGrounded) {
                        Vec3 cur = this.getDeltaMovement();
                        this.setDeltaMovement(new Vec3(cur.x, 0.24, cur.z)); // Hop jump!
                        this.setOnGround(false);
                        this.hasImpulse = true;
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 12.0F, 1.8F);
                    }
                } else if (this.inputSteer < -0.05f) {
                    this.driftDirection = 1;  // D key: Drift Right
                    this.driftTicks = 0;
                    if (this.onGround() || this.wheelGrounded) {
                        Vec3 cur = this.getDeltaMovement();
                        this.setDeltaMovement(new Vec3(cur.x, 0.24, cur.z)); // Hop jump!
                        this.setOnGround(false);
                        this.hasImpulse = true;
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 12.0F, 1.8F);
                    }
                }
            }

            if (this.driftDirection != 0) {
                this.driftTicks++;
            }
        } else {
            // Released space key or stopped -> Mini-Turbo release!
            if (this.driftDirection != 0) {
                if (this.driftTicks >= 14 && this.currentSpeed > 0.04f) {
                    // Mini-Turbo boost!
                    this.turboBoostTicks = 10; // Boost dash
                    this.currentSpeed = (float) Math.min(this.currentSpeed * 1.35f, maxSpeed * 1.30f);
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 12.0F, 1.8F);
                    if (this.level().isClientSide) {
                        spawnMiniTurboBurst();
                    }
                }
                this.driftDirection = 0;
                this.driftTicks = 0;
            }
        }

        if (this.turboBoostTicks > 0) {
            this.turboBoostTicks--;
        }

        // === Counter-Steer and In-Steer Calculations ===
        // inputSteer > 0 is Left (A key), inputSteer < 0 is Right (D key)
        float counterAmount = 0.0f; // 0.0 (none) to 1.0 (full counter-steer)
        float inSteerAmount = 0.0f; // 0.0 (none) to 1.0 (full in-steer)

        if (this.driftDirection == -1) {
            // Left Drift: D key (<0) is Counter-Steer to Right! A key (>0) is In-Steer to Left
            if (this.inputSteer < -0.05f) {
                counterAmount = -this.inputSteer; // D key = Counter-steer
            } else if (this.inputSteer > 0.05f) {
                inSteerAmount = this.inputSteer;  // A key = In-steer
            }
        } else if (this.driftDirection == 1) {
            // Right Drift: A key (>0) is Counter-Steer to Left! D key (<0) is In-Steer to Right
            if (this.inputSteer > 0.05f) {
                counterAmount = this.inputSteer;  // A key = Counter-steer
            } else if (this.inputSteer < -0.05f) {
                inSteerAmount = -this.inputSteer; // D key = In-steer
            }
        }

        // 4点サスペンション物理とピッチ・ロール姿勢角の計算 (サスペンションシステム)
        this.prevRollAngle = this.rollAngle;
        this.prevPitchAngle = this.pitchAngle;
        updateSuspensionPhysics(counterAmount);

        // === Steering / Yaw Turn ===
        if (this.driftDirection != 0 && Math.abs(this.currentSpeed) > 0.002f) {
            float baseTurn = 4.2f;
            float turnSpeed;
            if (inSteerAmount > 0.0f) {
                turnSpeed = baseTurn * (1.0f + inSteerAmount * 0.85f); // In-steer tighter
            } else {
                // Counter-steer controls and holds the drift angle
                turnSpeed = baseTurn * (1.0f - counterAmount * 0.90f);
            }
            this.setYRot(this.getYRot() + turnSpeed * this.driftDirection);
        } else {
            if (Math.abs(this.currentSpeed) > 0.002f) {
                float baseTurn = (this.currentSpeed > 0 ? 13.5f : -13.5f);
                float turnSpeed = (this.steerAngle / 35.0f) * baseTurn;
                this.setYRot(this.getYRot() - turnSpeed);
            }
        }

        // === Inertial drift movement vector synthesis (Power slide along counter-steer) ===
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double driveX = -Mth.sin(yawRad);
        double driveZ = Mth.cos(yawRad);

        // Right vector: rightX = -driveZ = -cos, rightZ = driveX = -sin
        double rightX = -driveZ;
        double rightZ = driveX;

        if (this.driftDirection != 0) {
            // Slide direction vector in the counter-steer direction:
            // Left Drift (driftDirection == -1): Counter is Right -> slide direction is Right Vector (rightX, rightZ)
            // Right Drift (driftDirection == 1): Counter is Left -> slide direction is Left Vector (-rightX, -rightZ)
            double counterSlideX = -rightX * this.driftDirection;
            double counterSlideZ = -rightZ * this.driftDirection;

            // When counter-steering, the car strongly slides in the counter-steered direction!
            // Neutral: drive 0.60, slide 0.55
            // Full Counter: drive 0.25, slide 1.05 -> Power slide laterally in counter-steer direction!
            // Full In-steer: drive 0.90, slide 0.20 -> Drives forward into turn
            double driveWeight = 0.60 - (counterAmount * 0.35) + (inSteerAmount * 0.30);
            double slideWeight = 0.55 + (counterAmount * 0.50) - (inSteerAmount * 0.35);

            double combinedX = (driveX * driveWeight) + (counterSlideX * slideWeight);
            double combinedZ = (driveZ * driveWeight) + (counterSlideZ * slideWeight);

            double mag = Math.sqrt(combinedX * combinedX + combinedZ * combinedZ);
            if (mag > 0.001) {
                combinedX = (combinedX / mag) * this.currentSpeed;
                combinedZ = (combinedZ / mag) * this.currentSpeed;
            }

            this.carVelocity = new Vec3(
                    Mth.lerp(0.22, this.carVelocity.x, combinedX),
                    0,
                    Mth.lerp(0.22, this.carVelocity.z, combinedZ)
            );
        } else {
            if (Math.abs(this.currentSpeed) < 0.001f) {
                this.carVelocity = Vec3.ZERO;
            } else {
                this.carVelocity = new Vec3(
                        Mth.lerp(0.55, this.carVelocity.x, driveX * this.currentSpeed),
                        0,
                        Mth.lerp(0.55, this.carVelocity.z, driveZ * this.currentSpeed)
                );
            }
        }

        double motionX = this.carVelocity.x;
        double motionZ = this.carVelocity.z;
        double motionY;
        if (this.onGround() && this.getDeltaMovement().y <= 0.001) {
            motionY = 0.0;
        } else {
            motionY = this.getDeltaMovement().y - 0.04; // Light gravity
        }

        Vec3 preMovePos = this.position();
        double totalDistH = Math.sqrt(motionX * motionX + motionZ * motionZ);

        // ② サブステップ分割 (CCD: 連続衝突判定)
        // 1回の移動量がガードレールの厚み(0.2m)を飛び越えて壁抜けするトンネリング現象を完全防止
        int numSteps = 1;
        if (totalDistH > 0.04) {
            numSteps = (int) Math.ceil(totalDistH / 0.04); // 1ステップ最大0.04m (約0.6px)
            numSteps = Mth.clamp(numSteps, 1, 6);
        }

        double stepX = motionX / numSteps;
        double stepY = motionY / numSteps;
        double stepZ = motionZ / numSteps;

        for (int i = 0; i < numSteps; i++) {
            this.setDeltaMovement(new Vec3(stepX, stepY, stepZ));
            this.move(MoverType.SELF, this.getDeltaMovement());
            if (this.horizontalCollision) {
                break; // 壁に接触した瞬間に残りのステップを即時遮断！
            }
        }
        Vec3 actualMoved = this.position().subtract(preMovePos);

        // 地面に衝突した（着地した）場合は接地状態を維持
        if (this.verticalCollision && motionY <= 0.0) {
            this.setOnGround(true);
        }

        // 衝突検知 (壁・障害物に激突して移動量が大幅に遮断された場合、マシンの速度を急減衰して確実にストップ)
        double expectedH = totalDistH;
        double actualH = Math.sqrt(actualMoved.x * actualMoved.x + actualMoved.z * actualMoved.z);
        if (this.horizontalCollision || (expectedH > 0.03 && actualH < expectedH * 0.40)) {
            this.currentSpeed *= 0.15f; // ガツンと止まる
            this.carVelocity = new Vec3(actualMoved.x, 0.0, actualMoved.z);

            // 衝突SE & クラッシュ火花パーティクル再生
            if (expectedH > 0.02) {
                if (this.level().isClientSide) {
                    spawnCollisionSparks(expectedH);
                }

                if (this.collisionCooldown <= 0) {
                    this.collisionCooldown = 8; // 0.4秒間隔
                    float pitch = (float) (1.0f + Math.random() * 0.3f);
                    float volume = (float) Math.min(1.0, Math.max(0.5, expectedH * 4.0));

                    if (this.level().isClientSide) {
                        this.level().playLocalSound(this.getX(), this.getY(), this.getZ(),
                                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                                net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch, false);
                    } else {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                                net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch);
                    }
                }
            }

            MicroDroneMod.LOGGER.debug("[COLLISION_DETECTED] Side: {}, VehicleUUID: {}, Pos: [{}, {}, {}], ExpectedSpeed: {}, ActualSpeed: {}, CurrentSpeed: {}",
                    this.level().isClientSide ? "CLIENT" : "SERVER", this.getUUID(),
                    String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                    String.format("%.3f", expectedH), String.format("%.3f", actualH),
                    String.format("%.3f", this.currentSpeed));
        }

        // 接地摩擦
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }

        // タイヤ回転角の積算
        this.wheelRotation += (this.currentSpeed * 144.0f);

        // ドリフト白煙エフェクト (クライアント側)
        // Drift smoke effect (when tires lose grip)
        // Drift particles & Mini-Turbo effects
        if (this.level().isClientSide && (this.onGround() || this.wheelGrounded) && Math.abs(this.currentSpeed) > 0.025f) {
            if (this.driftDirection != 0) {
                spawnDriftParticles(this.driftTicks >= 14);
            }
            if (this.turboBoostTicks > 0) {
                spawnTurboBoostTrail();
            }
        }

        // バッテリー消費 (サーバー側でのみ実行)
        if (!this.level().isClientSide && !this.installedBattery.isEmpty()) {
            double moveDist = Math.sqrt(motionX * motionX + motionZ * motionZ);
            this.accumulatedDistance += moveDist;
            if (this.accumulatedDistance >= 1.0) {
                int drain = (int) this.accumulatedDistance;
                this.accumulatedDistance -= drain;
                if (this.installedBattery.getItem() instanceof DroneBatteryItem batteryItem) {
                    batteryItem.consumeFlightDistance(this.installedBattery, drain);
                    updateBatterySyncData();
                }
            }
        }
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    // === インベントリ・右クリック対話 ===
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        if (!this.level().isClientSide) {
            ItemStack heldItem = player.getItemInHand(hand);

            // 1. バッテリー装着
            if (heldItem.getItem() instanceof DroneBatteryItem batteryItem) {
                if (!this.installedBattery.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("message.microdrone.battery_already_installed"));
                    return InteractionResult.FAIL;
                }
                this.installedBattery = heldItem.split(1);
                updateBatterySyncData();
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
                player.sendSystemMessage(Component.translatable("message.microdrone.battery_installed",
                        batteryItem.getRemainingBlocks(this.installedBattery), batteryItem.getMaxFlightBlocks()));
                return InteractionResult.SUCCESS;
            }

            // 2. モーター換装 (装着・交換)
            if (heldItem.getItem() instanceof DroneMotorItem motorItem) {
                ItemStack oldMotor = this.installedMotor.copy();
                this.installedMotor = heldItem.split(1);
                updateMotorSyncData();
                if (!oldMotor.isEmpty()) {
                    if (!player.getInventory().add(oldMotor)) {
                        this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), oldMotor));
                    }
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
                player.sendSystemMessage(Component.translatable("message.microdrone.motor_installed",
                        motorItem.getTier(), motorItem.getTierName(), String.format("%.1f", motorItem.getDisplaySpeedMps())));
                return InteractionResult.SUCCESS;
            }

            // 3. プロポ所持時: 操縦開始
            if (heldItem.getItem() instanceof com.microdrone.mod.item.DroneControllerItem) {
                if (player instanceof ServerPlayer serverPlayer) {
                    if (this.isPilotedBy(player)) {
                        this.stopPiloting(serverPlayer);
                    } else {
                        this.startPiloting(serverPlayer);
                    }
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.FAIL;
            }

            // 4. スニーク右クリック: バッテリー回収（未装着なら本体回収）
            if (player.isShiftKeyDown()) {
                if (!this.installedBattery.isEmpty()) {
                    if (!player.getInventory().add(this.installedBattery)) {
                        this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedBattery));
                    }
                    this.installedBattery = ItemStack.EMPTY;
                    updateBatterySyncData();
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
                    player.sendSystemMessage(Component.translatable("message.microdrone.battery_removed"));
                    return InteractionResult.SUCCESS;
                } else {
                    this.dropVehicle(player);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private void dropVehicle(Player player) {
        if (!this.level().isClientSide) {
            ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(),
                    new ItemStack(ModItems.RC_CAR_ITEM.get()));
            this.level().addFreshEntity(item);

            if (!this.installedBattery.isEmpty()) {
                this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedBattery));
                this.installedBattery = ItemStack.EMPTY;
            }
            if (!this.installedMotor.isEmpty()) {
                this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedMotor));
                this.installedMotor = ItemStack.EMPTY;
            }
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (this.pilotUUID != null && this.level() instanceof ServerLevel sl) {
                ServerPlayer p = (ServerPlayer) sl.getPlayerByUUID(this.pilotUUID);
                if (p != null) this.stopPiloting(p);
            }
            this.dropVehicle(null);
            return true;
        }
        return false;
    }

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

    private void updateMotorSyncData() {
        if (!this.installedMotor.isEmpty() && this.installedMotor.getItem() instanceof DroneMotorItem mot) {
            this.entityData.set(DATA_MOTOR_TIER, mot.getTier());
            this.entityData.set(DATA_INSTALLED_MOTOR, this.installedMotor.copy());
        } else {
            this.entityData.set(DATA_MOTOR_TIER, 1);
            this.entityData.set(DATA_INSTALLED_MOTOR, ItemStack.EMPTY);
        }
    }

    public boolean hasBattery() {
        return this.entityData.get(DATA_HAS_BATTERY);
    }

    public int getBatteryRemaining() {
        return this.entityData.get(DATA_BATTERY_REMAINING);
    }

    public int getBatteryMax() {
        return this.entityData.get(DATA_BATTERY_MAX);
    }

    public DroneMotorItem getActiveMotorItem() {
        ItemStack motorStack = this.entityData.get(DATA_INSTALLED_MOTOR);
        if (!motorStack.isEmpty() && motorStack.getItem() instanceof DroneMotorItem mot) {
            return mot;
        }
        return (DroneMotorItem) ModItems.MOTOR_TIER_1.get();
    }

    public float getWheelRotation() {
        return this.wheelRotation;
    }

    public float getSteerAngle() {
        return this.steerAngle;
    }

    // === 遠距離描画拡大 ===
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = Math.max(256.0, this.getBoundingBox().getSize() * 64.0) * getViewScale();
        return distance < range * range;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("PilotUUID")) {
            this.pilotUUID = compound.getUUID("PilotUUID");
        }
        if (compound.contains("InstalledBattery", 10)) {
            this.installedBattery = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledBattery")).orElse(ItemStack.EMPTY);
            updateBatterySyncData();
        }
        if (compound.contains("InstalledMotor", 10)) {
            this.installedMotor = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledMotor")).orElse(ItemStack.EMPTY);
            updateMotorSyncData();
        }
        this.accumulatedDistance = compound.getDouble("BatteryAccumulatedDist");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.pilotUUID != null) {
            compound.putUUID("PilotUUID", this.pilotUUID);
        }
        if (!this.installedBattery.isEmpty()) {
            compound.put("InstalledBattery", this.installedBattery.save(this.registryAccess()));
        }
        if (!this.installedMotor.isEmpty()) {
            compound.put("InstalledMotor", this.installedMotor.save(this.registryAccess()));
        }
        compound.putDouble("BatteryAccumulatedDist", this.accumulatedDistance);
    }

    private void spawnDriftParticles(boolean turboReady) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double backX = Mth.sin(yawRad) * 0.15;
        double backZ = -Mth.cos(yawRad) * 0.15;
        double rightX = Mth.cos(yawRad) * 0.14;
        double rightZ = Mth.sin(yawRad) * 0.14;
        double y = this.getY() + 0.03;

        // White tire smoke
        this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                this.getX() + backX - rightX, y, this.getZ() + backZ - rightZ,
                (Math.random() - 0.5) * 0.01, 0.015, (Math.random() - 0.5) * 0.01);
        this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                this.getX() + backX + rightX, y, this.getZ() + backZ + rightZ,
                (Math.random() - 0.5) * 0.01, 0.015, (Math.random() - 0.5) * 0.01);

        // Mario Kart blue spark sparks when Mini-Turbo is charged!
        if (turboReady) {
            this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX() + backX - rightX, y + 0.02, this.getZ() + backZ - rightZ,
                    (Math.random() - 0.5) * 0.03, 0.02, (Math.random() - 0.5) * 0.03);
            this.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX() + backX + rightX, y + 0.02, this.getZ() + backZ + rightZ,
                    (Math.random() - 0.5) * 0.03, 0.02, (Math.random() - 0.5) * 0.03);
        }
    }

    private void spawnMiniTurboBurst() {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double backX = Mth.sin(yawRad) * 0.20;
        double backZ = -Mth.cos(yawRad) * 0.20;
        double y = this.getY() + 0.05;
        for (int i = 0; i < 8; i++) {
            double rx = (Math.random() - 0.5) * 0.1;
            double rz = (Math.random() - 0.5) * 0.1;
            this.level().addParticle(ParticleTypes.FLAME,
                    this.getX() + backX + rx, y, this.getZ() + backZ + rz,
                    backX * 0.5, 0.03, backZ * 0.5);
        }
    }

    private void spawnTurboBoostTrail() {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double backX = Mth.sin(yawRad) * 0.16;
        double backZ = -Mth.cos(yawRad) * 0.16;
        double y = this.getY() + 0.04;
        this.level().addParticle(ParticleTypes.FLAME,
                this.getX() + backX, y, this.getZ() + backZ,
                0, 0.01, 0);
    }

    /**
     * ① 前方スキャン: マシンの前方に衝突する壁（ガードレール等）が存在するか事前検知
     * レーシング縁石（高さ0.12m以下）は乗り越え可能なので壁と判定せず、走行を阻害しない。
     */
    private boolean isObstacleAhead(double checkDistance) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double fwdX = -Mth.sin(yawRad);
        double fwdZ = Mth.cos(yawRad);

        net.minecraft.world.phys.AABB currentBox = this.getBoundingBox();
        net.minecraft.world.phys.AABB checkAABB = currentBox.expandTowards(fwdX * checkDistance, 0.0, fwdZ * checkDistance);

        Iterable<net.minecraft.world.phys.shapes.VoxelShape> collisions = this.level().getBlockCollisions(this, checkAABB);
        for (net.minecraft.world.phys.shapes.VoxelShape shape : collisions) {
            if (!shape.isEmpty()) {
                for (net.minecraft.world.phys.AABB aabb : shape.toAabbs()) {
                    // 高さ判定: レーシング縁石(高さ約0.038m)は除外、高さ0.12m以上の壁をブロック対象とする
                    if (aabb.maxY - this.getY() > 0.12) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * ④ クラッシュ火花パーティクル再生 (王道アーケードレース風: 高速閃光スパーク FIREWORK ＋ 黄金色星型 CRIT)
     */
    private void spawnCollisionSparks(double impactSpeed) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double frontX = -Mth.sin(yawRad) * 0.18;
        double frontZ = Mth.cos(yawRad) * 0.18;
        double y = this.getY() + 0.08;
        int count = Math.min(14, (int) (impactSpeed * 70.0) + 5);

        for (int i = 0; i < count; i++) {
            // 前方衝突面から四方へ勢いよく跳ね返る速度ベクトル
            double vx = (Math.random() - 0.5) * 0.35 - frontX * 0.5;
            double vy = Math.random() * 0.22 + 0.06;
            double vz = (Math.random() - 0.5) * 0.35 - frontZ * 0.5;

            // 高速で尾を引く閃光スパーク (花火パーティクル: シュパパッと鮮やかに散る)
            this.level().addParticle(ParticleTypes.FIREWORK,
                    this.getX() + frontX + (Math.random() - 0.5) * 0.10,
                    y,
                    this.getZ() + frontZ + (Math.random() - 0.5) * 0.10,
                    vx, vy, vz);

            // 黄金色の星型キラキラスパーク (アーケードゲーム特有の派手なヒット演出)
            if (i % 2 == 0) {
                this.level().addParticle(ParticleTypes.CRIT,
                        this.getX() + frontX + (Math.random() - 0.5) * 0.12,
                        y + 0.02,
                        this.getZ() + frontZ + (Math.random() - 0.5) * 0.12,
                        vx * 0.8, vy * 0.8, vz * 0.8);
            }
        }
    }

    public float getRollAngle() {
        return this.rollAngle;
    }

    public float getPitchAngle() {
        return this.pitchAngle;
    }

    public float getSuspFL() { return this.suspFL; }
    public float getSuspFR() { return this.suspFR; }
    public float getSuspRL() { return this.suspRL; }
    public float getSuspRR() { return this.suspRR; }
    public boolean isWheelGrounded() { return this.wheelGrounded; }

    /**
     * 4点仮想サスペンション物理シミュレーション
     * 4輪の地面高さからサス縮み量を算出し、路面起伏・片輪乗り上げ・旋回ロール・加減速ピッチを精密計算
     */
    private void updateSuspensionPhysics(float counterAmount) {
        float yaw = this.getYRot();
        double rad = Math.toRadians(yaw);
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = -Math.cos(rad);
        double rightZ = -Math.sin(rad);

        double halfL = 0.20; // ホイールベース前後スパン
        double halfW = 0.16; // トレッド幅左右スパン

        double px = getX();
        double py = getY();
        double pz = getZ();

        // 4輪位置の地面高さをサンプリング
        double hFL = sampleGroundY(px + fwdX * halfL - rightX * halfW, py, pz + fwdZ * halfL - rightZ * halfW);
        double hFR = sampleGroundY(px + fwdX * halfL + rightX * halfW, py, pz + fwdZ * halfL + rightZ * halfW);
        double hRL = sampleGroundY(px - fwdX * halfL - rightX * halfW, py, pz - fwdZ * halfL - rightZ * halfW);
        double hRR = sampleGroundY(px - fwdX * halfL + rightX * halfW, py, pz - fwdZ * halfL + rightZ * halfW);

        // 各タイヤのサスペンション伸縮ストローク量 (clamp [-0.08, 0.08])
        float targetSuspFL = (float) Mth.clamp(hFL - py, -0.08, 0.08);
        float targetSuspFR = (float) Mth.clamp(hFR - py, -0.08, 0.08);
        float targetSuspRL = (float) Mth.clamp(hRL - py, -0.08, 0.08);
        float targetSuspRR = (float) Mth.clamp(hRR - py, -0.08, 0.08);

        // スプリング・ダンパーによる追従
        this.suspFL = Mth.lerp(0.40f, this.suspFL, targetSuspFL);
        this.suspFR = Mth.lerp(0.40f, this.suspFR, targetSuspFR);
        this.suspRL = Mth.lerp(0.40f, this.suspRL, targetSuspRL);
        this.suspRR = Mth.lerp(0.40f, this.suspRR, targetSuspRR);

        // 4輪中いずれかが路面に接触・追従しているか (車体下約0.08ブロック以内に地面があればタイヤ接地・トラクション有効)
        this.wheelGrounded = (hFL >= py - 0.08 || hFR >= py - 0.08 || hRL >= py - 0.08 || hRR >= py - 0.08);

        // === 前後ピッチ角 (地面傾斜 + 加減速G) ===
        double hFront = (hFL + hFR) * 0.5;
        double hRear = (hRL + hRR) * 0.5;
        double deltaPitch = hRear - hFront;
        float groundPitch = (float) Math.toDegrees(Math.atan2(deltaPitch, halfL * 2.0));

        // 急ブレーキ/後退でノーズダイブ(+), 前進加速でスクワット(-)
        float accelPitch = 0.0f;
        if (this.inputForward > 0.1f) {
            accelPitch = -2.5f * this.inputForward;
        } else if (this.inputForward < -0.1f || this.inputHandbrake) {
            accelPitch = 4.0f;
        }
        float targetPitch = Mth.clamp(groundPitch + accelPitch, -25.0f, 25.0f);
        this.pitchAngle = Mth.lerp(0.35f, this.pitchAngle, targetPitch);

        // === 左右ロール角 (地面傾斜 + 旋回・ドリフト遠心力) ===
        double hLeft = (hFL + hRL) * 0.5;
        double hRight = (hFR + hRR) * 0.5;
        double deltaRoll = hLeft - hRight;
        float groundRoll = (float) Math.toDegrees(Math.atan2(deltaRoll, halfW * 2.0));

        // 旋回・ドリフトによる遠心力ロール (外側サスペンションが正しく沈み込むように符号を修正)
        float dynamicRoll = 0.0f;
        if (this.driftDirection != 0) {
            float baseRoll = 9.0f;
            float totalRoll = baseRoll + (counterAmount * 8.5f);
            dynamicRoll = -this.driftDirection * totalRoll; // 外側サス沈み込み
        } else {
            dynamicRoll = this.steerAngle * (float)(this.currentSpeed * 3.5); // 外側サス沈み込み
        }
        float targetRoll = Mth.clamp(groundRoll + dynamicRoll, -25.0f, 25.0f);
        this.rollAngle = Mth.lerp(0.35f, this.rollAngle, targetRoll);
    }

    private double sampleGroundY(double x, double y, double z) {
        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(x, y + 0.25, z);
        net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(x, y - 0.35, z);
        net.minecraft.world.phys.BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this
        ));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return hit.getLocation().y;
        }
        return y - 0.35;
    }
}
