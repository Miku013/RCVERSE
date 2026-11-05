package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity;
import com.microdrone.mod.block.miniature.MiniaturePlacementData;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.DronePilotStatePayload;
import com.microdrone.mod.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private static final EntityDataAccessor<Float> DATA_CUSTOM_WEIGHT =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_RESYNC_BLINK_TICKS =
            SynchedEntityData.defineId(RcCarEntity.class, EntityDataSerializers.INT);

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
    private boolean wasAirborneLastTick = false; // 前tick空中フラグ（着地衝撃ダンパー検知用）
    private boolean onSlope = false;       // スロープ斜面走行中フラグ
    private double slopeClimbDot = 0.0;    // スロープ登り方向と進行方向の内積
    private double slopeSinTheta = 0.0;    // スロープ斜面法線から算出した傾斜sin値 (1段目約0.38, 2段目約0.71)
    private int collisionCooldown = 0;    // 壁衝突SEの連打防止クールダウン (ticks)
    private int jumpCooldown = 0;         // ジャンプ台連続発動防止クールダウン (ticks)
    private int jumpAirborneTicks = 0;    // ジャンプ空中飛行保護タイマー (地面吸着・水平化を防止)
    private int resyncBlinkTicks = 0;     // レスキュー位置復帰点滅タイマー (ticks)

    // レース計測システム (Step 1)
    private int currentLap = 0;                  // 0 = 未スタート, 1 = 1周目...
    private long lapStartTimeMs = 0;             // 現ラップ開始時刻 (ms)
    private long bestLapTimeMs = 0;              // 自己ベストラップタイム (ms)
    private BlockPos lastCheckpointPos = null;   // 直前に通過したチェックポイント座標
    private int lastCheckpointSlot = -1;         // 直前に通過したスロットID
    private int checkpointCooldown = 0;          // 多重反応防止クールダウン (ticks)
    private int passedCheckpointsInLap = 0;      // 現ラップ内の通過チェックポイント数

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
     * 通常走行時はクライアント予測移動を最優先して入力遅延・引きずりをゼロ化。
     * 1.8mを超える異常乖離（壁抜け、スタック等）が発生した時のみ、
     * 機体を一時的に点滅させてサーバー位置へ瞬間強制レスキュー復帰を行う。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            // 3.5m (distSq > 12.25) 超過の重大な位置ズレ発生時 (壁抜け・スタック等の真の事故)、かつ非点滅時にレスキュー復帰発動
            // ※通常走行時の自然なネットワーク通信遅延 (1.5m〜2.0m) では誤作動しない安全な適正閾値
            if (distSq > 12.25 && this.resyncBlinkTicks <= 0) {
                triggerRescueResync(x, y, z, yRot, Math.sqrt(distSq));
            }
            // 自プレイヤー操縦中はソフト引き寄せターゲットを設定しない
            this.hasServerTarget = false;
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    /**
     * サーバーとクライアントの位置が大きく乖離した際の強制レスキュー同期＆点滅演出
     */
    public void triggerRescueResync(double serverX, double serverY, double serverZ, float serverYRot, double deltaDist) {
        MicroDroneMod.LOGGER.warn("[RESCUE_RESYNC] Vehicle: {}, ClientPos: [{}, {}, {}] -> ServerPos: [{}, {}, {}], Delta: {}m",
                this.getUUID(),
                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                String.format("%.2f", serverX), String.format("%.2f", serverY), String.format("%.2f", serverZ),
                String.format("%.2f", deltaDist));

        // 1. サーバー正規座標への瞬間スナップ
        this.setPos(serverX, serverY, serverZ);
        this.setYRot(serverYRot);
        this.yRotO = serverYRot;
        this.carVelocity = Vec3.ZERO;
        this.setDeltaMovement(Vec3.ZERO);
        this.currentSpeed = 0.0f;
        this.pitchAngle = 0.0f;
        this.rollAngle = 0.0f;
        this.setOnGround(true);

        // 2. 点滅タイマー発動 (20 ticks = 約1秒間高速点滅)
        this.resyncBlinkTicks = 20;

        // 3. レスキュー演出音
        this.level().playSound(null, serverX, serverY, serverZ,
                SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.9F, 1.7F);

        // 4. パーティクル演出
        if (this.level().isClientSide) {
            for (int i = 0; i < 8; i++) {
                double ox = (this.random.nextDouble() - 0.5) * 0.4;
                double oy = this.random.nextDouble() * 0.3;
                double oz = (this.random.nextDouble() - 0.5) * 0.4;
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                        serverX + ox, serverY + oy, serverZ + oz,
                        (this.random.nextDouble() - 0.5) * 0.2, 0.1, (this.random.nextDouble() - 0.5) * 0.2);
            }

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§e⚡ [位置同期レスキュー発動]"), true);
            }
        }
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
        builder.define(DATA_CUSTOM_WEIGHT, 1.10f);
        builder.define(DATA_RESYNC_BLINK_TICKS, 0);
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

            // レースセッションリセット
            this.currentLap = 0;
            this.lapStartTimeMs = 0;
            this.lastCheckpointPos = null;
            this.lastCheckpointSlot = -1;
            this.passedCheckpointsInLap = 0;
        }
    }

    // === 自動車物理計算 (Tick) ===
    @Override
    public void tick() {
        super.tick();

        if (this.collisionCooldown > 0) {
            this.collisionCooldown--;
        }
        if (this.checkpointCooldown > 0) {
            this.checkpointCooldown--;
        }
        if (this.jumpCooldown > 0) {
            this.jumpCooldown--;
        }
        if (this.jumpAirborneTicks > 0) {
            this.jumpAirborneTicks--;
        }
        if (this.resyncBlinkTicks > 0) {
            this.resyncBlinkTicks--;
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
            checkCheckpointSensors(pilot);
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
                // ローカル操縦中はクライアント側で即座に物理演算を行い、遅延ゼロ＆ダイレクトな操作感を実現
                long startNanos = System.nanoTime();
                tickCarPhysics();

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
                        double hopY = 0.16 / Math.sqrt(Math.max(0.45, this.getVehicleMass() / 1.10f));
                        this.setDeltaMovement(new Vec3(cur.x, hopY, cur.z)); // Hop jump!
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
                        double hopY = 0.16 / Math.sqrt(Math.max(0.45, this.getVehicleMass() / 1.10f));
                        this.setDeltaMovement(new Vec3(cur.x, hopY, cur.z)); // Hop jump!
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

        // 三角形メッシュ衝突方式: 斜面走行時は法線傾斜(1段目22.5°/2段目45°)に応じた垂直登坂速度を付与して段差衝突をゼロ化
        if (this.jumpAirborneTicks <= 0 && this.onSlope && this.wheelGrounded && Math.abs(this.currentSpeed) > 0.005f) {
            double sinSlope = Math.max(0.38, this.slopeSinTheta); // 1段目0.3827, 2段目0.7071
            double climbVy = this.currentSpeed * sinSlope * this.slopeClimbDot;
            motionY = climbVy;
            this.setOnGround(true);
        } else if (this.jumpAirborneTicks <= 0 && this.onGround() && this.getDeltaMovement().y <= 0.001) {
            motionY = 0.0;
        } else {
            float mass = this.getVehicleMass();
            // ハイブリッド重力: 基礎重力を 0.046 -> 0.064 に引き締め、重さに応じた落下力を強化
            double gravity = 0.064 + Math.max(0.0, (mass - 1.10f) * 0.025);
            // 頂点を過ぎて下降中(Vy < 0)は重力を1.55倍にブースト！ふわふわ滞空せずキュッと素早く着地
            if (this.getDeltaMovement().y < 0.0) {
                gravity *= 1.55;
            }
            motionY = this.getDeltaMovement().y - gravity;
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
                // スロープ斜面走行中は微小な階段スライスとの誤接触で前進を遮断しない
                if (!this.onSlope) {
                    break; // 壁に接触した瞬間に残りのステップを即時遮断！
                }
            }
        }
        Vec3 actualMoved = this.position().subtract(preMovePos);

        // 地面に衝突した（着地した）場合は接地状態を維持 (ジャンプ飛行保護中はバイパス)
        if (this.jumpAirborneTicks <= 0 && this.verticalCollision && motionY <= 0.0) {
            this.setOnGround(true);
        }

        // 移動後に斜面メッシュの上面へ完全吸着（板の下への潜り込みを100%防止、ジャンプ飛行中はバイパス）
        if (this.jumpAirborneTicks <= 0 && this.onSlope) {
            WheelRaycastHit curHit = sampleWheelGround(this.getX(), this.getY(), this.getZ());
            if (curHit.onSlope && this.getY() < curHit.groundY) {
                this.setPos(this.getX(), curHit.groundY, this.getZ());
                this.setOnGround(true);
            }
        }

        // === スロープジャンプ台の跳躍判定 (レイキャスト先端リップ検知) ===
        if (this.jumpCooldown <= 0 && this.currentSpeed > 0.05f) {
            // 前輪位置または車体中心位置でスロープ先端リップを判定
            double frontX = this.getX() + driveX * 0.20;
            double frontZ = this.getZ() + driveZ * 0.20;
            WheelRaycastHit hitCenter = sampleWheelGround(this.getX(), this.getY(), this.getZ());
            WheelRaycastHit hitFront = sampleWheelGround(frontX, this.getY(), frontZ);

            boolean isLipReached = (hitCenter.onSlope && hitCenter.isLip) || (hitFront.onSlope && hitFront.isLip);
            boolean isClimbing = this.slopeClimbDot > 0.20; // 登坂方向に向かって前進している

            if (isLipReached && isClimbing) {
                boolean isBoost = hitCenter.isBoost || hitFront.isBoost;
                this.jumpCooldown = 22; // 約1.1秒クールダウン
                this.setOnGround(false);
                this.hasImpulse = true;

                float mass = this.getVehicleMass();
                // 車重による打ち上げ初速の低下を完全撤廃！重いマシンでもしっかり高く打ち上がる
                double massBounce = 1.0 + Math.max(0.0, (mass - 1.10f) * 0.08); // 重いほどサス反発で力強く弾き出される

                if (isBoost) {
                    // ブーストジャンプ台（ダッシュパネル）: 強制ロケット加速 + 空高く駆け上がる大ジャンプ！
                    this.turboBoostTicks = 35;
                    this.currentSpeed = (float) Math.max(this.currentSpeed * 1.65, 0.58);
                    // 強化された重力に合わせて初速を0.92+へ底上げ
                    double jumpY = (0.92 + (this.currentSpeed * 0.65)) * massBounce;
                    this.carVelocity = new Vec3(this.carVelocity.x * 1.45, jumpY, this.carVelocity.z * 1.45);
                    this.setDeltaMovement(this.carVelocity);
                    this.pitchAngle = -34.0f; // 豪快なノーズアップ空中姿勢
                    this.jumpAirborneTicks = 18; // 空中モード維持
                    this.setPos(this.getX(), this.getY() + 0.15, this.getZ()); // リップから浮かせる

                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.6F);
                } else {
                    // 通常ジャンプ台: 45°キッカーのノーズアップ方向へ豪快に駆け上がる放物線跳躍！
                    // 強化された重力に合わせて初速を0.76+へ底上げ
                    double jumpY = (0.76 + (this.currentSpeed * 0.60)) * massBounce;
                    double fwdBoost = 1.15 + (0.05 * (mass - 1.10f)); // 慣性で前方に突き抜ける
                    this.carVelocity = new Vec3(this.carVelocity.x * fwdBoost, jumpY, this.carVelocity.z * fwdBoost);
                    this.setDeltaMovement(this.carVelocity);
                    this.pitchAngle = -30.0f; // 45°キッカーからのノーズアップ空中姿勢
                    this.jumpAirborneTicks = 15; // 空中モード維持
                    this.setPos(this.getX(), this.getY() + 0.12, this.getZ()); // リップから浮かせる

                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_ELYTRA.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
                }
            }
        }

        // 衝突検知 (高さ0.10m以上の壁・障害物に激突して移動量が大幅に遮断された場合のみ、マシンの速度を急減衰して確実にストップ)
        // ※ レーシング縁石(高さ0.019m〜0.038m)やスロープ登坂時は激突と判定せずスムーズに通過
        double expectedH = totalDistH;
        double actualH = Math.sqrt(actualMoved.x * actualMoved.x + actualMoved.z * actualMoved.z);
        boolean isBlocked = !this.onSlope && (this.horizontalCollision || (expectedH > 0.03 && actualH < expectedH * 0.40));
        boolean hitSolidWall = isBlocked && isObstacleInDirection(motionX, motionZ, 0.10);

        if (hitSolidWall) {
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

        ItemStack heldItem = player.getItemInHand(hand);

        // プロポ所持 + スニーク右クリック: 車重＆物理チューニングGUI画面を開く
        if (heldItem.getItem() instanceof com.microdrone.mod.item.DroneControllerItem && player.isShiftKeyDown()) {
            if (this.level().isClientSide) {
                com.microdrone.mod.client.gui.ClientGuiOpener.openRcCarTuningScreen(this);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (!this.level().isClientSide) {

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

    /**
     * 車重 (kg) の取得 (アプローチ3: プロポGUIチューニング)
     */
    public float getVehicleMass() {
        return this.entityData.get(DATA_CUSTOM_WEIGHT);
    }

    /**
     * 車重 (kg) の設定 (0.50kg ~ 2.50kg)
     */
    public void setCustomWeight(float weightKg) {
        float clamped = Mth.clamp(weightKg, 0.50f, 2.50f);
        this.entityData.set(DATA_CUSTOM_WEIGHT, clamped);
    }

    public int getResyncBlinkTicks() {
        if (this.level().isClientSide && this.locallyControlled) {
            return this.resyncBlinkTicks;
        }
        return this.entityData.get(DATA_RESYNC_BLINK_TICKS);
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
        if (compound.contains("CustomWeight", 99)) {
            this.entityData.set(DATA_CUSTOM_WEIGHT, compound.getFloat("CustomWeight"));
        }
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
        compound.putFloat("CustomWeight", this.entityData.get(DATA_CUSTOM_WEIGHT));
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
     * ① 移動方向障害物スキャン: 指定方向に衝突する壁（ガードレール等）が存在するか検知
     * レーシング縁石（高さ0.10m以下）は乗り越え可能なので壁と判定せず、走行を阻害しない。
     */
    private boolean isObstacleInDirection(double dirX, double dirZ, double checkDistance) {
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        double nx, nz;
        if (len > 0.001) {
            nx = dirX / len;
            nz = dirZ / len;
        } else {
            float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
            nx = -Mth.sin(yawRad);
            nz = Mth.cos(yawRad);
        }

        net.minecraft.world.phys.AABB currentBox = this.getBoundingBox();
        net.minecraft.world.phys.AABB checkAABB = currentBox.expandTowards(nx * checkDistance, 0.0, nz * checkDistance);

        Iterable<net.minecraft.world.phys.shapes.VoxelShape> collisions = this.level().getBlockCollisions(this, checkAABB);
        for (net.minecraft.world.phys.shapes.VoxelShape shape : collisions) {
            if (!shape.isEmpty()) {
                for (net.minecraft.world.phys.AABB aabb : shape.toAabbs()) {
                    // スロープブロック（ジャンプ台）への進入は壁激突と判定しない
                    BlockPos bp = BlockPos.containing((aabb.minX + aabb.maxX) * 0.5, aabb.minY + 0.05, (aabb.minZ + aabb.maxZ) * 0.5);
                    if (this.level().getBlockState(bp).getBlock() instanceof com.microdrone.mod.block.RacingSlopeBlock) {
                        continue;
                    }

                    // 高さ判定: レーシング縁石(高さ約0.019m〜0.038m)は除外、高さ0.10m以上の壁をブロック対象とする
                    if (aabb.maxY - this.getY() > 0.10) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isObstacleAhead(double checkDistance) {
        float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
        return isObstacleInDirection(-Mth.sin(yawRad), Mth.cos(yawRad), checkDistance);
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
     * タイヤごとの接地・三角形メッシュ衝突判定結果
     */
    public static class WheelRaycastHit {
        public final double groundY;
        public final Vec3 normal;
        public final boolean onSlope;
        public final boolean isLip;
        public final boolean isBoost;
        public final double progress;
        public final Direction slopeFacing;

        public WheelRaycastHit(double groundY, Vec3 normal, boolean onSlope, boolean isLip, boolean isBoost, double progress, Direction slopeFacing) {
            this.groundY = groundY;
            this.normal = normal;
            this.onSlope = onSlope;
            this.isLip = isLip;
            this.isBoost = isBoost;
            this.progress = progress;
            this.slopeFacing = slopeFacing;
        }

        public static final WheelRaycastHit FLAT(double y) {
            return new WheelRaycastHit(y, new Vec3(0, 1, 0), false, false, false, 0.0, Direction.NORTH);
        }
    }

    /**
     * 4点仮想サスペンション物理シミュレーション (レイキャスト × 三角形メッシュ衝突方式)
     * 4輪のメッシュ交差高さからサス伸縮、路面追従、登坂ピッチ・片輪ロール・車体吸着を精密計算
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

        // 4輪位置の地面高さを三角形メッシュレイキャストで精密サンプリング
        WheelRaycastHit hitFL = sampleWheelGround(px + fwdX * halfL - rightX * halfW, py, pz + fwdZ * halfL - rightZ * halfW);
        WheelRaycastHit hitFR = sampleWheelGround(px + fwdX * halfL + rightX * halfW, py, pz + fwdZ * halfL + rightZ * halfW);
        WheelRaycastHit hitRL = sampleWheelGround(px - fwdX * halfL - rightX * halfW, py, pz - fwdZ * halfL - rightZ * halfW);
        WheelRaycastHit hitRR = sampleWheelGround(px - fwdX * halfL + rightX * halfW, py, pz - fwdZ * halfL + rightZ * halfW);

        double hFL = hitFL.groundY;
        double hFR = hitFR.groundY;
        double hRL = hitRL.groundY;
        double hRR = hitRR.groundY;

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

        // ジャンプ台から飛び出した直後（空中飛行中）は、路面接地や斜面吸着を強制バイパス！
        if (this.jumpAirborneTicks > 0) {
            this.wheelGrounded = false;
            this.onSlope = false;
            this.setOnGround(false);
        } else {
            // 4輪中いずれかが路面に接触・追従しているか
            this.wheelGrounded = (hFL >= py - 0.08 || hFR >= py - 0.08 || hRL >= py - 0.08 || hRR >= py - 0.08);
            this.onSlope = hitFL.onSlope || hitFR.onSlope || hitRL.onSlope || hitRR.onSlope;
        }

        // 着地衝撃ダンパー: 空中から接地した瞬間に車重と落下速度に応じたサスペンション沈み込みを発生
        boolean isGroundedNow = this.jumpAirborneTicks <= 0 && (this.onGround() || this.wheelGrounded);
        if (this.wasAirborneLastTick && isGroundedNow) {
            float mass = this.getVehicleMass();
            double impactSpeed = Math.abs(this.getDeltaMovement().y);
            if (impactSpeed > 0.05) {
                float impactCompress = (float) Math.min(0.08, impactSpeed * 0.45 * (mass / 1.10f));
                this.suspFL = Math.min(0.08f, this.suspFL + impactCompress);
                this.suspFR = Math.min(0.08f, this.suspFR + impactCompress);
                this.suspRL = Math.min(0.08f, this.suspRL + impactCompress);
                this.suspRR = Math.min(0.08f, this.suspRR + impactCompress);

                float pitch = 1.30f - (mass * 0.25f);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.7F, pitch);
            }
        }
        this.wasAirborneLastTick = !isGroundedNow;

        // 斜面判定＆登り方向ベクトルの内積計算
        if (this.onSlope) {
            // 最も支配的なスロープの向きを取得
            Direction slopeDir = hitFL.onSlope ? hitFL.slopeFacing : (hitFR.onSlope ? hitFR.slopeFacing : (hitRL.onSlope ? hitRL.slopeFacing : hitRR.slopeFacing));
            double slopeFwdX = switch (slopeDir) {
                case EAST -> 1.0;
                case WEST -> -1.0;
                default -> 0.0;
            };
            double slopeFwdZ = switch (slopeDir) {
                case SOUTH -> 1.0;
                case NORTH -> -1.0;
                default -> 0.0;
            };
            this.slopeClimbDot = (fwdX * slopeFwdX) + (fwdZ * slopeFwdZ);

            // 4輪の接地法線から現在の傾斜サイン値 (1段目約0.38, 2段目約0.71) を動的算出
            double avgNx = (hitFL.normal.x + hitFR.normal.x + hitRL.normal.x + hitRR.normal.x) * 0.25;
            double avgNz = (hitFL.normal.z + hitFR.normal.z + hitRL.normal.z + hitRR.normal.z) * 0.25;
            this.slopeSinTheta = Math.sqrt(avgNx * avgNx + avgNz * avgNz);
        } else {
            this.slopeClimbDot = 0.0;
            this.slopeSinTheta = 0.0;
        }

        // スロープ（斜面）上での車体沈み込み防止: 車体底面を三角形メッシュ表面へ完全に吸着・リフト (空中飛行中はバイパス)
        if (this.jumpAirborneTicks <= 0) {
            double avgGroundY = (hFL + hFR + hRL + hRR) * 0.25;
            if (this.onSlope) {
                // 斜面上では常に路面高さに車体を滑らかに密着・吸着させて沈み込みを100%遮断
                if (py < avgGroundY || (py - avgGroundY < 0.10 && this.getDeltaMovement().y <= 0.02)) {
                    this.setPos(px, avgGroundY, pz);
                    py = avgGroundY;
                    this.setOnGround(true);
                }
            } else if (py < avgGroundY && (this.onGround() || this.wheelGrounded)) {
                double liftY = Math.min(avgGroundY, py + 0.10);
                this.setPos(px, liftY, pz);
                py = liftY;
            }
        }

        // === 前後ピッチ角 (路面傾斜 + 加減速G / 空中放物線ノーズダイブ) ===
        if (!this.onGround() && !this.wheelGrounded) {
            double vY = this.getDeltaMovement().y;
            double vH = Math.sqrt(this.carVelocity.x * this.carVelocity.x + this.carVelocity.z * this.carVelocity.z);
            float targetFlightPitch;
            if (vY > 0.08) {
                // 上昇中: 飛び出し時のノーズアップ姿勢（-22°〜-34°）をしっかり維持！ノーズ方向に高さがぐんぐん上がる
                float climbAngle = (float) -Math.toDegrees(Math.atan2(vY, Math.max(0.06, vH)));
                targetFlightPitch = Mth.clamp(climbAngle, -34.0f, -20.0f);
                this.pitchAngle = Mth.lerp(0.15f, this.pitchAngle, targetFlightPitch);
            } else if (vY > -0.06) {
                // 放物線の頂点付近: ノーズアップから水平（0°）へ滑らかに遷移
                targetFlightPitch = 0.0f;
                this.pitchAngle = Mth.lerp(0.18f, this.pitchAngle, targetFlightPitch);
            } else {
                // 下降中: 落下速度に応じたノーズダイブ（+15°〜+26°、前傾接地姿勢で地面へ吸い込まれる）
                float diveAngle = (float) -Math.toDegrees(Math.atan2(vY, Math.max(0.06, vH)));
                targetFlightPitch = Mth.clamp(diveAngle, 0.0f, 26.0f);
                this.pitchAngle = Mth.lerp(0.20f, this.pitchAngle, targetFlightPitch);
            }
        } else {
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
            float targetPitch = Mth.clamp(groundPitch + accelPitch, -48.0f, 48.0f);
            this.pitchAngle = Mth.lerp(0.35f, this.pitchAngle, targetPitch);
        }

        // === 左右ロール角 (路面傾斜 + 旋回・ドリフト遠心力) ===
        double hLeft = (hFL + hRL) * 0.5;
        double hRight = (hFR + hRR) * 0.5;
        double deltaRoll = hLeft - hRight;
        float groundRoll = (float) Math.toDegrees(Math.atan2(deltaRoll, halfW * 2.0));

        // 旋回・ドリフトによる遠心力ロール (外側サスペンションが正しく沈み込む)
        float dynamicRoll = 0.0f;
        if (this.driftDirection != 0) {
            float baseRoll = 9.0f;
            float totalRoll = baseRoll + (counterAmount * 8.5f);
            dynamicRoll = -this.driftDirection * totalRoll;
        } else {
            dynamicRoll = this.steerAngle * (float)(this.currentSpeed * 3.5);
        }
        float targetRoll = Mth.clamp(groundRoll + dynamicRoll, -25.0f, 25.0f);
        this.rollAngle = Mth.lerp(0.35f, this.rollAngle, targetRoll);
    }

    /**
     * タイヤ位置の地面高さ・三角形メッシュ衝突レイキャスト
     */
    public WheelRaycastHit sampleWheelGround(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        net.minecraft.world.level.block.state.BlockState st = level().getBlockState(pos);
        if (!(st.getBlock() instanceof com.microdrone.mod.block.RacingSlopeBlock)) {
            pos = pos.below();
            st = level().getBlockState(pos);
        }
        if (st.getBlock() instanceof com.microdrone.mod.block.RacingSlopeBlock) {
            double relX = x - pos.getX();
            double relZ = z - pos.getZ();
            com.microdrone.mod.block.RacingSlopeBlock.RampHitResult hit =
                    com.microdrone.mod.block.RacingSlopeBlock.raycastSlopeMesh(st, relX, relZ);
            if (hit.hit) {
                Direction facing = st.getValue(com.microdrone.mod.block.RacingSlopeBlock.FACING);
                return new WheelRaycastHit(pos.getY() + hit.height, hit.normal, true, hit.isLip, hit.isBoost, hit.progress, facing);
            }
        }

        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(x, y + 0.25, z);
        net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(x, y - 0.35, z);
        net.minecraft.world.phys.BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this
        ));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return WheelRaycastHit.FLAT(hit.getLocation().y);
        }
        return WheelRaycastHit.FLAT(y - 0.35);
    }

    private double sampleGroundY(double x, double y, double z) {
        return sampleWheelGround(x, y, z).groundY;
    }

    // === レース計測・チェックポイント通過センシング (Step 1) ===
    private void checkCheckpointSensors(ServerPlayer pilot) {
        Vec3 pos = this.position();
        BlockPos atPos = BlockPos.containing(pos.x, pos.y, pos.z);
        BlockPos belowPos = atPos.below();

        BlockPos[] checkPositions = new BlockPos[]{
                atPos,
                belowPos,
                BlockPos.containing(pos.x, pos.y + 0.25, pos.z)
        };

        MiniaturePlacementBlockEntity targetBe = null;
        BlockPos targetPos = null;

        for (BlockPos cp : checkPositions) {
            if (this.level().getBlockEntity(cp) instanceof MiniaturePlacementBlockEntity be) {
                targetBe = be;
                targetPos = cp;
                break;
            }
        }

        if (targetBe == null) {
            // タイヤの接地範囲（四方0.25m）も走査
            double[][] offsets = {
                    {0.25, 0.0}, {-0.25, 0.0}, {0.0, 0.25}, {0.0, -0.25}
            };
            for (double[] off : offsets) {
                BlockPos p = BlockPos.containing(pos.x + off[0], pos.y, pos.z + off[1]);
                if (this.level().getBlockEntity(p) instanceof MiniaturePlacementBlockEntity be) {
                    targetBe = be;
                    targetPos = p;
                    break;
                }
                BlockPos pb = p.below();
                if (this.level().getBlockEntity(pb) instanceof MiniaturePlacementBlockEntity be) {
                    targetBe = be;
                    targetPos = pb;
                    break;
                }
            }
        }

        if (targetBe != null && targetPos != null) {
            double relX = pos.x - targetPos.getX();
            double relZ = pos.z - targetPos.getZ();

            for (MiniaturePlacementData slotData : targetBe.getAllSlots()) {
                String id = slotData.getMiniatureId();
                boolean isGate = "goal_gate".equals(id);
                boolean isCheckpoint = "checkpoint_plate".equals(id);

                if (!isGate && !isCheckpoint) continue;

                float slotCenterX = MiniaturePlacementData.getSlotCenterX(slotData.getSlotId());
                float slotCenterZ = MiniaturePlacementData.getSlotCenterZ(slotData.getSlotId());

                double dx = relX - slotCenterX;
                double dz = relZ - slotCenterZ;
                double distSq = dx * dx + dz * dz;

                // ゴールゲートは3スロット分のワイド設計なので判定半径0.5m、プレートは0.3m
                double triggerRadius = isGate ? 0.50 : 0.30;
                if (distSq <= triggerRadius * triggerRadius) {
                    triggerSensorPass(targetPos, slotData.getSlotId(), isGate, pilot);
                    break;
                }
            }
        }
    }

    private void triggerSensorPass(BlockPos pos, int slotId, boolean isGate, ServerPlayer pilot) {
        // クールダウン処理（同一スロットの連続反応防止 & チャタリング防止）
        if (this.checkpointCooldown > 0) {
            if (pos.equals(this.lastCheckpointPos) && slotId == this.lastCheckpointSlot) {
                return;
            }
            if (this.checkpointCooldown > 20) {
                return;
            }
        }

        long now = System.currentTimeMillis();

        // 不正防止: ゴールゲート通過によるラップ進行は、スタート/前周から10秒以上経過が必須
        if (isGate && this.currentLap > 0) {
            long lapTimeMs = now - this.lapStartTimeMs;
            if (lapTimeMs < 10000L) {
                if (pilot != null) {
                    long remainSec = (10000L - lapTimeMs + 999) / 1000;
                    pilot.displayClientMessage(Component.literal("§c§l[LAP INVALID] §e10秒未満のため無効 §7(あと " + remainSec + "秒)"), true);
                }
                return;
            }
        }

        this.lastCheckpointPos = pos;
        this.lastCheckpointSlot = slotId;
        this.checkpointCooldown = 30; // 1.5秒クールダウン

        // 音源再生 (ユーザー指定: 音量50% = 0.5f)
        this.level().playSound(
                null,
                this.getX(), this.getY(), this.getZ(),
                ModSounds.CHECKPOINT_PASS.get(),
                SoundSource.PLAYERS,
                0.5f,
                1.0f
        );

        if (isGate) {
            if (this.currentLap == 0) {
                // レース開始 (Start Line)
                this.currentLap = 1;
                this.lapStartTimeMs = now;
                this.passedCheckpointsInLap = 0;
                if (pilot != null) {
                    pilot.displayClientMessage(Component.literal("§a§l[START!] §f§lRace Started! §7(LAP 1)"), true);
                }
                com.microdrone.mod.block.entity.RaceDisplayBlockEntity.broadcastRaceUpdate(
                        this.level(), this.position(), 64.0,
                        pilot != null ? pilot.getName().getString() : "---",
                        1, 0, this.bestLapTimeMs, 0, 0, true);
            } else {
                // 周回完了 (Finish Line)
                long lapTimeMs = now - this.lapStartTimeMs;
                boolean isNewRecord = (this.bestLapTimeMs == 0 || lapTimeMs < this.bestLapTimeMs);
                if (isNewRecord) {
                    this.bestLapTimeMs = lapTimeMs;
                }

                String lapTimeStr = formatRaceTime(lapTimeMs);
                String bestTimeStr = formatRaceTime(this.bestLapTimeMs);

                if (pilot != null) {
                    String prefix = isNewRecord ? "§d§l★NEW RECORD! " : "";
                    String msg = String.format("%s§6§l[LAP %d] §e§l%s §7(Best: %s)",
                            prefix, this.currentLap, lapTimeStr, bestTimeStr);
                    pilot.displayClientMessage(Component.literal(msg), true);
                }

                com.microdrone.mod.block.entity.RaceDisplayBlockEntity.broadcastRaceUpdate(
                        this.level(), this.position(), 64.0,
                        pilot != null ? pilot.getName().getString() : "---",
                        this.currentLap, lapTimeMs, this.bestLapTimeMs, 0, 0, true);

                this.currentLap++;
                this.lapStartTimeMs = now;
                this.passedCheckpointsInLap = 0;
            }
        } else {
            // チェックポイント通過
            this.passedCheckpointsInLap++;
            if (this.currentLap > 0) {
                long splitTimeMs = now - this.lapStartTimeMs;
                String splitTimeStr = formatRaceTime(splitTimeMs);
                if (pilot != null) {
                    String msg = String.format("§b§l[CP %d] §f§l%s §7(Lap %d)",
                            this.passedCheckpointsInLap, splitTimeStr, this.currentLap);
                    pilot.displayClientMessage(Component.literal(msg), true);
                }
                com.microdrone.mod.block.entity.RaceDisplayBlockEntity.broadcastRaceUpdate(
                        this.level(), this.position(), 64.0,
                        pilot != null ? pilot.getName().getString() : "---",
                        this.currentLap, 0, this.bestLapTimeMs, splitTimeMs, this.passedCheckpointsInLap, true);
            } else {
                // レース前テスト通過
                if (pilot != null) {
                    pilot.displayClientMessage(Component.literal("§b§l[CHECKPOINT] §a§lPASS!"), true);
                }
                com.microdrone.mod.block.entity.RaceDisplayBlockEntity.broadcastRaceUpdate(
                        this.level(), this.position(), 64.0,
                        pilot != null ? pilot.getName().getString() : "---",
                        0, 0, 0, 0, 1, false);
            }
        }
    }

    private String formatRaceTime(long ms) {
        long minutes = (ms / 60000);
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format("%02d'%02d\"%03d", minutes, seconds, millis);
    }
}
