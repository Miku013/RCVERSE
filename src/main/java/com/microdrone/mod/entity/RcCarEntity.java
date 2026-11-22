package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.block.ModBlocks;
import com.microdrone.mod.block.VerticalLoopBlock;
import com.microdrone.mod.block.miniature.MiniaturePlacementBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.microdrone.mod.block.miniature.MiniaturePlacementData;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.DronePilotStatePayload;
import com.microdrone.mod.physics.DrivingMode;
import com.microdrone.mod.physics.RealCarPhysics;
import com.microdrone.mod.decal.CarDecalData;
import com.microdrone.mod.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.item.DyeItem;
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

public class RcCarEntity extends Entity implements GeoEntity, RcControllable, com.microdrone.mod.decal.RcDecalHolder, com.microdrone.mod.sync.ISnapshotInterpolated {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === マルチプレイ同期用 スナップショット補間バッファ（第1段階） ===
    private final com.microdrone.mod.sync.VehicleSnapshotBuffer snapshotBuffer = new com.microdrone.mod.sync.VehicleSnapshotBuffer();

    @Override
    public com.microdrone.mod.sync.VehicleSnapshotBuffer getSnapshotBuffer() {
        return this.snapshotBuffer;
    }

    // === 同期用 EntityData ===
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING = SynchedEntityData
            .defineId(RcCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY = SynchedEntityData
            .defineId(RcCarEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> DATA_MOTOR_TIER = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_MOTOR = SynchedEntityData
            .defineId(RcCarEntity.class, EntityDataSerializers.ITEM_STACK);

    // 車輪回転角 & ステアリング操舵角（クライアント描画用）
    private static final EntityDataAccessor<Float> DATA_WHEEL_ROTATION = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_STEER_ANGLE = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_ANGLE = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH_ANGLE = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_FL = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_FR = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_RL = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SUSP_RR = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CUSTOM_WEIGHT = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_RESYNC_BLINK_TICKS = SynchedEntityData
            .defineId(RcCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_VARIANT_ID = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_IN_LOOP = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_LOOP_PROGRESS = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_CHASSIS_BOUNCE = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HEADLIGHT = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BRAKING = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_REAL_MODE = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_COUNTER_MAX_ANGLE = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DRIFT_START_ANGLE = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_BODY_COLOR = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<CompoundTag> DATA_DECALS = SynchedEntityData.defineId(RcCarEntity.class,
            EntityDataSerializers.COMPOUND_TAG);

    // 車体ステッカー（デカール）リスト
    private final java.util.List<CarDecalData> decals = new java.util.ArrayList<>();

    // 操縦者UUID
    private UUID pilotUUID = null;
    private double startX = Double.NaN, startY = Double.NaN, startZ = Double.NaN;
    private UUID dummyEntityUUID = null;
    private RcPilotDummyEntity dummyEntityRef = null;
    private ChunkPos lastChunkPos = null;

    // 装着中のアイテム
    private ItemStack installedBattery = ItemStack.EMPTY;
    private ItemStack installedMotor = ItemStack.EMPTY;
    private ItemStack installedTire = ItemStack.EMPTY;
    private double accumulatedDistance = 0.0;

    public ItemStack getInstalledTire() {
        return this.installedTire;
    }

    public com.microdrone.mod.item.RcTireItem.TireType getTireType() {
        if (!this.installedTire.isEmpty() && this.installedTire.getItem() instanceof com.microdrone.mod.item.RcTireItem tireItem) {
            return tireItem.getTireType();
        }
        return com.microdrone.mod.item.RcTireItem.TireType.NORMAL;
    }

    // 操作入力（サーバー/クライアント共用）
    public float inputForward = 0.0f; // 前進(+1.0) / 後退(-1.0)
    private float prevInputForward = 0.0f; // 直前tickのアクセル入力（踏み直し・チョッピング検出用）
    public float inputSteer = 0.0f; // 左ステアリング(-1.0) / 右ステアリング(+1.0)

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
    private Vec3 carVelocity = Vec3.ZERO; // Inertial movement vector
    private int driftDirection = 0; // 0: normal, -1: drift left, +1: drift right
    private int driftTicks = 0; // Drift duration ticks
    private int turboBoostTicks = 0; // Mini-turbo boost remaining ticks
    private float rollAngle = 0.0f; // Body roll tilt angle (degrees)
    public float prevRollAngle = 0.0f; // Previous tick roll for smooth lerp
    private float pitchAngle = 0.0f; // Body pitch tilt angle (degrees)
    public float prevPitchAngle = 0.0f; // Previous tick pitch for smooth lerp

    // 4輪サスペンションのストローク伸縮量 (ブロック単位, 正=縮み, 負=伸び)
    private float suspFL = 0.0f;
    private float suspFR = 0.0f;
    private float suspRL = 0.0f;
    private float suspRR = 0.0f;
    private boolean wheelGrounded = false; // 仮想サスペンション（タイヤ）の接地判定
    private boolean wasAirborneLastTick = false; // 前tick空中フラグ（着地衝撃ダンパー検知用）
    private boolean onSlope = false; // スロープ斜面走行中フラグ
    private double slopeClimbDot = 0.0; // スロープ登り方向と進行方向の内積
    private double slopeSinTheta = 0.0; // スロープ斜面法線から算出した傾斜sin値 (1段目約0.38, 2段目約0.71)
    private int collisionCooldown = 0; // 壁衝突SEの連打防止クールダウン (ticks)
    private int jumpCooldown = 0; // ジャンプ台連続発動防止クールダウン (ticks)
    private int jumpAirborneTicks = 0; // ジャンプ空中飛行保護タイマー (地面吸着・水平化を防止)
    private int jumpPadLaunchTicks = 0; // ジャンプパッド上昇シーケンスタイマー (ticks)
    private int jumpPadPower = 0; // ジャンプパッドのパワー段階 (1:LOW, 2:MID, 3:HIGH)
    private boolean jumpPadExpelled = false; // 登り切った時の前方吐き出し実行済みフラグ
    private int resyncBlinkTicks = 0; // レスキュー位置復帰点滅タイマー (ticks)

    // ヘッドライト・リトラクタブルシステム
    private float prevHeadlightProgress = 0.0f;
    private float headlightProgress = 0.0f; // 0.0f (閉) ~ 1.0f (全開)
    private BlockPos lastHeadlightPos = null;

    // レース計測システム (Step 1)
    private int currentLap = 0; // 0 = 未スタート, 1 = 1周目...
    private long lapStartTimeMs = 0; // 現ラップ開始時刻 (ms)
    private long bestLapTimeMs = 0; // 自己ベストラップタイム (ms)
    private BlockPos lastCheckpointPos = null; // 直前に通過したチェックポイント座標
    private int lastCheckpointSlot = -1; // 直前に通過したスロットID
    private int checkpointCooldown = 0; // 多重反応防止クールダウン (ticks)
    private int passedCheckpointsInLap = 0; // 現ラップ内の通過チェックポイント数

    // クライアント主導位置同期（ステップ2）
    private boolean clientSyncActive = false;
    private int clientSyncTimeout = 0;

    // 360°縦ループ（Loop-the-Loop）完全自由物理ステート
    private boolean onLoop = false;
    private boolean inLoop = false;
    private float loopProgress = 0.0f; // 進行角度 (0.0f 〜 360.0f 度)
    private float loopAngle = 0.0f;
    private BlockPos loopBlockPos = null;
    private Direction loopFacing = Direction.NORTH;

    // 着地サスペンションダンパー・車体バウンス振動
    private float chassisBounce = 0.0f;
    private float chassisBounceVelocity = 0.0f;
    private double lastFallSpeed = 0.0; // 着地前の落下垂直速度 (着地衝撃計算用)

    // REALモード専用物理演算エンジン (RACEモードとは完全分離)
    private final RealCarPhysics realPhysics = new RealCarPhysics();

    public RealCarPhysics getRealPhysics() {
        return this.realPhysics;
    }

    public boolean isRealMode() {
        return this.entityData.get(DATA_REAL_MODE);
    }

    public DrivingMode getDrivingMode() {
        return DrivingMode.fromBoolean(isRealMode());
    }

    public float getCounterMaxAngle() {
        return this.entityData.get(DATA_COUNTER_MAX_ANGLE);
    }

    public void setCounterMaxAngle(float angleDeg) {
        float clamped = Mth.clamp(angleDeg, 35.0f, 65.0f);
        this.entityData.set(DATA_COUNTER_MAX_ANGLE, clamped);
        this.realPhysics.setCounterMaxAngleDeg(clamped);
    }

    public float getDriftStartAngle() {
        return this.entityData.get(DATA_DRIFT_START_ANGLE);
    }

    public void setDriftStartAngle(float angleDeg) {
        float clamped = Mth.clamp(angleDeg, 10.0f, 35.0f);
        this.entityData.set(DATA_DRIFT_START_ANGLE, clamped);
        this.realPhysics.setDriftStartAngleDeg(clamped);
    }

    /**
     * 走行モード（RACE / REAL）を切り替える。
     * 現在の速度ベクトル・向きを維持したまま滑らかに引き継ぐ。
     */
    public void setDrivingMode(boolean realMode) {
        boolean prev = this.entityData.get(DATA_REAL_MODE);
        this.entityData.set(DATA_REAL_MODE, realMode);
        if (prev != realMode) {
            if (realMode) {
                this.realPhysics.syncFromWorldVelocity(this.carVelocity, this.getYRot(), this.steerAngle);
                this.driftDirection = 0;
                this.driftTicks = 0;
                this.turboBoostTicks = 0;
            } else {
                this.driftDirection = 0;
                this.driftTicks = 0;
                // REALの進行成分をRACEのcurrentSpeedへ維持
                float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
                double fwdX = -Mth.sin(yawRad);
                double fwdZ = Mth.cos(yawRad);
                this.currentSpeed = (float) (this.carVelocity.x * fwdX + this.carVelocity.z * fwdZ);
            }
        }
    }

    public boolean isInLoop() {
        return this.inLoop;
    }

    public float getLoopProgress() {
        return this.loopProgress;
    }

    public float getCurrentSpeed() {
        return this.currentSpeed;
    }

    public int getDriftDirection() {
        return this.driftDirection;
    }

    /**
     * クライアント主導位置同期 (ステップ2: サーバー追従)
     * 操縦者クライアントが手元で計算した正規の位置・速度・Yawを受け取り、サーバー側の車両を追従させる。
     */
    public void applyClientPositionSync(double cx, double cy, double cz, float speed, float yaw) {
        if (this.level().isClientSide) return;

        // サーバー側の現在位置との距離
        double dx = cx - this.getX();
        double dy = cy - this.getY();
        double dz = cz - this.getZ();
        double movedDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // マルチプレイ不正対策（簡易バリデーション）:
        // 撮影モード時は閾値を32mに拡大して同期拒否による取り残されテレポートを完全防止
        double maxLimit = com.microdrone.mod.cinematic.CinematicRecordingManager.isEnabled() ? 32.0 : 3.0;
        if (movedDist <= maxLimit || !this.clientSyncActive) {
            this.setPos(cx, cy, cz);
            this.setYRot(yaw);
            this.yRotO = yaw;
            this.currentSpeed = speed;

            if (this.isRealMode() && this.clientSyncActive) {
                // REALモードでは横滑り成分を消さないよう実際の位置差分からワールド速度を再構成
                this.carVelocity = new Vec3(dx, this.getDeltaMovement().y, dz);
                this.realPhysics.syncFromWorldVelocity(this.carVelocity, yaw, this.steerAngle);
            } else {
                float yawRad = yaw * Mth.DEG_TO_RAD;
                double driveX = -Mth.sin(yawRad);
                double driveZ = Mth.cos(yawRad);
                this.carVelocity = new Vec3(driveX * speed, this.getDeltaMovement().y, driveZ * speed);
            }

            this.clientSyncActive = true;
            this.clientSyncTimeout = 5; // 5 Tick以上パケットが届かなければ自律物理へ安全フォールバック
        } else {
            MicroDroneMod.LOGGER.warn("[CLIENT_SYNC_REJECTED] Vehicle: {}, MovedDist: {}m exceeds limit!", this.getUUID(), String.format("%.2f", movedDist));
        }
    }

    public RcCarEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;

    }

    public void setLocallyControlled(boolean locallyControlled) {
        this.locallyControlled = locallyControlled;
    }

    public boolean isLocallyControlled() {
        if (this.level().isClientSide && com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            return false;
        }
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
        if (this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            double deltaDist = Math.sqrt(distSq);

            // ズレが0.3m(30cm)を超えた場合、または20tick(1秒)毎に同期ズレ量を詳細ロギング
            if (deltaDist > 0.30 || this.tickCount % 20 == 0) {
                MicroDroneMod.LOGGER.info(
                        "[SYNC_DELTA] Tick: {}, DeltaDist: {}m, ClientPos: [{}, {}, {}], ServerPos: [{}, {}, {}], ClientYaw: {}, ServerYaw: {}, Grounded: {}, Drift: {}",
                        this.tickCount, String.format("%.3f", deltaDist),
                        String.format("%.3f", this.getX()), String.format("%.3f", this.getY()), String.format("%.3f", this.getZ()),
                        String.format("%.3f", x), String.format("%.3f", y), String.format("%.3f", z),
                        String.format("%.2f", this.getYRot()), String.format("%.2f", yRot),
                        (this.onGround() || this.wheelGrounded), this.driftDirection);
            }

            // 3.5m (distSq > 12.25) 超過の重大な位置ズレ発生時 (壁抜け・スタック等の真の事故)、かつ非点滅時にレスキュー復帰発動
            // ※撮影モード(Cinematic)では複数台同時操縦・並走撮影での誤爆を防ぐため完全無効化！
            if (!com.microdrone.mod.cinematic.CinematicRecordingManager.isEnabled()) {
                if (distSq > 12.25 && this.resyncBlinkTicks <= 0) {
                    triggerRescueResync(x, y, z, yRot, deltaDist, "EXCESSIVE_DISTANCE_DESYNC (>3.5m)");
                }
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
    public void triggerRescueResync(double serverX, double serverY, double serverZ, float serverYRot,
            double deltaDist, String reason) {
        MicroDroneMod.LOGGER.warn(
                "[RESCUE_RESYNC] Reason: {}, Vehicle: {}, ClientPos: [{}, {}, {}] -> ServerPos: [{}, {}, {}], Delta: {}m, Grounded: {}, Chunk: [{}, {}]",
                reason, this.getUUID(),
                String.format("%.3f", this.getX()), String.format("%.3f", this.getY()),
                String.format("%.3f", this.getZ()),
                String.format("%.3f", serverX), String.format("%.3f", serverY), String.format("%.3f", serverZ),
                String.format("%.3f", deltaDist), (this.onGround() || this.wheelGrounded),
                this.chunkPosition().x, this.chunkPosition().z);

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
                mc.player.displayClientMessage(Component.translatable("message.microdrone.car_resync"), true);
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
        builder.define(DATA_VARIANT_ID, "default");
        builder.define(DATA_IN_LOOP, false);
        builder.define(DATA_LOOP_PROGRESS, 0.0f);
        builder.define(DATA_CHASSIS_BOUNCE, 0.0f);
        builder.define(DATA_HEADLIGHT, false);
        builder.define(DATA_BRAKING, false);
        builder.define(DATA_REAL_MODE, false);
        builder.define(DATA_COUNTER_MAX_ANGLE, 44.0f);
        builder.define(DATA_DRIFT_START_ANGLE, 22.0f);
        builder.define(DATA_BODY_COLOR, -1);
        builder.define(DATA_DECALS, new CompoundTag());
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

        // 本物のラジコン感覚: 操縦者プレイヤーの身代わりクローン（RcPilotDummyEntity）の配置（撮影MODで無効化可能）
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

        RcVehicleManager.registerPiloting(player.getUUID(), this);

        PacketDistributor.sendToPlayer(player,
                new DronePilotStatePayload(this.getId(), true, this.startX, this.startY, this.startZ));
        player.sendSystemMessage(Component.translatable("message.microdrone.pilot_start"));

        this.lastChunkPos = new ChunkPos(this.blockPosition());
        com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(player, this);

        return true;
    }

    @Override
    public void stopPiloting(ServerPlayer player) {
        clearHeadlight();
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
                if (dummy != null)
                    dummy.discard();
            }

            player.setCamera(player);
            player.setInvisible(false);

            boolean hasValidStartPos = !Double.isNaN(this.startX) &&
                    !(Math.abs(this.startX) < 0.001 && Math.abs(this.startY) < 0.001 && Math.abs(this.startZ) < 0.001);
            if (hasValidStartPos) {
                player.teleportTo(this.startX, this.startY, this.startZ);
            } else {
                MicroDroneMod.LOGGER.warn("[SERVER] [RC_CAR] Invalid or origin start position ({}, {}, {}) on stopPiloting. Retaining player position at {}",
                        this.startX, this.startY, this.startZ, player.position());
            }

            if (targetDummyUUID != null) {
                Entity dummyRetry = serverLevel.getEntity(targetDummyUUID);
                if (dummyRetry != null)
                    dummyRetry.discard();
            }

            double sendX = hasValidStartPos ? this.startX : player.getX();
            double sendY = hasValidStartPos ? this.startY : player.getY();
            double sendZ = hasValidStartPos ? this.startZ : player.getZ();

            PacketDistributor.sendToPlayer(player,
                    new DronePilotStatePayload(this.getId(), false, sendX, sendY, sendZ));
            player.sendSystemMessage(Component.translatable("message.microdrone.pilot_stop"));

            com.microdrone.mod.chunk.DroneChunkManager.releaseDroneChunks(player, this);

            RcVehicleManager.unregisterPiloting(player.getUUID());
            this.pilotUUID = null;
            this.startX = Double.NaN;
            this.startY = Double.NaN;
            this.startZ = Double.NaN;
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
        if (this.jumpPadLaunchTicks > 0) {
            this.jumpPadLaunchTicks--;
        }
        if (this.resyncBlinkTicks > 0) {
            this.resyncBlinkTicks--;
        }

        // リトラクタブルヘッドライトのスムーズ開閉アニメーション補間
        this.prevHeadlightProgress = this.headlightProgress;
        if (isHeadlightOn()) {
            this.headlightProgress = Math.min(1.0f, this.headlightProgress + 0.20f);
        } else {
            this.headlightProgress = Math.max(0.0f, this.headlightProgress - 0.20f);
        }

        if (!this.level().isClientSide) {
            updateHeadlightLighting();
            if (this.clientSyncTimeout > 0) {
                this.clientSyncTimeout--;
                if (this.clientSyncTimeout == 0) {
                    this.clientSyncActive = false;
                }
            }

            ServerPlayer pilot = this.pilotUUID != null ? (ServerPlayer) this.level().getPlayerByUUID(this.pilotUUID)
                    : null;

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
                    MicroDroneMod.LOGGER.warn(
                            "[SERVER] [BATTERY_EMPTY_FAILSAFE] Car {} ran out of battery. Stopping piloting.",
                            this.getUUID());
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
                MicroDroneMod.LOGGER.warn("[VEHICLE_TICK_TIME] SERVER car physics took {} us (>5ms) for {}", elapsedUs,
                        this.getUUID());
            }

            if (this.tickCount % 20 == 0 && this.isPiloted()) {
                MicroDroneMod.LOGGER.info(
                        "[SYNC_TRACK_SERVER] Tick: {}, Pos: [{}, {}, {}], Vel: [{}, {}, {}], Speed: {}, Yaw: {}, Pitch: {}, Grounded: {}, Chunk: [{}, {}]",
                        this.tickCount,
                        String.format("%.3f", this.getX()), String.format("%.3f", this.getY()), String.format("%.3f", this.getZ()),
                        String.format("%.3f", this.carVelocity.x), String.format("%.3f", this.getDeltaMovement().y), String.format("%.3f", this.carVelocity.z),
                        String.format("%.3f", this.currentSpeed),
                        String.format("%.2f", this.getYRot()), String.format("%.2f", this.pitchAngle),
                        (this.onGround() || this.wheelGrounded),
                        this.chunkPosition().x, this.chunkPosition().z);
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
            this.entityData.set(DATA_IN_LOOP, this.inLoop);
            this.entityData.set(DATA_LOOP_PROGRESS, this.loopProgress);
            this.entityData.set(DATA_CHASSIS_BOUNCE, this.chassisBounce);
            this.entityData.set(DATA_BRAKING, isBrakingInput());

            // マルチプレイ同期: Snapshot Interpolation用パケットを周囲のプレイヤーへ配信（第1段階）
            boolean isMoving = this.isPiloted() || (this.carVelocity != null && this.carVelocity.lengthSqr() > 0.0001) || Math.abs(this.currentSpeed) > 0.01f;
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
                        (float) (this.carVelocity != null ? this.carVelocity.x : 0.0),
                        (float) (this.carVelocity != null ? this.carVelocity.y : 0.0),
                        (float) (this.carVelocity != null ? this.carVelocity.z : 0.0)
                ));
            }
        } else {
            // クライアント側処理
            if (this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
                // ローカル操縦中はクライアント側で即座に物理演算を行い、遅延ゼロ＆ダイレクトな操作感を実現
                long startNanos = System.nanoTime();
                tickCarPhysics();

                long elapsedUs = (System.nanoTime() - startNanos) / 1000;
                if (elapsedUs > 5000) {
                    MicroDroneMod.LOGGER.warn("[VEHICLE_TICK_TIME] CLIENT car physics took {} us (>5ms) for {}",
                            elapsedUs, this.getUUID());
                }

                if (this.tickCount % 20 == 0) {
                    MicroDroneMod.LOGGER.info(
                            "[SYNC_TRACK_CLIENT] Tick: {}, Pos: [{}, {}, {}], Vel: [{}, {}, {}], Speed: {}, Yaw: {}, Pitch: {}, Grounded: {}, Chunk: [{}, {}], DriftDir: {}",
                            this.tickCount,
                            String.format("%.3f", this.getX()), String.format("%.3f", this.getY()), String.format("%.3f", this.getZ()),
                            String.format("%.3f", this.carVelocity.x), String.format("%.3f", this.getDeltaMovement().y), String.format("%.3f", this.carVelocity.z),
                            String.format("%.3f", this.currentSpeed),
                            String.format("%.2f", this.getYRot()), String.format("%.2f", this.pitchAngle),
                            (this.onGround() || this.wheelGrounded),
                            this.chunkPosition().x, this.chunkPosition().z,
                            this.driftDirection);
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
                this.inLoop = this.entityData.get(DATA_IN_LOOP);
                this.loopProgress = this.entityData.get(DATA_LOOP_PROGRESS);
                this.chassisBounce = this.entityData.get(DATA_CHASSIS_BOUNCE);
            }
        }
    }

    private void tickCarPhysics() {
        DroneMotorItem motor = getActiveMotorItem();
        com.microdrone.mod.config.car.CarConfig carConfig = com.microdrone.mod.config.car.CarConfigManager
                .getConfig(getVariantId());
        com.microdrone.mod.config.car.CarConfig.CarProperties props = carConfig.properties;

        // 車用に速度を 1/4 に調整 (blocks/tick) + 外部コンフィグ倍率適用
        double maxSpeed = motor.getMaxHorizontalSpeed() * 0.55 * props.maxSpeedMultiplier;
        double accel = (0.025 + (motor.getTier() * 0.008)) * props.accelMultiplier; // 速度に合わせた滑らかで扱いやすい加速

        if (this.isRealMode()) {
            // === REALモード専用物理演算 (RACEモードから完全分離) ===
            com.microdrone.mod.item.RcTireItem.TireType tireType = getTireType();
            float blockFriction = 1.0f;
            boolean onIce = false;
            boolean onOffroad = false;
            if (this.onGround() || this.wheelGrounded) {
                var roadHit = com.microdrone.mod.block.road.RoadSplineManager.sampleRoad(
                        this.level(), this.getX(), this.getY(), this.getZ());
                boolean onSplineRoad = roadHit.hit;

                net.minecraft.world.level.block.state.BlockState groundState = this.level().getBlockState(this.blockPosition().below());
                net.minecraft.world.level.block.state.BlockState atState = this.level().getBlockState(this.blockPosition());
                float bFriction = groundState.getBlock().getFriction();
                if (onSplineRoad) {
                    onIce = (roadHit.surfaceType == com.microdrone.mod.block.road.RoadSplineData.RoadSurfaceType.ICE);
                    onOffroad = (roadHit.surfaceType == com.microdrone.mod.block.road.RoadSplineData.RoadSurfaceType.DIRT);
                    if (roadHit.surfaceType == com.microdrone.mod.block.road.RoadSplineData.RoadSurfaceType.PUDDLE) {
                        maxSpeed *= 0.60;
                        accel *= 0.40;
                        if (Math.abs(this.currentSpeed) > 0.05f && this.random.nextInt(2) == 0) {
                            this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                    this.getX() + (this.random.nextDouble() - 0.5) * 0.4,
                                    this.getY() + 0.05,
                                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.4,
                                    (this.random.nextDouble() - 0.5) * 0.2, 0.15, (this.random.nextDouble() - 0.5) * 0.2);
                        }
                    }
                } else {
                    onIce = ((bFriction > 0.85f) || groundState.is(net.minecraft.tags.BlockTags.ICE) || atState.is(net.minecraft.tags.BlockTags.ICE));
                    onOffroad = (groundState.is(net.minecraft.tags.BlockTags.DIRT)
                            || groundState.is(net.minecraft.tags.BlockTags.SAND)
                            || groundState.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                            || groundState.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                            || groundState.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)
                            || groundState.is(net.minecraft.world.level.block.Blocks.MUD)
                            || atState.is(net.minecraft.tags.BlockTags.DIRT)
                            || atState.is(net.minecraft.tags.BlockTags.SAND)
                            || atState.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                            || atState.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK));
                }
                blockFriction = bFriction;
            }

            float dynamicGrip = tireType.getLateralGripMultiplier();
            if (tireType == com.microdrone.mod.item.RcTireItem.TireType.SPIKE) {
                dynamicGrip = onIce ? 2.24f : 0.70f;
            } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                // 舗装路では良好(1.68f)でグリップタイヤ(3.08f)より下、オフロードでは最強(2.59f)でグリップタイヤを圧倒！
                dynamicGrip = onOffroad ? 2.59f : 1.68f;
            } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.GRIP) {
                // 舗装路では最強(3.08f)、オフロードでは泥や砂で目詰まりして激減(0.77f)
                dynamicGrip = onOffroad ? 0.77f : 3.08f;
            }

            this.realPhysics.setTireProperties(
                    dynamicGrip,
                    tireType.getScrubDragMultiplier(),
                    tireType.isIceImmunity(),
                    tireType.isOffroadImmunity()
            );
            this.realPhysics.setSurfaceFriction(blockFriction);

            double realMaxSpeed = maxSpeed * this.realPhysics.getConfig().realSpeedScale;
            double realAccel = accel * this.realPhysics.getConfig().realAccelScale;
            this.realPhysics.setCounterMaxAngleDeg(this.getCounterMaxAngle());
            this.realPhysics.setDriftStartAngleDeg(this.getDriftStartAngle());
            // 4点サスペンション・地面接地・ピッチ/ロール計算を先に実行
            this.prevRollAngle = this.rollAngle;
            this.prevPitchAngle = this.pitchAngle;
            if (!this.onLoop && !this.inLoop) {
                updateSuspensionPhysics(0.0f);

                boolean wallAheadReal = !this.onSlope
                        && (this.horizontalCollision || (this.inputForward > 0.05f && isObstacleAhead(0.12)));
                boolean groundedNow = (this.jumpAirborneTicks <= 0) && (this.onGround() || this.wheelGrounded);

                RealCarPhysics.StepResult res = this.realPhysics.stepHorizontal(
                        this.carVelocity,
                        this.getYRot(),
                        this.inputForward,
                        this.inputSteer,
                        this.inputHandbrake,
                        groundedNow,
                        wallAheadReal,
                        realMaxSpeed,
                        realAccel,
                        props.turnSpeedMultiplier,
                        props.invertSteer
                );

                this.setYRot(res.newYaw());
                this.steerAngle = res.newSteerAngle();
                this.carVelocity = res.worldVelocity();
                this.currentSpeed = res.forwardSpeed();
            }
            if (this.level().isClientSide && this.locallyControlled) {
                this.realPhysics.recordTelemetryTick(
                        this.getId(),
                        this.getX(),
                        this.getZ(),
                        this.inputForward,
                        this.inputSteer,
                        this.inputHandbrake
                );
            }
            this.driftDirection = 0;
            this.driftTicks = 0;
            this.turboBoostTicks = 0;
        } else {
            // === RACEモード (既存の挙動を完全維持) ===
            com.microdrone.mod.item.RcTireItem.TireType tireType = getTireType();
            double friction = 0.90; // 地面摩擦減速
            boolean onIce = false;
            boolean onOffroad = false;
            boolean onPuddle = false;

            if (this.onGround() || this.wheelGrounded) {
                var roadHit = com.microdrone.mod.block.road.RoadSplineManager.sampleRoad(
                        this.level(), this.getX(), this.getY(), this.getZ());
                boolean onSplineRoad = roadHit.hit;

                net.minecraft.world.level.block.state.BlockState groundState = this.level().getBlockState(this.blockPosition().below());
                net.minecraft.world.level.block.state.BlockState atState = this.level().getBlockState(this.blockPosition());
                float bFriction = groundState.getBlock().getFriction();
                if (onSplineRoad) {
                    onIce = (roadHit.surfaceType == com.microdrone.mod.block.road.RoadSplineData.RoadSurfaceType.ICE);
                    onOffroad = (roadHit.surfaceType == com.microdrone.mod.block.road.RoadSplineData.RoadSurfaceType.DIRT);
                    if (roadHit.surfaceType == com.microdrone.mod.block.road.RoadSplineData.RoadSurfaceType.PUDDLE) {
                        onPuddle = true;
                    }
                } else {
                    onIce = ((bFriction > 0.85f) || groundState.is(net.minecraft.tags.BlockTags.ICE) || atState.is(net.minecraft.tags.BlockTags.ICE));
                    onOffroad = (groundState.is(net.minecraft.tags.BlockTags.DIRT)
                            || groundState.is(net.minecraft.tags.BlockTags.SAND)
                            || groundState.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                            || groundState.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                            || groundState.is(net.minecraft.world.level.block.Blocks.SOUL_SAND)
                            || groundState.is(net.minecraft.world.level.block.Blocks.MUD)
                            || atState.is(net.minecraft.tags.BlockTags.DIRT)
                            || atState.is(net.minecraft.tags.BlockTags.SAND)
                            || atState.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                            || atState.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK));
                }

                if (onPuddle) {
                    // 水たまり: 水の抵抗で減速＆水しぶき
                    maxSpeed *= 0.60;
                    accel *= 0.40;
                    friction = 0.92;
                    if (Math.abs(this.currentSpeed) > 0.05f && this.random.nextInt(2) == 0) {
                        this.level().addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                                this.getX() + (this.random.nextDouble() - 0.5) * 0.4,
                                this.getY() + 0.05,
                                this.getZ() + (this.random.nextDouble() - 0.5) * 0.4,
                                (this.random.nextDouble() - 0.5) * 0.2, 0.15, (this.random.nextDouble() - 0.5) * 0.2);
                    }
                } else if (onIce) {
                    if (tireType.isIceImmunity()) {
                        // スパイクタイヤ: 氷上でもアスファルト同等にガッチリ食いつく！
                        friction = 0.90;
                    } else {
                        // 通常タイヤ: 氷上ではツルツル滑って止まらない＆曲がらない！
                        friction = 0.985;
                    }
                } else if (!onOffroad) {
                    // 通常ブロック・普通の舗装道路！
                    if (tireType == com.microdrone.mod.item.RcTireItem.TireType.SPIKE) {
                        // スパイクタイヤ: 金属ピンが舗装路で滑り、アクセルオフでもスーッと滑って減速しにくい
                        friction = 0.96;
                    } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                        // オフロードタイヤ: 舗装路でもしっかり減速・グリップ
                        friction = 0.90;
                    }
                }
            }

            double offroadMaxMult = 1.0;
            double offroadAccelMult = 1.0;
            if (onOffroad) {
                if (tireType.isOffroadImmunity()) {
                    // ラリータイヤ: ダート・草地でも減速なし＆トラクション向上！
                    offroadMaxMult = 1.0;
                    offroadAccelMult = 1.35;
                } else {
                    // 通常タイヤ（ノーマル/ドリフト/グリップ）: 不整地に足を取られて失速！
                    offroadMaxMult = 0.65;
                    offroadAccelMult = 0.50;
                }
            }

            // ステアリング操舵 (最大35度)
            float steerMult = (props.invertSteer ? -1.0f : 1.0f) * props.turnSpeedMultiplier;
            float targetSteer = this.inputSteer * 35.0f * steerMult;
            this.steerAngle = Mth.lerp(0.40f, this.steerAngle, targetSteer);

            // ③ 接触時の出力抑制 (壁・障害物に接触/密着している状態での前進アクセルを大幅カットし、ゴリ押し抜けを完全防止)
            // ※ スロープ斜面走行中や縦ループ走行中・突入時は段差との誤接触を防ぐため除外
            boolean wallAhead = !this.onSlope && !this.onLoop && !this.inLoop
                    && (this.horizontalCollision || (this.inputForward > 0.05f && isObstacleAhead(0.12)));
            if (wallAhead && this.inputForward > 0.05f) {
                this.currentSpeed = Math.min(this.currentSpeed, 0.015f); // ほぼ停止速度に制限
                accel *= 0.08; // 加速を90%以上カット
            }

            // 前進 / 後退 / ESCブレーキ処理 (実機ホビーラジコンESC仕様: 前進中のS入力は急ブレーキ、停止後にバック)
            // Forward / Reverse / Brake processing with Mario Kart drift speed maintenance
            boolean throttleKicked = (this.inputForward > 0.10f && this.prevInputForward <= 0.05f);
            if (this.inputForward > 0.05f) {
                if (this.currentSpeed < -0.01f) {
                    this.currentSpeed += (float) (accel * 2.5f);
                    if (this.currentSpeed > 0.0f)
                        this.currentSpeed = 0.0f;
                } else {
                    float tractionMult = tireType.getTractionMultiplier();
                    if (!onIce && !onOffroad) {
                        if (tireType == com.microdrone.mod.item.RcTireItem.TireType.SPIKE) {
                            tractionMult = 1.19f; // 舗装路では金属ピンが空転して加速が逃げる (0.85*1.4)
                        } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                            tractionMult = 1.47f; // 舗装路でも良好なトラクション (1.05*1.4)
                        }
                    } else if (onOffroad && tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                        tractionMult = 1.96f; // オフロードでは最強トラクション！ (1.40*1.4)
                    }
                    float forwardAccel = (float) (accel * this.inputForward * tractionMult * offroadAccelMult);
                    if (this.driftDirection != 0) {
                        forwardAccel *= 1.30f; // Maintain high speed during drift (long slide)

                        // ★ 実車・RC流アクセル煽り（チョッピング）: ドリフト中のアクセル踏み直しで前に蹴り出し＆垂れをリフレッシュ
                        if (throttleKicked && this.currentSpeed > 0.04f) {
                            this.currentSpeed += (float) (accel * 1.4f); // 一瞬グッと前に蹴り出すトラクション
                            if (this.driftTicks > 14) {
                                this.driftTicks = Math.max(8, this.driftTicks - 16); // 垂れカウントを巻き戻し、ドリフトが息を吹き返す
                            }
                        }
                    }
                    this.currentSpeed += forwardAccel;
                }
            } else if (this.inputForward < -0.05f) {
                if (this.currentSpeed > 0.01f) {
                    this.currentSpeed -= (float) (accel * 2.5f);
                    if (this.currentSpeed < 0.0f)
                        this.currentSpeed = 0.0f;
                } else {
                    this.currentSpeed += (float) (accel * this.inputForward * 0.65f);
                }
            } else {
                this.currentSpeed *= friction;
                if (Math.abs(this.currentSpeed) < 0.001f) {
                    this.currentSpeed = 0.0f;
                }
            }

            double currentMax = ((this.turboBoostTicks > 0) ? (maxSpeed * 1.30) : maxSpeed) * offroadMaxMult;
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
                        this.driftDirection = 1; // D key: Drift Right
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

                    // ドリフト継続時間・距離に応じたスクラブ抵抗による緩やかな減速（減速緩和微調整版）
                    // 序盤(~20tick=約1.0秒)は最高速ドリフトを維持し、それ以降も減速を抑えてスピードをキープ
                    if (this.driftTicks > 20) {
                        float scrubFactor = Math.min(0.0075f * tireType.getScrubDragMultiplier(), (this.driftTicks - 20) * 0.00020f);
                        this.currentSpeed = Math.max(0.04f, this.currentSpeed * (1.0f - scrubFactor));
                    }
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
                // Left Drift: D key (<0) is Counter-Steer to Right! A key (>0) is In-Steer to
                // Left
                if (this.inputSteer < -0.05f) {
                    counterAmount = -this.inputSteer; // D key = Counter-steer
                } else if (this.inputSteer > 0.05f) {
                    inSteerAmount = this.inputSteer; // A key = In-steer
                }
            } else if (this.driftDirection == 1) {
                // Right Drift: A key (>0) is Counter-Steer to Left! D key (<0) is In-Steer to
                // Right
                if (this.inputSteer > 0.05f) {
                    counterAmount = this.inputSteer; // A key = Counter-steer
                } else if (this.inputSteer < -0.05f) {
                    inSteerAmount = -this.inputSteer; // D key = In-steer
                }
            }

            // 4点サスペンション物理とピッチ・ロール姿勢角の計算 (サスペンションシステム)
            // ※ 360°縦ループ走行中はループ専用のピッチ・ロール・吸着物理が担当するため完全スキップ！
            this.prevRollAngle = this.rollAngle;
            this.prevPitchAngle = this.pitchAngle;
            if (!this.onLoop && !this.inLoop) {
                updateSuspensionPhysics(counterAmount);
            }

            // === Steering / Yaw Turn ===
            if (this.driftDirection != 0 && Math.abs(this.currentSpeed) > 0.002f) {
                float baseTurn = 4.2f;
                if (tireType == com.microdrone.mod.item.RcTireItem.TireType.DRIFT) {
                    baseTurn = 5.6f;
                } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.GRIP) {
                    baseTurn = 2.8f;
                } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.SPIKE) {
                    baseTurn = onIce ? 3.4f : 5.0f; // 舗装路では金属ピンが滑ってスピンしやすい
                } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                    baseTurn = onOffroad ? 3.6f : 4.4f; // 舗装路ではスパイク(5.0)より安定(4.4)
                }
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
                    if (tireType == com.microdrone.mod.item.RcTireItem.TireType.GRIP) {
                        baseTurn *= (onOffroad ? 0.98f : 2.03f); // 0.70*1.4, 1.45*1.4
                    } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.DRIFT) {
                        baseTurn *= 1.61f; // 1.15*1.4
                    } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.SPIKE) {
                        if (!onIce) {
                            baseTurn *= 1.15f; // 0.82*1.4
                        }
                    } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                        if (onOffroad) {
                            baseTurn *= 1.89f; // 1.35*1.4
                        } else {
                            baseTurn *= 1.54f; // 1.10*1.4
                        }
                    }
                    if (onIce && !tireType.isIceImmunity()) {
                        baseTurn *= 0.35f;
                    }
                    float turnSpeed = (this.steerAngle / 35.0f) * baseTurn;
                    this.setYRot(this.getYRot() - turnSpeed);
                }
            }

            // === Inertial drift movement vector synthesis (Power slide along
            // counter-steer) ===
            float yawRad = this.getYRot() * Mth.DEG_TO_RAD;
            double driveX = -Mth.sin(yawRad);
            double driveZ = Mth.cos(yawRad);

            // Right vector: rightX = -driveZ = -cos, rightZ = driveX = -sin
            double rightX = -driveZ;
            double rightZ = driveX;

            if (this.driftDirection != 0) {
                // Slide direction vector in the counter-steer direction:
                // Left Drift (driftDirection == -1): Counter is Right -> slide direction is
                // Right Vector (rightX, rightZ)
                // Right Drift (driftDirection == 1): Counter is Left -> slide direction is Left
                // Vector (-rightX, -rightZ)
                double counterSlideX = -rightX * this.driftDirection;
                double counterSlideZ = -rightZ * this.driftDirection;

                // When counter-steering, the car strongly slides in the counter-steered
                // direction!
                // Neutral: drive 0.60, slide 0.55
                // Full Counter: drive 0.25, slide 1.05 -> Power slide laterally in
                // counter-steer direction!
                // Full In-steer: drive 0.90, slide 0.20 -> Drives forward into turn
                double driveWeight = 0.60 - (counterAmount * 0.35) + (inSteerAmount * 0.30);
                double slideWeight = 0.55 + (counterAmount * 0.50) - (inSteerAmount * 0.35);

                if (tireType == com.microdrone.mod.item.RcTireItem.TireType.DRIFT) {
                    slideWeight *= 1.70;
                    driveWeight *= 0.55;
                } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.GRIP) {
                    if (onOffroad) {
                        slideWeight *= 1.20; // 悪路ではスリックが目詰まり・空転して滑る
                        driveWeight *= 0.80;
                    } else {
                        slideWeight *= 0.12; // 舗装路では最強グリップ！
                        driveWeight *= 1.75;
                    }
                } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.SPIKE) {
                    if (!onIce) {
                        // 舗装路では金属ピンが滑ってオフロードより激しくスライド！
                        slideWeight *= 1.50;
                        driveWeight *= 0.65;
                    } else {
                        // 氷上ではガッチリ噛む
                        slideWeight *= 0.55;
                        driveWeight *= 1.40;
                    }
                } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                    if (onOffroad) {
                        // オフロードでは最強！ドリフトを抑えて強烈なトラクションで前進！
                        slideWeight *= 0.15;
                        driveWeight *= 1.85;
                    } else {
                        // 舗装路でもしっかり踏ん張る(0.60)がグリップ(0.12)には及ばない
                        slideWeight *= 0.60;
                        driveWeight *= 1.35;
                    }
                }

                // ドリフト距離・時間に応じたグリップ力回復 (Grip Recovery - マイルド調整版)
                // ドリフトが伸びると横滑り(slideWeight)が適度に収まり、前進トラクション(driveWeight)へ移行
                if (this.driftTicks > 24) {
                    float gripRecovery = Mth.clamp((this.driftTicks - 24) / 45.0f, 0.0f, 1.0f);
                    driveWeight = Mth.lerp(gripRecovery, driveWeight, 0.85);
                    slideWeight = Mth.lerp(gripRecovery, slideWeight, 0.18);

                    // 長時間流しきった(60tick超=約3.0秒以上)またはステア中立で完全グリップ復帰＆立ち上がり
                    if (this.driftTicks > 60 || (this.driftTicks > 36 && counterAmount < 0.10f && inSteerAmount < 0.10f)) {
                        if (this.driftTicks >= 14 && this.currentSpeed > 0.04f) {
                            this.turboBoostTicks = 10;
                            this.currentSpeed = (float) Math.min(this.currentSpeed * 1.30f, maxSpeed * 1.25f);
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
                        Mth.lerp(0.22, this.carVelocity.z, combinedZ));
            } else {
                if (Math.abs(this.currentSpeed) < 0.001f) {
                    this.carVelocity = Vec3.ZERO;
                } else {
                    double gripFollowRate = 0.77; // NORMALの基準値 (0.55 * 1.4)
                    if (onIce) {
                        if (tireType.isIceImmunity()) {
                            gripFollowRate = 0.98; // 0.70 * 1.4
                        } else {
                            gripFollowRate = 0.11; // 0.08 * 1.4
                        }
                    } else if (onOffroad) {
                        if (tireType.isOffroadImmunity()) {
                            // ★ オフロード路面: オフロードタイヤが最強グリップ！ (0.92 * 1.4 -> 1.0)
                            gripFollowRate = 1.0;
                        } else {
                            // グリップタイヤや他タイヤは悪路で目詰まり・足を取られてズルズル
                            gripFollowRate = 0.35; // 0.25 * 1.4
                        }
                    } else {
                        // ★ 通常ブロック・舗装道路・メッシュ道路！
                        if (tireType == com.microdrone.mod.item.RcTireItem.TireType.GRIP) {
                            gripFollowRate = 1.25; // ★ ご指定: グリップタイヤは 1.25！
                        } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.OFFROAD) {
                            gripFollowRate = 0.72; // ★ ご指定: ノーマルタイヤ(0.77)よりもちょびっとだけ小さく！
                        } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.DRIFT) {
                            gripFollowRate = 0.39; // 0.28 * 1.4
                        } else if (tireType == com.microdrone.mod.item.RcTireItem.TireType.SPIKE) {
                            gripFollowRate = 0.25; // 0.18 * 1.4
                        }
                    }

                    double newVx = Mth.lerp(gripFollowRate, this.carVelocity.x, driveX * this.currentSpeed);
                    double newVz = Mth.lerp(gripFollowRate, this.carVelocity.z, driveZ * this.currentSpeed);
                    double currentMag = Math.sqrt(newVx * newVx + newVz * newVz);
                    double targetSpeed = Math.abs(this.currentSpeed);
                    if (currentMag > 0.001 && currentMag > targetSpeed) {
                        newVx = (newVx / currentMag) * targetSpeed;
                        newVz = (newVz / currentMag) * targetSpeed;
                    }
                    this.carVelocity = new Vec3(newVx, 0, newVz);
                }
            }
            this.prevInputForward = this.inputForward;
        }

        float commonYawRad = this.getYRot() * Mth.DEG_TO_RAD;
        double driveX = -Mth.sin(commonYawRad);
        double driveZ = Mth.cos(commonYawRad);

        // === クライアント主導位置同期（ステップ2: サーバー側処理） ===
        // 操縦者クライアントが存在し、位置同期パケットを受信中の場合:
        // 車両の座標・姿勢はクライアントで確定・スナップ済みのため、
        // サーバー側での重複move(二重前進)や重複壁衝突(誤スタック)をスキップ
        // ※撮影モード(Cinematic)ではReplayModに移動パケットを届けるためスキップせずサーバー物理を完全実行
        if (!this.level().isClientSide && this.clientSyncActive && !com.microdrone.mod.cinematic.CinematicRecordingManager.isEnabled()) {
            // タイヤ回転の積算
            this.wheelRotation += (this.currentSpeed * 144.0f);

            // バッテリー消費のみ確実に実行
            if (!this.installedBattery.isEmpty()) {
                double moveDist = Math.abs(this.currentSpeed);
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
            return;
        }

        double motionX = this.carVelocity.x;
        double motionZ = this.carVelocity.z;
        double motionY;

        // === 垂直打ち上げジャンプパッド (JumpPadBlock) の踏み込み検知 ===
        if (this.jumpCooldown <= 0) {
            double rad = this.getYRot() * Mth.DEG_TO_RAD;
            double fwdX = -Mth.sin((float) rad);
            double fwdZ = Mth.cos((float) rad);

            // 車体中心、先端バンパー、後輪のいずれかでジャンプパッドを先読み検知 (段差激突ゼロ！)
            net.minecraft.world.level.block.state.BlockState padState = getPadStateAt(this.getX(), this.getY(), this.getZ());
            if (padState == null) {
                padState = getPadStateAt(this.getX() + fwdX * 0.35, this.getY(), this.getZ() + fwdZ * 0.35);
            }
            if (padState == null) {
                padState = getPadStateAt(this.getX() - fwdX * 0.25, this.getY(), this.getZ() - fwdZ * 0.25);
            }

            if (padState != null) {
                int power = padState.hasProperty(com.microdrone.mod.block.JumpPadBlock.POWER)
                        ? padState.getValue(com.microdrone.mod.block.JumpPadBlock.POWER)
                        : 2;

                this.jumpCooldown = 25;
                this.jumpPadPower = power;
                this.jumpPadExpelled = false;

                // 上昇タイマー: LOW=8, MID=13, HIGH=18 ticks
                this.jumpPadLaunchTicks = power == 1 ? 8 : (power == 2 ? 13 : 18);
                // 滞空保護時間: 上昇＋頂点射出後の放物線着地までカバー
                this.jumpAirborneTicks = this.jumpPadLaunchTicks + 22;

                // 上昇初速: スプリングのように垂直へ力強く一気に持ち上げる！
                float mass = this.getVehicleMass();
                double massBounce = 1.0 + Math.max(0.0, (mass - 1.10f) * 0.08);
                double baseJumpY = power == 1 ? 0.54 : (power == 2 ? 0.86 : 1.28);
                double jumpY = baseJumpY * massBounce;

                // 上昇中の水平速度: 停止時は真上、走行中なら前進速度を少しマイルドに保って垂直リフト感を強調
                double currentFwd = Math.max(0.0, this.currentSpeed);
                double fwdInit = Math.min(currentFwd * 0.35, 0.12);
                this.carVelocity = new Vec3(fwdX * fwdInit, jumpY, fwdZ * fwdInit);
                motionX = this.carVelocity.x;
                motionZ = this.carVelocity.z;
                this.setDeltaMovement(this.carVelocity);
                this.setOnGround(false);
                this.wheelGrounded = false;
                this.onSlope = false;
                this.hasImpulse = true;
                this.setPos(this.getX(), this.getY() + 0.08, this.getZ());
                this.pitchAngle = -2.0f; // 水平〜わずかにノーズアップで美しくリフト

                // サウンド演出 (ピストン射出音 + スライムバウンス)
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.PISTON_EXTEND,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 1.0F + power * 0.3F);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.SLIME_BLOCK_FALL,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);

                // 発射パーティクル
                if (!this.level().isClientSide && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            this.getX(), this.getY() + 0.1, this.getZ(), 8, 0.2, 0.05, 0.2, 0.05);
                }
            }
        }

        // === 360°縦ループ（完全自由物理方式: スロープと同等の連続傾斜登坂・遠心力吸着） ===
        LoopSurfaceHit loopHit = sampleLoopSurface(this.position());
        com.microdrone.mod.block.road.RoadSplineGenerator.RoadHitResult splineLoopHit = null;
        if (!loopHit.onSurface) {
            var roadHit = com.microdrone.mod.block.road.RoadSplineManager.sampleRoad(this.level(), this.getX(), this.getY(), this.getZ());
            if (roadHit.hit && roadHit.isLoop360) {
                splineLoopHit = roadHit;
            }
        }

        this.onLoop = loopHit.onSurface || splineLoopHit != null;
        if (this.onLoop) {
            boolean isSplineLoop = (splineLoopHit != null);
            float activeAngle = loopHit.onSurface ? loopHit.angle : (float) splineLoopHit.loopAngleDeg;
            this.loopAngle = activeAngle;
            boolean wasInLoop = this.inLoop;
            this.inLoop = true;
            this.loopProgress = activeAngle;
            if (loopHit.onSurface) {
                this.loopBlockPos = loopHit.blockPos;
                this.loopFacing = loopHit.facing;
            } else if (!wasInLoop) {
                // スプラインループ突入時: 3D速度・水平速度のうち大きい方を引き継ぎ、設定された加速倍率を適用
                float boost = Math.max(1.0f, splineLoopHit.loopBoostMultiplier);
                double entrySpd = Math.max(this.currentSpeed, this.carVelocity.length());
                double boostedSpd = Math.max(entrySpd * boost, 0.35 * boost);
                this.currentSpeed = (float) Math.min(maxSpeed * Math.max(1.5, boost), boostedSpd);
            }

            // 遠心力加速度 a_c = V^2 / R (R = 1.9375m)
            double vSq = this.currentSpeed * this.currentSpeed;
            double centAccel = vSq / 1.9375;

            // スプラインループ中はアクセル入力がある限り失速落下しない（ブレーキ/停止時のみ落下）
            boolean allowLoopStall = !isSplineLoop || this.inputForward < -0.05f || this.currentSpeed < 0.08f;
            // 垂直〜天井区間 (50°〜310°) で遠心力が極端に不足した場合（ほぼ停止状態）のみ吸着を解除して自由落下へ
            if (allowLoopStall && activeAngle >= 50.0f && activeAngle <= 310.0f && centAccel < 0.005) {
                this.onLoop = false;
                this.inLoop = false;
                this.setOnGround(false);
                this.wheelGrounded = false;
                MicroDroneMod.LOGGER.warn("[LOOP_STALL] Vehicle lost centrifugal adhesion! Angle: {} deg, Speed: {}",
                        String.format("%.1f", activeAngle), String.format("%.3f", this.currentSpeed));
            }
        } else {
            if (this.inLoop) {
                // ループ脱出時: 現在の進行速度を水平方向へ引き継いで通常走行へスムーズに復帰
                double yawRad = Math.toRadians(this.getYRot());
                double fwdX = -Math.sin(yawRad);
                double fwdZ = Math.cos(yawRad);
                double exitSpd = Math.max(0.0, this.currentSpeed);
                this.carVelocity = new Vec3(fwdX * exitSpd, Math.max(0.0, this.carVelocity.y), fwdZ * exitSpd);
            }
            this.inLoop = false;
            // ループ脱出時のピッチ角正常化 (-360° -> 0°)
            if (this.pitchAngle < -180.0f) {
                this.pitchAngle += 360.0f;
                this.prevPitchAngle = this.pitchAngle;
            }
        }

        if (this.onLoop) {
            boolean isSplineLoop = (splineLoopHit != null);
            float activeAngle = loopHit.onSurface ? loopHit.angle : (float) splineLoopHit.loopAngleDeg;
            if (isSplineLoop) {
                // スプライン360°ループ中は設定された加速倍率で速度を維持・加速（ブレーキ時のみ減速）
                float boost = Math.max(1.0f, splineLoopHit.loopBoostMultiplier);
                double loopCap = maxSpeed * Math.max(1.15, boost);
                double loopFloor = 0.35 * Math.min(boost, 2.0);
                if (this.inputForward > 0.05f) {
                    this.currentSpeed = (float) Math.min(loopCap, Math.max(this.currentSpeed, loopFloor) + 0.004 * boost);
                } else if (this.inputForward < -0.05f) {
                    this.currentSpeed = (float) Math.max(0.0, this.currentSpeed - 0.025);
                } else {
                    // アクセルオフでもループ中は速度をほぼ維持
                    this.currentSpeed = (float) Math.max(0.28 * Math.min(boost, 1.5), this.currentSpeed);
                }
            } else {
                // 既存のループブロック用トラクション補正
                double rad = Math.toRadians(activeAngle);
                double slopeFactor = Math.sin(rad); // 0°で0, 90°で+1(登坂抵抗), 180°で0, 270°で-1(下り加速)
                if (slopeFactor > 0) {
                    double drag = slopeFactor * (this.inputForward > 0.05f ? 0.001 : 0.006);
                    this.currentSpeed = (float) Math.max(0.08, this.currentSpeed - drag);
                } else if (slopeFactor < 0) {
                    this.currentSpeed = (float) Math.min(maxSpeed * 1.25, this.currentSpeed - slopeFactor * 0.005);
                }
            }

            // ループ傾斜角 (0°〜360°) に応じた前進・垂直・横移動ベクトル (完全自由移動)
            Vec3 loopMotion = loopHit.onSurface
                    ? getLoopTangentialMotion(loopHit, this.currentSpeed, this.inputSteer)
                    : getSplineLoopTangentialMotion(splineLoopHit, this.currentSpeed, this.inputSteer);
            motionX = loopMotion.x;
            motionY = loopMotion.y;
            motionZ = loopMotion.z;
            this.carVelocity = new Vec3(motionX, motionY, motionZ);
            this.setOnGround(true);
            this.wheelGrounded = true;

            // ピッチ角追従 (-phi で360度宙返り)
            float targetPitch = -activeAngle;
            if (this.pitchAngle < -300.0f && targetPitch > -60.0f) {
                // 360°から0°への通過時のみ、逆回転を防ぐためprevPitchAngleをスナップ
                this.prevPitchAngle = targetPitch;
            }
            this.pitchAngle = targetPitch;
            this.setXRot(this.pitchAngle);

            // ロール角 (ステアリング遠心バンク)
            this.rollAngle = Mth.lerp(0.30f, this.rollAngle, -this.inputSteer * 14.0f);

            // 走行SE
            if (this.tickCount % 4 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 0.4F, 1.4F + (this.currentSpeed * 0.8F));
            }
        } else if (this.jumpPadLaunchTicks > 0) {
            // ジャンプパッド上昇シーケンス中: 軽やかな専用重力 (0.040) で滑らかに頂点まで登り切らせる
            motionY = this.getDeltaMovement().y - 0.040;
            this.setOnGround(false);

            // 登り切った瞬間（頂点到達）判定:
            // 上昇タイマー終了間近 (<= 1) または Y上昇速度が失速 (Vy <= 0.08) になった瞬間！
            if ((this.jumpPadLaunchTicks <= 1 || motionY <= 0.08) && !this.jumpPadExpelled) {
                this.jumpPadExpelled = true;

                // ★ 登り切った瞬間の「前方に吐き出す（前方射出ブースト）」！！ ★
                double rad = this.getYRot() * Mth.DEG_TO_RAD;
                double fwdX = -Mth.sin((float) rad);
                double fwdZ = Mth.cos((float) rad);

                // 前方への吐き出し速度（LOW: 軽快に前進, MID: 爽快な前方射出, HIGH: ハイパージェット吐き出し）
                double expelH = this.jumpPadPower == 1 ? 0.38 : (this.jumpPadPower == 2 ? 0.65 : 0.98);
                expelH = Math.max(expelH, Math.abs(this.currentSpeed) * 1.30);
                this.currentSpeed = (float) expelH;

                // わずかな浮力と共に前方にシュッと射出！
                double expelVy = this.jumpPadPower == 1 ? 0.10 : 0.14;
                this.carVelocity = new Vec3(fwdX * expelH, expelVy, fwdZ * expelH);
                motionX = this.carVelocity.x;
                motionZ = this.carVelocity.z;
                motionY = expelVy;
                this.setDeltaMovement(this.carVelocity);
                this.hasImpulse = true;

                // 車体姿勢: 前方に飛び出すダイナミックなノーズダウン姿勢！
                this.pitchAngle = 6.0f;

                // サウンド演出: 空中で前方に吹き飛ぶ爽快なジェット風切り音＆ロケット音！
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_SHOOT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 1.1F + this.jumpPadPower * 0.25F);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.WIND_CHARGE_BURST.value(),
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.4F);

                // パーティクル演出: 車体後方からブースト煙を噴出！
                if (!this.level().isClientSide && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    double tailX = this.getX() - fwdX * 0.25;
                    double tailZ = this.getZ() - fwdZ * 0.25;
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                            tailX, this.getY() + 0.1, tailZ, 10, 0.15, 0.08, 0.15, 0.06);
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            tailX, this.getY() + 0.1, tailZ, 8, 0.2, 0.1, 0.2, 0.08);
                    if (this.jumpPadPower == 3) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                                tailX, this.getY() + 0.1, tailZ, 12, 0.2, 0.1, 0.2, 0.1);
                    }
                }
            }
        } else if (this.jumpAirborneTicks <= 0 && this.onSlope && this.wheelGrounded && Math.abs(this.currentSpeed) > 0.005f) {
            double sinSlope = this.slopeSinTheta; // 実際の路面傾斜サイン値を使用
            double cosSlope = Math.max(0.35, Math.sqrt(Math.max(0.0, 1.0 - sinSlope * sinSlope)));
            double tanSlope = Math.min(2.0, sinSlope / cosSlope); // 急角度でも1tickで路面に潜り込まないようtanθで上昇
            double climbVy = this.currentSpeed * tanSlope * this.slopeClimbDot;
            motionY = climbVy;
            this.setOnGround(true);
        } else if (this.jumpAirborneTicks <= 0 && this.wheelGrounded && this.getDeltaMovement().y <= 0.001) {
            motionY = 0.0;
        } else {
            float mass = this.getVehicleMass();
            // ハイブリッド重力: 基礎重力を 0.046 -> 0.064 に引き締め、重さに応じた落下力を強化
            double gravity = 0.064 + Math.max(0.0, (mass - 1.10f) * 0.025);
            // 頂点を過ぎて下降中(Vy < 0)は重力を1.80倍にブースト！ふわふわ滞空せずキュッと素早く着地
            if (this.getDeltaMovement().y < 0.0) {
                gravity *= 1.80;
            }
            motionY = this.getDeltaMovement().y - gravity;
            this.setOnGround(false);
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
                // スロープ斜面走行中および縦ループ走行中は微小なメッシュ分割との誤接触で前進を遮断しない
                if (!this.onSlope && !this.onLoop) {
                    break; // 壁に接触した瞬間に残りのステップを即時遮断！
                }
            }
        }
        // サブステップ分割後のDeltaMovementが1ステップ分(1/numSteps)のまま残らないよう、1tick分の速度へ復元
        this.setDeltaMovement(new Vec3(motionX, motionY, motionZ));
        Vec3 actualMoved = this.position().subtract(preMovePos);

        // 地面に衝突した（着地した）場合は接地状態を維持 (ジャンプ飛行保護中はバイパス)
        if (this.jumpAirborneTicks <= 0 && this.verticalCollision && motionY <= 0.0) {
            this.setOnGround(true);
        }

        // 移動後に斜面メッシュの上面へ完全吸着（板の下への潜り込み・浮き上がり跳ねを100%防止、ジャンプ飛行中・縦ループ中はバイパス）
        if (this.jumpAirborneTicks <= 0 && this.onSlope && !this.onLoop) {
            WheelRaycastHit curHit = sampleWheelGround(this.getX(), this.getY(), this.getZ());
            if (curHit.onSlope) {
                if (this.getY() < curHit.groundY || Math.abs(this.getY() - curHit.groundY) < 0.15) {
                    this.setPos(this.getX(), curHit.groundY, this.getZ());
                    this.setOnGround(true);
                }
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
                boolean isModularSlope = (this.level()
                        .getBlockState(BlockPos.containing(this.getX(), this.getY(), this.getZ()))
                        .getBlock() instanceof com.microdrone.mod.block.ModularSlopeBlock) ||
                        (this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.5, this.getZ()))
                                .getBlock() instanceof com.microdrone.mod.block.ModularSlopeBlock);

                // スロープ坂の低速走行時(<= 0.18f)はジャンプせずスムーズに平地へ乗り移る
                if (isModularSlope && this.currentSpeed <= 0.18f) {
                    // 低速時はジャンプをスキップしてスムーズな乗り移りに任せる
                } else {
                    this.jumpCooldown = 22; // 約1.1秒クールダウン
                    this.setOnGround(false);
                    this.hasImpulse = true;

                    float mass = this.getVehicleMass();
                    double massBounce = 1.0 + Math.max(0.0, (mass - 1.10f) * 0.08); // 重いほどサス反発で力強く弾き出される

                    // ブーストジャンプ台（ダッシュパネル）: 進入時にロケット加速発動！
                    if (isBoost) {
                        this.turboBoostTicks = 35;
                        this.currentSpeed = (float) Math.max(this.currentSpeed * 1.65, 0.62);
                    }

                    // === 全ジャンプ台・スロープ共通: 速度と斜面角度に応じた自然な物理射出 ===
                    // 斜面の法線ベクトルから角度(sinθ, cosθ)を精密計算
                    Vec3 normal = hitCenter.onSlope ? hitCenter.normal : hitFront.normal;
                    double sinTheta = Math.sqrt(normal.x * normal.x + normal.z * normal.z);
                    double cosTheta = Math.max(0.1, normal.y);
                    if (sinTheta < 0.1) sinTheta = 0.7071; // ジャンプ台標準は45° (sin45°)

                    // 登坂速度 V を斜面角度に沿って水平推進力・垂直打ち上げ初速へ完全分解射出！
                    double speed = this.currentSpeed;
                    double launchMult = isBoost ? 1.45 : 1.30;
                    double jumpY = speed * sinTheta * launchMult * massBounce; // 斜面角度と速度に応じた上向き射出初速
                    double fwdSpeed = speed * cosTheta * (isBoost ? 1.25 : 1.05); // 斜面角度と速度に応じた水平推進力

                    double rad = this.getYRot() * Mth.DEG_TO_RAD;
                    double outX = -Mth.sin((float) rad) * fwdSpeed;
                    double outZ = Mth.cos((float) rad) * fwdSpeed;

                    this.carVelocity = new Vec3(outX, jumpY, outZ);
                    this.setDeltaMovement(this.carVelocity);

                    // 車体の空中姿勢: 飛び出した斜面の角度そのままにノーズアップ！
                    float slopePitchDeg = (float) Math.toDegrees(Math.asin(Mth.clamp(sinTheta, 0.0, 1.0)));
                    this.pitchAngle = -slopePitchDeg;

                    // 速度に応じた滞空保護時間 (大ジャンプほど長く、低速なら短く自然に着地)
                    this.jumpAirborneTicks = (int) Mth.clamp(Math.round(jumpY * 26.0), 8, 20);
                    this.setPos(this.getX(), this.getY() + 0.10, this.getZ()); // 先端からわずかに浮かせる

                    // サウンド演出
                    if (isBoost) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.6F);
                    } else if (speed > 0.15f) {
                        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_ELYTRA.value(),
                                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
                    }
                }
            }
        }

        // 衝突検知 (高さ0.10m以上の壁・障害物に激突して移動量が大幅に遮断された場合のみ、マシンの速度を急減衰して確実にストップ)
        // ※ レーシング縁石(高さ0.019m〜0.038m)やスロープ登坂時は激突と判定せずスムーズに通過
        double expectedH = totalDistH;
        double actualH = Math.sqrt(actualMoved.x * actualMoved.x + actualMoved.z * actualMoved.z);
        boolean isBlocked = !this.onSlope
                && (this.horizontalCollision || (expectedH > 0.03 && actualH < expectedH * 0.40));
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

            MicroDroneMod.LOGGER.info(
                    "[COLLISION_DETECTED] Side: {}, Tick: {}, Pos: [{}, {}, {}], Vel: [{}, {}, {}], ExpectedSpeed: {}, ActualSpeed: {}, CurrentSpeed: {}, Grounded: {}, Chunk: [{}, {}]",
                    this.level().isClientSide ? "CLIENT" : "SERVER", this.tickCount,
                    String.format("%.3f", this.getX()), String.format("%.3f", this.getY()),
                    String.format("%.3f", this.getZ()),
                    String.format("%.3f", this.carVelocity.x), String.format("%.3f", this.getDeltaMovement().y), String.format("%.3f", this.carVelocity.z),
                    String.format("%.3f", expectedH), String.format("%.3f", actualH),
                    String.format("%.3f", this.currentSpeed),
                    (this.onGround() || this.wheelGrounded),
                    this.chunkPosition().x, this.chunkPosition().z);
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
        if (this.level().isClientSide && (this.onGround() || this.wheelGrounded)
                && (Math.abs(this.currentSpeed) > 0.025f || this.carVelocity.horizontalDistance() > 0.025)) {
            if (this.driftDirection != 0) {
                spawnDriftParticles(this.driftTicks >= 14);
            } else if (this.isRealMode() && this.realPhysics.isDrifting()) {
                spawnDriftParticles(false);
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

            // 0. 染料による車体塗装
            if (heldItem.getItem() instanceof DyeItem dyeItem) {
                int color = dyeItem.getDyeColor().getTextureDiffuseColor();
                this.setBodyColor(color);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            this.getX(), this.getY() + 0.15, this.getZ(),
                            8, 0.15, 0.1, 0.15, 0.02);
                }
                if (!player.getAbilities().instabuild) {
                    heldItem.shrink(1);
                }
                player.sendSystemMessage(Component.translatable("message.microdrone.car_dyed",
                        Component.translatable("color.minecraft." + dyeItem.getDyeColor().getName())));
                return InteractionResult.SUCCESS;
            }

            // 0-b. 水入りバケツまたは濡れたスポンジで塗装洗い落とし
            if (heldItem.is(Items.WATER_BUCKET) || heldItem.is(Items.WET_SPONGE)) {
                if (this.getBodyColor() != -1) {
                    this.setBodyColor(-1);
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.2F);
                    if (this.level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.SPLASH,
                                this.getX(), this.getY() + 0.15, this.getZ(),
                                12, 0.15, 0.1, 0.15, 0.05);
                    }
                    player.sendSystemMessage(Component.translatable("message.microdrone.car_dye_removed"));
                    return InteractionResult.SUCCESS;
                }
            }

            // 0-c. フェーズ1テスト用: 紙 (Items.PAPER) でサンプルステッカー貼り付け
            if (heldItem.is(Items.PAPER)) {
                boolean isTruck = "truck".equalsIgnoreCase(getVariantId());
                float hoodY = isTruck ? 26.2f : 10.2f;
                float hoodZ = isTruck ? 10.0f : 9.0f;
                float hoodScale = isTruck ? 7.0f : 5.5f;

                CarDecalData testDecal = new CarDecalData(
                        "sample_racing_43.png",
                        "body",
                        0.0f, hoodY, hoodZ,
                        -90.0f, 0.0f, 0.0f,
                        hoodScale
                );
                this.addDecal(testDecal);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.ITEM_SLIME,
                            this.getX(), this.getY() + 0.2, this.getZ(),
                            10, 0.15, 0.1, 0.15, 0.02);
                }
                player.sendSystemMessage(Component.translatable("message.microdrone.car_decal_applied", "sample_racing_43.png"));
                return InteractionResult.SUCCESS;
            }

            // 0-d. ハサミ (Items.SHEARS) でステッカー全剥がし
            if (heldItem.is(Items.SHEARS)) {
                if (!this.getDecals().isEmpty()) {
                    this.clearDecals();
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendSystemMessage(Component.translatable("message.microdrone.car_decal_cleared"));
                    return InteractionResult.SUCCESS;
                }
            }

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
                        this.level().addFreshEntity(
                                new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), oldMotor));
                    }
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
                player.sendSystemMessage(Component.translatable("message.microdrone.motor_installed",
                        motorItem.getTier(), motorItem.getTierName(),
                        String.format("%.1f", motorItem.getDisplaySpeedMps())));
                return InteractionResult.SUCCESS;
            }

            // 2.5. タイヤ換装 (装着・交換)
            if (heldItem.getItem() instanceof com.microdrone.mod.item.RcTireItem tireItem) {
                ItemStack oldTire = this.installedTire.copy();
                this.installedTire = heldItem.split(1);
                if (!oldTire.isEmpty()) {
                    if (!player.getInventory().add(oldTire)) {
                        this.level().addFreshEntity(
                                new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), oldTire));
                    }
                }
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
                player.sendSystemMessage(Component.translatable("message.microdrone.tire_installed",
                        Component.translatable("tooltip.microdrone.tire_type." + tireItem.getTireType().getId())));
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
                        this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(),
                                this.installedBattery));
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
            ItemStack dropStack = new ItemStack(ModItems.RC_CAR_ITEM.get());
            if (!"default".equalsIgnoreCase(getVariantId())) {
                net.minecraft.world.item.component.CustomData.update(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        dropStack,
                        tag -> tag.putString("Variant", getVariantId()));
                var config = com.microdrone.mod.config.car.CarConfigManager.getConfig(getVariantId());
                if (config != null && config.displayName != null) {
                    dropStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            Component.literal(config.displayName));
                }
            }
            if (getBodyColor() != -1) {
                net.minecraft.world.item.component.CustomData.update(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        dropStack,
                        tag -> tag.putInt("BodyColor", getBodyColor()));
            }
            if (!this.decals.isEmpty()) {
                net.minecraft.world.item.component.CustomData.update(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        dropStack,
                        tag -> tag.put("Decals", serializeDecals(this.decals)));
            }
            ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), dropStack);
            this.level().addFreshEntity(item);

            if (!this.installedBattery.isEmpty()) {
                this.level().addFreshEntity(
                        new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedBattery));
                this.installedBattery = ItemStack.EMPTY;
            }
            if (!this.installedMotor.isEmpty()) {
                this.level().addFreshEntity(
                        new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedMotor));
                this.installedMotor = ItemStack.EMPTY;
            }
            if (!this.installedTire.isEmpty()) {
                this.level().addFreshEntity(
                        new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedTire));
                this.installedTire = ItemStack.EMPTY;
            }
            this.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source))
            return false;
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (this.pilotUUID != null && this.level() instanceof ServerLevel sl) {
                ServerPlayer p = (ServerPlayer) sl.getPlayerByUUID(this.pilotUUID);
                if (p != null)
                    this.stopPiloting(p);
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

    public String getVariantId() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    public void setVariantId(String variantId) {
        this.entityData.set(DATA_VARIANT_ID, (variantId != null && !variantId.isBlank()) ? variantId : "default");
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
        if (compound.contains("StartX", 6)) {
            this.startX = compound.getDouble("StartX");
            this.startY = compound.getDouble("StartY");
            this.startZ = compound.getDouble("StartZ");
        } else {
            this.startX = Double.NaN;
            this.startY = Double.NaN;
            this.startZ = Double.NaN;
        }
        if (compound.contains("InstalledBattery", 10)) {
            this.installedBattery = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledBattery"))
                    .orElse(ItemStack.EMPTY);
            updateBatterySyncData();
        }
        if (compound.contains("InstalledMotor", 10)) {
            this.installedMotor = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledMotor"))
                    .orElse(ItemStack.EMPTY);
            updateMotorSyncData();
        }
        if (compound.contains("InstalledTire")) {
            this.installedTire = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledTire"))
                    .orElse(ItemStack.EMPTY);
        }
        this.accumulatedDistance = compound.getDouble("BatteryAccumulatedDist");
        if (compound.contains("CustomWeight", 99)) {
            this.entityData.set(DATA_CUSTOM_WEIGHT, compound.getFloat("CustomWeight"));
        }
        if (compound.contains("VariantId", 8)) {
            setVariantId(compound.getString("VariantId"));
        }
        if (compound.contains("Headlight")) {
            setHeadlightOn(compound.getBoolean("Headlight"));
        }
        if (compound.contains("RealDrivingMode")) {
            this.entityData.set(DATA_REAL_MODE, compound.getBoolean("RealDrivingMode"));
        }
        if (compound.contains("CounterMaxAngle")) {
            setCounterMaxAngle(compound.getFloat("CounterMaxAngle"));
        }
        if (compound.contains("DriftStartAngle")) {
            setDriftStartAngle(compound.getFloat("DriftStartAngle"));
        }
        if (compound.contains("BodyColor")) {
            setBodyColor(compound.getInt("BodyColor"));
        }
        if (compound.contains("Decals", 10)) {
            setDecals(deserializeDecals(compound.getCompound("Decals")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.pilotUUID != null) {
            compound.putUUID("PilotUUID", this.pilotUUID);
        }
        if (!Double.isNaN(this.startX)) {
            compound.putDouble("StartX", this.startX);
            compound.putDouble("StartY", this.startY);
            compound.putDouble("StartZ", this.startZ);
        }
        if (!this.installedBattery.isEmpty()) {
            compound.put("InstalledBattery", this.installedBattery.save(this.registryAccess()));
        }
        if (!this.installedMotor.isEmpty()) {
            compound.put("InstalledMotor", this.installedMotor.save(this.registryAccess()));
        }
        if (!this.installedTire.isEmpty()) {
            compound.put("InstalledTire", this.installedTire.save(this.registryAccess()));
        }
        compound.putDouble("BatteryAccumulatedDist", this.accumulatedDistance);
        compound.putFloat("CustomWeight", this.entityData.get(DATA_CUSTOM_WEIGHT));
        compound.putString("VariantId", getVariantId());
        compound.putBoolean("Headlight", isHeadlightOn());
        compound.putBoolean("RealDrivingMode", isRealMode());
        compound.putFloat("CounterMaxAngle", getCounterMaxAngle());
        compound.putFloat("DriftStartAngle", getDriftStartAngle());
        compound.putInt("BodyColor", getBodyColor());
        compound.put("Decals", serializeDecals(this.decals));
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

        Iterable<net.minecraft.world.phys.shapes.VoxelShape> collisions = this.level().getBlockCollisions(this,
                checkAABB);
        for (net.minecraft.world.phys.shapes.VoxelShape shape : collisions) {
            if (!shape.isEmpty()) {
                for (net.minecraft.world.phys.AABB aabb : shape.toAabbs()) {
                    // スロープブロック（ジャンプ台・モジュラースロープ）への進入は壁激突と判定しない
                    BlockPos bp = BlockPos.containing((aabb.minX + aabb.maxX) * 0.5, aabb.minY + 0.05,
                            (aabb.minZ + aabb.maxZ) * 0.5);
                    if (this.level().getBlockState(bp).getBlock() instanceof com.microdrone.mod.block.RacingSlopeBlock
                            ||
                            this.level().getBlockState(bp)
                                    .getBlock() instanceof com.microdrone.mod.block.ModularSlopeBlock) {
                        continue;
                    }

                    // 高さ判定: レーシング縁石(約0.03m)除外。スロープ走行中は登りきり段差(0.25m)を考慮して0.35m以上を壁とみなす
                    double wallThreshold = this.onSlope ? 0.35 : 0.10;
                    if (aabb.maxY - this.getY() > wallThreshold) {
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

    public float getSuspFL() {
        return this.suspFL;
    }

    public float getSuspFR() {
        return this.suspFR;
    }

    public float getSuspRL() {
        return this.suspRL;
    }

    public float getSuspRR() {
        return this.suspRR;
    }

    public float getChassisBounce() {
        return this.chassisBounce;
    }

    public boolean isWheelGrounded() {
        return this.wheelGrounded;
    }

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

        public WheelRaycastHit(double groundY, Vec3 normal, boolean onSlope, boolean isLip, boolean isBoost,
                double progress, Direction slopeFacing) {
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
        boolean isTruck = "truck".equalsIgnoreCase(getVariantId());
        if (isTruck) {
            halfL = 0.32;
            halfW = 0.26;
        }

        double px = getX();
        double py = getY();
        double pz = getZ();

        // 4輪位置の地面高さを三角形メッシュレイキャストで精密サンプリング
        WheelRaycastHit hitFL = sampleWheelGround(px + fwdX * halfL - rightX * halfW, py,
                pz + fwdZ * halfL - rightZ * halfW);
        WheelRaycastHit hitFR = sampleWheelGround(px + fwdX * halfL + rightX * halfW, py,
                pz + fwdZ * halfL + rightZ * halfW);
        WheelRaycastHit hitRL = sampleWheelGround(px - fwdX * halfL - rightX * halfW, py,
                pz - fwdZ * halfL - rightZ * halfW);
        WheelRaycastHit hitRR = sampleWheelGround(px - fwdX * halfL + rightX * halfW, py,
                pz - fwdZ * halfL + rightZ * halfW);

        double hFL = hitFL.groundY;
        double hFR = hitFR.groundY;
        double hRL = hitRL.groundY;
        double hRR = hitRR.groundY;

        // 各タイヤのサスペンション伸縮ストローク量 (幾何学パーツ限界: トラックは縮み+0.28m, 伸び-0.20m)
        double maxBump = isTruck ? 0.28 : 0.08;
        double maxDroop = isTruck ? -0.20 : -0.08;
        float targetSuspFL = (float) Mth.clamp(hFL - py, maxDroop, maxBump);
        float targetSuspFR = (float) Mth.clamp(hFR - py, maxDroop, maxBump);
        float targetSuspRL = (float) Mth.clamp(hRL - py, maxDroop, maxBump);
        float targetSuspRR = (float) Mth.clamp(hRR - py, maxDroop, maxBump);

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
            this.setOnGround(this.wheelGrounded);
        }

        // 落下速度の記録 (着地直前に motionY が 0 になる前の真の落下速度)
        double currentVy = this.getDeltaMovement().y;
        if (currentVy < -0.02) {
            this.lastFallSpeed = Math.abs(currentVy);
        }

        // 着地衝撃ダンパー: 空中から接地した瞬間に車重と落下速度に応じたサスペンション沈み込み・車体バウンスを発生
        boolean isGroundedNow = (this.jumpAirborneTicks <= 0) && (this.onGround() || this.wheelGrounded);
        if (this.wasAirborneLastTick && isGroundedNow) {
            float mass = this.getVehicleMass();
            double impactSpeed = Math.max(Math.abs(this.getDeltaMovement().y), this.lastFallSpeed);
            if (impactSpeed > 0.04) {
                float impactCompress = (float) Math.min(maxBump, impactSpeed * 0.85 * (mass / 1.10f));
                this.suspFL = (float) Math.min(maxBump, this.suspFL + impactCompress);
                this.suspFR = (float) Math.min(maxBump, this.suspFR + impactCompress);
                this.suspRL = (float) Math.min(maxBump, this.suspRL + impactCompress);
                this.suspRR = (float) Math.min(maxBump, this.suspRR + impactCompress);

                // 車体（シャシー・ボディ）を下向きにドスンと沈ませる下向き初速を与える
                this.chassisBounceVelocity = -impactCompress * 1.50f;

                float pitch = 1.30f - (mass * 0.25f);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.7F, pitch);
            }
            this.lastFallSpeed = 0.0;
        }
        this.wasAirborneLastTick = !isGroundedNow;

        // 車体バウンスの減衰振動（スプリング・ダンパー物理系: 沈み込んでからフワッと戻る）
        float springK = 0.24f; // スプリング剛性 (元に戻そうとする力)
        float damping = 0.75f; // オイルダンパー減衰比
        this.chassisBounceVelocity += (-this.chassisBounce * springK);
        this.chassisBounceVelocity *= damping;
        this.chassisBounce += this.chassisBounceVelocity;
        this.chassisBounce = Mth.clamp(this.chassisBounce, -0.28f, 0.15f);
        if (Math.abs(this.chassisBounce) < 0.001f && Math.abs(this.chassisBounceVelocity) < 0.001f) {
            this.chassisBounce = 0.0f;
            this.chassisBounceVelocity = 0.0f;
        }

        // 斜面判定＆登り方向ベクトルの内積計算
        if (this.onSlope) {
            // 最も支配的なスロープの向きを取得
            Direction slopeDir = hitFL.onSlope ? hitFL.slopeFacing
                    : (hitFR.onSlope ? hitFR.slopeFacing : (hitRL.onSlope ? hitRL.slopeFacing : hitRR.slopeFacing));
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
                // 斜面上では常に路面高さに車体を滑らかに密着・吸着させて沈み込み・跳ね上がりを100%遮断
                if (py < avgGroundY || Math.abs(py - avgGroundY) < 0.20) {
                    this.setPos(px, avgGroundY, pz);
                    py = avgGroundY;
                    this.setOnGround(true);
                }
            } else if (py < avgGroundY && (this.onGround() || this.wheelGrounded)) {
                double liftY = Math.min(avgGroundY, py + 0.25);
                this.setPos(px, liftY, pz);
                py = liftY;
                this.setOnGround(true);
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
            if (this.isRealMode()) {
                accelPitch = -this.realPhysics.getWeightTransfer().getLongitudinalShift() * 34.0f;
            } else if (this.inputForward > 0.1f) {
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

        // 車体コンフィグからロール倍率と最大ロール角を取得
        var carConfig = com.microdrone.mod.config.car.CarConfigManager.getConfig(getVariantId());
        float rollMult = (carConfig != null) ? carConfig.properties.rollMultiplier : 1.0f;
        float maxRoll = (carConfig != null) ? carConfig.properties.maxRollAngle : 48.0f;

        // 旋回・ドリフトによる遠心力ロール (外側サスペンションが正しく沈み込む)
        float dynamicRoll = 0.0f;
        if (this.isRealMode()) {
            double totalLatForce = this.realPhysics.getLateralForceFront() + this.realPhysics.getLateralForceRear();
            dynamicRoll = (float) (-totalLatForce * 550.0) * rollMult;
        } else if (this.driftDirection != 0) {
            float baseRoll = 9.0f;
            float totalRoll = baseRoll + (counterAmount * 8.5f);
            dynamicRoll = -this.driftDirection * totalRoll * rollMult;
        } else {
            dynamicRoll = this.steerAngle * (float) (this.currentSpeed * 3.5) * rollMult;
        }
        float targetRoll = Mth.clamp(groundRoll + dynamicRoll, -maxRoll, maxRoll);
        this.rollAngle = Mth.lerp(0.35f, this.rollAngle, targetRoll);
    }

    /**
     * タイヤ位置の地面高さ・三角形メッシュ衝突レイキャスト
     */
    public WheelRaycastHit sampleWheelGround(double x, double y, double z) {
        // ① スプライン道路・ヘアピンカーブのメッシュ判定 (最優先)
        com.microdrone.mod.block.road.RoadSplineGenerator.RoadHitResult roadHit =
                com.microdrone.mod.block.road.RoadSplineManager.sampleRoad(level(), x, y, z);
        if (roadHit.hit) {
            Direction approxFacing = Direction.getNearest(roadHit.tangent.x, 0.0, roadHit.tangent.z);
            return new WheelRaycastHit(roadHit.groundY, roadHit.normal, true, roadHit.isLip, false, roadHit.progress, approxFacing);
        }

        BlockPos pos = BlockPos.containing(x, y, z);
        net.minecraft.world.level.block.state.BlockState st = level().getBlockState(pos);
        if (!(st.getBlock() instanceof com.microdrone.mod.block.RacingSlopeBlock)
                && !(st.getBlock() instanceof com.microdrone.mod.block.ModularSlopeBlock)) {
            pos = pos.below();
            st = level().getBlockState(pos);
        }
        if (st.getBlock() instanceof com.microdrone.mod.block.RacingSlopeBlock) {
            double relX = x - pos.getX();
            double relZ = z - pos.getZ();
            com.microdrone.mod.block.RacingSlopeBlock.RampHitResult hit = com.microdrone.mod.block.RacingSlopeBlock
                    .raycastSlopeMesh(st, relX, relZ);
            if (hit.hit) {
                Direction facing = st.getValue(com.microdrone.mod.block.RacingSlopeBlock.FACING);
                return new WheelRaycastHit(pos.getY() + hit.height + 0.20, hit.normal, true, hit.isLip, hit.isBoost,
                        hit.progress, facing);
            }
        } else if (st.getBlock() instanceof com.microdrone.mod.block.ModularSlopeBlock) {
            double relX = x - pos.getX();
            double relZ = z - pos.getZ();
            com.microdrone.mod.block.RacingSlopeBlock.RampHitResult hit = com.microdrone.mod.block.ModularSlopeBlock
                    .raycastSlopeMesh(level(), pos, st, relX, relZ);
            if (hit.hit) {
                Direction facing = st.getValue(com.microdrone.mod.block.ModularSlopeBlock.FACING);
                return new WheelRaycastHit(pos.getY() + hit.height, hit.normal, true, hit.isLip, false, hit.progress,
                        facing);
            }
        }

        net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(x, y + 0.25, z);
        net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(x, y - 1.25, z);
        net.minecraft.world.phys.BlockHitResult hit = level().clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                this));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return WheelRaycastHit.FLAT(hit.getLocation().y);
        }
        return new WheelRaycastHit(y - 2.0, new net.minecraft.world.phys.Vec3(0, 1, 0), false, false, false, 0.0, Direction.UP);
    }

    private double sampleGroundY(double x, double y, double z) {
        return sampleWheelGround(x, y, z).groundY;
    }

    // === レース計測・チェックポイント通過センシング (Step 1) ===
    private void checkCheckpointSensors(ServerPlayer pilot) {
        Vec3 pos = this.position();
        BlockPos atPos = BlockPos.containing(pos.x, pos.y, pos.z);
        BlockPos belowPos = atPos.below();

        BlockPos[] checkPositions = new BlockPos[] {
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
                    { 0.25, 0.0 }, { -0.25, 0.0 }, { 0.0, 0.25 }, { 0.0, -0.25 }
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

                if (!isGate && !isCheckpoint)
                    continue;

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
                    pilot.displayClientMessage(
                            Component.translatable("message.microdrone.lap_invalid", remainSec), true);
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
                1.0f);

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

    // ==========================================
    // 360°縦ループ（Loop-the-Loop）完全自由物理システム
    // ==========================================

    public static class LoopSurfaceHit {
        public final boolean onSurface;
        public final float angle; // 0°〜360°
        public final BlockPos blockPos;
        public final Direction facing;
        public final double distanceToRoad;
        public final double lateralOffset; // 中心からの左右ズレ

        public LoopSurfaceHit(boolean onSurface, float angle, BlockPos blockPos, Direction facing, double distanceToRoad, double lateralOffset) {
            this.onSurface = onSurface;
            this.angle = angle;
            this.blockPos = blockPos;
            this.facing = facing;
            this.distanceToRoad = distanceToRoad;
            this.lateralOffset = lateralOffset;
        }

        public static final LoopSurfaceHit NONE = new LoopSurfaceHit(false, 0, null, Direction.NORTH, 999, 0);
    }

    /**
     * ブロックのFACINGに応じたループの前進進行方向ベクトル（手前進入プレートから奥脱出プレートへ抜けるワールド方向）
     * GeckoLibのGeoBlockRendererはNORTHで回転0度（無回転）のため、モデルのローカル+Z方向はfacing.getOpposite()に一致
     */
    private Vec3 getLoopForward(Direction facing) {
        Direction moveDir = facing.getOpposite();
        return switch (moveDir) {
            case NORTH -> new Vec3(0, 0, -1);
            case SOUTH -> new Vec3(0, 0, 1);
            case WEST  -> new Vec3(-1, 0, 0);
            case EAST  -> new Vec3(1, 0, 0);
            default    -> new Vec3(0, 0, 1);
        };
    }

    /**
     * ループの右方向ベクトル（車首前進方向に対する右側ベクトル）
     */
    private Vec3 getLoopRight(Direction facing) {
        Direction moveDir = facing.getOpposite();
        return switch (moveDir) {
            case SOUTH -> new Vec3(1, 0, 0);  // 南向きの右は東 (+X)
            case NORTH -> new Vec3(-1, 0, 0); // 北向きの右は西 (-X)
            case EAST  -> new Vec3(0, 0, 1);  // 東向きの右は南 (+Z)
            case WEST  -> new Vec3(0, 0, -1); // 西向きの右は北 (-Z)
            default    -> new Vec3(1, 0, 0);
        };
    }

    /**
     * 車両進行方向の基準Yaw角
     */
    private float getLoopYaw(Direction facing) {
        return facing.getOpposite().toYRot();
    }

    /**
     * 車両位置から360°縦ループの内面路面との接触・傾斜角をサンプリング (完全自由物理判定)
     */
    private LoopSurfaceHit sampleLoopSurface(Vec3 pos) {
        if (Math.abs(this.currentSpeed) < 0.05f) return LoopSurfaceHit.NONE;

        // 走行中のループブロックがある場合は最優先でチェック
        if (this.loopBlockPos != null) {
            LoopSurfaceHit hit = checkLoopBlock(pos, this.loopBlockPos);
            if (hit.onSurface) {
                return hit;
            }
        }

        BlockPos carPos = BlockPos.containing(pos);
        // 車両がループ上部（高さ最大約4m）にいる際も確実に検出するため、dy は -4 〜 +1 を探索
        for (int dy = -4; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos checkPos = carPos.offset(dx, dy, dz);
                    if (this.loopBlockPos != null && checkPos.equals(this.loopBlockPos)) {
                        continue;
                    }
                    LoopSurfaceHit hit = checkLoopBlock(pos, checkPos);
                    if (hit.onSurface) {
                        return hit;
                    }
                }
            }
        }
        return LoopSurfaceHit.NONE;
    }

    /**
     * 指定ブロック位置の縦ループブロックに対する路面判定
     */
    private LoopSurfaceHit checkLoopBlock(Vec3 pos, BlockPos checkPos) {
        BlockState state = this.level().getBlockState(checkPos);
        if (!state.is(ModBlocks.VERTICAL_LOOP.get())) {
            return LoopSurfaceHit.NONE;
        }

        Direction facing = state.getValue(VerticalLoopBlock.FACING);
        Vec3 fwd = getLoopForward(facing);
        Vec3 right = getLoopRight(facing);
        Vec3 bCenter = new Vec3(checkPos.getX() + 0.5, checkPos.getY(), checkPos.getZ() + 0.5);

        Vec3 diff = pos.subtract(bCenter);
        double locZ = diff.x * fwd.x + diff.z * fwd.z;
        double locX = diff.x * right.x + diff.z * right.z;
        double locY = pos.y - checkPos.getY();

        // シリンダー中心 (ピボット Y=31px -> 1.9375m, Z=0.0m)
        double cy = 1.9375;
        double cz = 0.0;
        double dyCenter = locY - cy;
        double dzCenter = locZ - cz;
        double r = Math.sqrt(dyCenter * dyCenter + dzCenter * dzCenter);

        // 角度 phi (0°〜360°)
        // 手前側(進入プレート側: dzCenter=-1.9375)が登り壁(90°)、奥側(dzCenter=+1.9375)が下り壁(270°)
        // 底面進入: locY=0, locZ=0 -> atan2(0, 1.9375) = 0°
        // 90°垂直登り壁(手前): locY=1.9375, locZ=-1.9375 -> atan2(1.9375, 0) = 90°
        // 180°天井: locY=3.875, locZ=0 -> atan2(0, -1.9375) = 180°
        // 270°垂直下り壁(奥): locY=1.9375, locZ=+1.9375 -> atan2(-1.9375, 0) = 270°
        float phi = (float) Math.toDegrees(Math.atan2(-dzCenter, -dyCenter));
        if (phi < 0.0f) phi += 360.0f;

        // 新モデル（32px幅 = 2.0m）のスクリュー中心線:
        // 進入プレート（0°）: X = +24px = +1.500m
        // 脱出プレート（360°）: X = -8.5px = -0.531m (全周で -2.03125m スライド)
        double roadCenterX = 1.500 - (phi / 360.0) * 2.03125;
        double lateral = locX - roadCenterX;

        // 道幅判定: 幅 2.0m (左右 ±1.0m)。ステアリング旋回・端寄せ突入の余裕を大きく取り ±1.50m に拡張
        boolean withinWidth = (Math.abs(lateral) <= 1.50);

        // 半径判定: R = 1.9375m に対し、路面接触許容範囲 (±0.75m)
        double R = 1.9375;
        double distToRoad = R - r;
        boolean nearSurface = (Math.abs(distToRoad) <= 0.75);

        // 車の進行向きとの整合性 (逆走突入防止)
        float yawRad = (float) -Math.toRadians(this.getYRot());
        Vec3 carDir = new Vec3(Mth.sin(yawRad), 0, Mth.cos(yawRad));
        if (phi < 45.0f) {
            // 進入プレート側: 手前登り壁(-fwd)へ向かって正しく前進突入しているかを判定
            if (carDir.dot(fwd.scale(-1)) < 0.15) return LoopSurfaceHit.NONE;
        } else if (phi > 315.0f) {
            // 脱出プレート側: 奥(+fwd)へ向かって前進脱出しているかを判定
            if (carDir.dot(fwd) < 0.15) return LoopSurfaceHit.NONE;
        }

        if (withinWidth && nearSurface) {
            return new LoopSurfaceHit(true, phi, checkPos, facing, distToRoad, lateral);
        }
        return LoopSurfaceHit.NONE;
    }

    /**
     * 縦ループ内での連続傾斜・接線前進・ステアリング横移動ベクトル (完全自由移動)
     */
    private Vec3 getLoopTangentialMotion(LoopSurfaceHit hit, float speed, float steerInput) {
        Vec3 fwd = getLoopForward(hit.facing);
        Vec3 right = getLoopRight(hit.facing);

        double rad = Math.toRadians(hit.angle);
        // 接線前進ベクトル
        // 周長 L = 2 * PI * 1.9375 = 12.1737m
        // 1周あたりの横スライド量 = -2.03125m
        // vx = -2.03125 / 12.1737 = -0.16685
        // 進入プレートから手前登り壁(90°)を駆け上がり、天井(180°)を経て奥下り壁(270°)へ下る
        double vz = -Math.cos(rad);
        double vy = Math.sin(rad);
        double vx = -(2.03125 / 12.1737); // スクリュー進行

        Vec3 tangent = fwd.scale(vz).add(0, vy, 0).add(right.scale(vx)).normalize();

        // 基本前進速度
        Vec3 motion = tangent.scale(speed);

        // 自由ステアリング操舵 (A/Dキーによる横レーンチェンジ)
        if (Math.abs(steerInput) > 0.05f) {
            // Aキー(左: +steerInput) でマイナス側、Dキー(右: -steerInput) でプラス側
            motion = motion.add(right.scale(-steerInput * 0.024f * (Math.abs(speed) / 0.25f)));
        }

        // 路面法線方向のサスペンション吸着・密着補正 (路面への密着を維持)
        // 中心から外側（路面）に向かう単位法線ベクトル:
        // 底面(0°): -Y (地面へ押し付け)
        // 手前上り壁(90°): -fwd (手前の壁面へ押し付け)
        // 天井(180°): +Y (天井へ押し付け)
        // 奥下り壁(270°): +fwd (奥の壁面へ押し付け)
        double ny = -Math.cos(rad);
        double nz = -Math.sin(rad);
        Vec3 outwardNormal = fwd.scale(nz).add(0, ny, 0).normalize();
        // 車が路面より内側(r < R -> distToRoad > 0)にある場合、外側(路面)へ押し付けて密着を維持！
        motion = motion.add(outwardNormal.scale(hit.distanceToRoad * 0.45));

        return motion;
    }

    /**
     * スプライン360°ループ内の接線前進・ステアリング横移動・法線密着ベクトル
     */
    private Vec3 getSplineLoopTangentialMotion(com.microdrone.mod.block.road.RoadSplineGenerator.RoadHitResult hit, float speed, float steerInput) {
        Vec3 tangent = hit.tangent;
        Vec3 leftVec = hit.normal.cross(tangent);
        leftVec = leftVec.lengthSqr() > 1e-6 ? leftVec.normalize() : new Vec3(1, 0, 0);

        Vec3 motion = tangent.scale(speed);

        if (Math.abs(steerInput) > 0.05f) {
            motion = motion.add(leftVec.scale(steerInput * 0.024f * (Math.abs(speed) / 0.25f)));
        }

        // 路面表面点への法線方向密着補正 (天井・垂直壁でも隙間なく張り付く)
        Vec3 toSurface = hit.surfacePoint.subtract(this.position());
        double normalGap = toSurface.dot(hit.normal);
        motion = motion.add(hit.normal.scale(normalGap * 0.45));

        return motion;
    }

    /**
     * 指定座標付近にあるジャンプパッドブロック（JumpPadBlock）を取得
     */
    private net.minecraft.world.level.block.state.BlockState getPadStateAt(double x, double y, double z) {
        for (double dy : new double[]{ 0.1, -0.2, 0.0, -0.5 }) {
            BlockPos pos = BlockPos.containing(x, y + dy, z);
            net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(pos);
            if (state.getBlock() instanceof com.microdrone.mod.block.JumpPadBlock) {
                return state;
            }
        }
        return null;
    }

    // === ヘッドライト・実光源システム ===
    public boolean isHeadlightOn() {
        return this.entityData.get(DATA_HEADLIGHT);
    }

    public void setHeadlightOn(boolean on) {
        this.entityData.set(DATA_HEADLIGHT, on);
        if (!on) {
            clearHeadlight();
        }
    }

    public void toggleHeadlight() {
        setHeadlightOn(!isHeadlightOn());
    }

    public float getHeadlightProgress(float partialTick) {
        return Mth.lerp(partialTick, this.prevHeadlightProgress, this.headlightProgress);
    }

    /**
     * 現在の入力がブレーキ／後退またはハンドブレーキ状態かを返す
     */
    public boolean isBrakingInput() {
        return hasBattery() && getBatteryRemaining() > 0
                && (this.inputForward < -0.05f || this.inputHandbrake);
    }

    /**
     * ブレーキランプ点灯状態（操縦中はローカル入力で即時反映、それ以外は同期値）
     */
    public boolean isBrakeLightOn() {
        if (this.level().isClientSide && this.locallyControlled) {
            return isBrakingInput();
        }
        return this.entityData.get(DATA_BRAKING);
    }

    /**
     * 車両の前方に実光源 (Blocks.LIGHT, level 15) を配置し、夜間を実際に照らす
     */
    private void updateHeadlightLighting() {
        if (this.level().isClientSide) return;

        if (!isHeadlightOn()) {
            clearHeadlight();
            return;
        }

        // 車両の前方約 1.8 ブロック、高さ +0.3 付近
        Vec3 look = getLookAngle();
        Vec3 lightVec = this.position().add(look.scale(1.8)).add(0, 0.3, 0);
        BlockPos currentLightPos = BlockPos.containing(lightVec);

        if (this.lastHeadlightPos != null && !this.lastHeadlightPos.equals(currentLightPos)) {
            clearHeadlight();
        }

        if (this.level().getBlockState(currentLightPos).isAir()) {
            this.level().setBlock(currentLightPos,
                    net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState().setValue(
                            net.minecraft.world.level.block.LightBlock.LEVEL, 15),
                    3);
            this.lastHeadlightPos = currentLightPos;
        }
    }

    public void clearHeadlight() {
        if (this.lastHeadlightPos != null && !this.level().isClientSide) {
            net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(this.lastHeadlightPos);
            if (state.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                this.level().removeBlock(this.lastHeadlightPos, false);
            }
            this.lastHeadlightPos = null;
        }
    }

    public int getBodyColor() {
        return this.entityData.get(DATA_BODY_COLOR);
    }

    public void setBodyColor(int color) {
        this.entityData.set(DATA_BODY_COLOR, color);
    }

    public java.util.List<CarDecalData> getDecals() {
        if (!this.decals.isEmpty()) {
            return this.decals;
        }
        if (this.level().isClientSide) {
            CompoundTag syncTag = this.entityData.get(DATA_DECALS);
            java.util.List<CarDecalData> list = deserializeDecals(syncTag);
            if (!list.isEmpty()) {
                this.decals.addAll(list);
            }
            return list;
        }
        return this.decals;
    }

    public void setDecals(java.util.List<CarDecalData> newDecals) {
        this.decals.clear();
        if (newDecals != null) {
            this.decals.addAll(newDecals);
        }
        this.entityData.set(DATA_DECALS, serializeDecals(this.decals));
    }

    public void addDecal(CarDecalData decal) {
        this.decals.add(decal);
        this.entityData.set(DATA_DECALS, serializeDecals(this.decals));
    }

    public void clearDecals() {
        this.decals.clear();
        this.entityData.set(DATA_DECALS, new CompoundTag());
    }

    public static CompoundTag serializeDecals(java.util.List<CarDecalData> list) {
        CompoundTag root = new CompoundTag();
        ListTag listTag = new ListTag();
        if (list != null) {
            for (CarDecalData d : list) {
                if (d != null) listTag.add(d.toTag());
            }
        }
        root.put("List", listTag);
        return root;
    }

    public static java.util.List<CarDecalData> deserializeDecals(CompoundTag root) {
        java.util.List<CarDecalData> list = new java.util.ArrayList<>();
        if (root != null && root.contains("List", 9)) {
            ListTag listTag = root.getList("List", 10);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(CarDecalData.fromTag(listTag.getCompound(i)));
            }
        }
        return list;
    }

    @Override
    public void remove(RemovalReason reason) {
        clearHeadlight();
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
}
