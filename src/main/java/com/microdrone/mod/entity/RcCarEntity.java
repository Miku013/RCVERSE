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

    // 操縦者UUID
    private UUID pilotUUID = null;
    private double startX, startY, startZ;
    private UUID dummyEntityUUID = null;
    private ArmorStand dummyEntityRef = null;
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

    // ドリフト走行パラメータ
    // Mario Kart Drift System
    public boolean inputHandbrake = false; // Space key: Drift button
    private Vec3 carVelocity = Vec3.ZERO;  // Inertial movement vector
    private int driftDirection = 0;        // 0: normal, -1: drift left, +1: drift right
    private int driftTicks = 0;            // Drift duration ticks
    private int turboBoostTicks = 0;       // Mini-turbo boost remaining ticks
    private float rollAngle = 0.0f;        // Body roll tilt angle (degrees)
    public float prevRollAngle = 0.0f;     // Previous tick roll for smooth lerp

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
            if (distSq > 16.0) {
                super.lerpTo(x, y, z, yRot, xRot, steps);
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

        // 本物のラジコン感覚: プレイヤーは立ち位置にプロポを持って立ったまま！
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
            ArmorStand targetDummyRef = this.dummyEntityRef;
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
            tickCarPhysics();

            // クライアント同期用角度
            this.entityData.set(DATA_WHEEL_ROTATION, this.wheelRotation);
            this.entityData.set(DATA_STEER_ANGLE, this.steerAngle);
            this.entityData.set(DATA_ROLL_ANGLE, this.rollAngle);
        } else {
            // クライアント側処理
            if (this.locallyControlled) {
                // ローカル操縦中はクライアント側でも即座に物理演算を行い、遅延ゼロ＆カクツキゼロを実現
                tickCarPhysics();
            } else {
                // 他プレイヤーが操縦中または放置中はサーバー同期値を使用
                this.wheelRotation = this.entityData.get(DATA_WHEEL_ROTATION);
                this.steerAngle = this.entityData.get(DATA_STEER_ANGLE);
                this.rollAngle = this.entityData.get(DATA_ROLL_ANGLE);
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
        boolean wantsDrift = this.inputHandbrake && (this.currentSpeed > 0.03f || this.driftDirection != 0);

        if (wantsDrift) {
            // Start drift lock
            if (this.driftDirection == 0) {
                if (this.inputSteer < -0.05f) {
                    this.driftDirection = -1; // Drift Left
                    this.driftTicks = 0;
                    if (this.onGround()) {
                        Vec3 cur = this.getDeltaMovement();
                        this.setDeltaMovement(new Vec3(cur.x, 0.24, cur.z)); // Noticeable Hop jump!
                        this.setOnGround(false);
                        this.hasImpulse = true;
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 1.8F);
                    }
                } else if (this.inputSteer > 0.05f) {
                    this.driftDirection = 1;  // Drift Right
                    this.driftTicks = 0;
                    if (this.onGround()) {
                        Vec3 cur = this.getDeltaMovement();
                        this.setDeltaMovement(new Vec3(cur.x, 0.24, cur.z)); // Noticeable Hop jump!
                        this.setOnGround(false);
                        this.hasImpulse = true;
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 1.8F);
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
                            SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.5F, 1.8F);
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

        // === Body Roll Tilt Angle Calculation (Deeper tilt on counter-steer) ===
        this.prevRollAngle = this.rollAngle;
        float targetRoll = 0.0f;
        if (this.driftDirection != 0) {
            float baseRoll = 9.0f; // Standard drift roll
            float counterAmount = 0.0f;
            if (this.driftDirection == -1 && this.inputSteer > 0.05f) {
                counterAmount = this.inputSteer; // 0.05 ~ 1.0
            } else if (this.driftDirection == 1 && this.inputSteer < -0.05f) {
                counterAmount = -this.inputSteer; // 0.05 ~ 1.0
            }
            // Deep aggressive tilt up to 16.5 degrees when counter-steering!
            float totalRoll = baseRoll + (counterAmount * 7.5f);
            targetRoll = -this.driftDirection * totalRoll;
        }
        this.rollAngle = Mth.lerp(0.35f, this.rollAngle, targetRoll);

        // === Steering / Yaw Turn (Gentle wide drift curve) ===
        if (this.driftDirection != 0 && Math.abs(this.currentSpeed) > 0.002f) {
            float steerMult = 1.0f;
            if (this.driftDirection == -1) {
                if (this.inputSteer < -0.05f) {
                    steerMult = 1.0f + (-this.inputSteer * 0.55f); // In-steer tighter
                } else if (this.inputSteer > 0.05f) {
                    steerMult = 1.0f - (this.inputSteer * 0.75f);  // Counter-steer: very gentle 0.25x
                }
            } else if (this.driftDirection == 1) {
                if (this.inputSteer > 0.05f) {
                    steerMult = 1.0f + (this.inputSteer * 0.55f);  // In-steer tighter
                } else if (this.inputSteer < -0.05f) {
                    steerMult = 1.0f - (-this.inputSteer * 0.75f); // Counter-steer: very gentle 0.25x
                }
            }
            // Base drift turn speed reduced to 4.5f for gentle long slides!
            float driftTurnSpeed = 4.5f * steerMult * (-this.driftDirection);
            this.setYRot(this.getYRot() + driftTurnSpeed);
        } else {
            if (Math.abs(this.currentSpeed) > 0.002f) {
                float baseTurn = (this.currentSpeed > 0 ? 13.5f : -13.5f);
                float turnSpeed = (this.steerAngle / 35.0f) * baseTurn;
                this.setYRot(this.getYRot() - turnSpeed);
            }
        }

        // === Inertial drift movement vector synthesis ===
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double driveX = -Mth.sin(yawRad) * this.currentSpeed;
        double driveZ = Mth.cos(yawRad) * this.currentSpeed;

        if (this.driftDirection != 0) {
            double normalX = Mth.cos(yawRad) * this.currentSpeed * this.driftDirection;
            double normalZ = Mth.sin(yawRad) * this.currentSpeed * this.driftDirection;

            // 0.90 forward + 0.28 slide for broad sweeping smooth curve
            double targetVelX = driveX * 0.90 + normalX * 0.28;
            double targetVelZ = driveZ * 0.90 + normalZ * 0.28;

            this.carVelocity = new Vec3(
                    Mth.lerp(0.18, this.carVelocity.x, targetVelX),
                    0,
                    Mth.lerp(0.18, this.carVelocity.z, targetVelZ)
            );
        } else {
            if (Math.abs(this.currentSpeed) < 0.001f) {
                this.carVelocity = Vec3.ZERO;
            } else {
                this.carVelocity = new Vec3(
                        Mth.lerp(0.55, this.carVelocity.x, driveX),
                        0,
                        Mth.lerp(0.55, this.carVelocity.z, driveZ)
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

        this.setDeltaMovement(new Vec3(motionX, motionY, motionZ));
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 接地摩擦
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0));
        }

        // タイヤ回転角の積算
        this.wheelRotation += (this.currentSpeed * 144.0f);

        // ドリフト白煙エフェクト (クライアント側)
        // Drift smoke effect (when tires lose grip)
        // Drift particles & Mini-Turbo effects
        if (this.level().isClientSide && this.onGround() && Math.abs(this.currentSpeed) > 0.025f) {
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

    public float getRollAngle() {
        return this.rollAngle;
    }
}
