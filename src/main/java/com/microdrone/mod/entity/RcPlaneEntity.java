package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.ModItems;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * プロポで遠隔操縦可能なマイクロRCプレーン（セスナ風・固定翼ラジコン飛行機）
 * - 固定翼航空力学: スロットル推進力、対気速度、揚力、失速急降下、滑空
 * - 3軸舵面可動: エレベーター(昇降舵)、エルロン(補助翼)、ラダー(方向舵)
 * - 尾輪式(テールドラッガー)ランディングギア: 前2輪 + 尾輪1輪による地上タキシング＆滑走離着陸
 * - モード切替ハイブリッド: 直感アシスト飛行(自動水平復帰) ⇔ 本格アクロバット(手動ロール・宙返り)
 * - 飛行制御ログ出力システム ＆ 地面接触時の姿勢リセット防止
 */
public class RcPlaneEntity extends Entity implements GeoEntity, RcControllable, com.microdrone.mod.decal.RcDecalHolder {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === 同期用 EntityData ===
    private static final EntityDataAccessor<CompoundTag> DATA_DECALS =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final java.util.List<com.microdrone.mod.decal.CarDecalData> decals = new java.util.ArrayList<>();
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> DATA_MOTOR_TIER =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_MOTOR =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.ITEM_STACK);

    // フライトパラメータ同期
    private static final EntityDataAccessor<Float> DATA_THROTTLE =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIRSPEED =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH_ANGLE =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_ANGLE =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ELEVATOR_INPUT =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AILERON_INPUT =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RUDDER_INPUT =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PROP_ROT =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_VARIANT_ID =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FLIGHT_MODE =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.INT);

    // 姿勢角 (度) -180度 〜 +180度に正規化
    public float pitchAngle = 0.0f;
    public float prevPitchAngle = 0.0f;
    public float rollAngle = 0.0f;
    public float prevRollAngle = 0.0f;

    // プロペラ・車輪回転角 (度)
    public float propRotation = 0.0f;
    public float wheelRotation = 0.0f;

    // 操縦者UUID
    private UUID pilotUUID = null;
    private double startX, startY, startZ;
    private java.util.UUID dummyEntityUUID = null;
    private RcPilotDummyEntity dummyEntityRef = null;
    private ChunkPos lastChunkPos = null;

    // クライアント側ローカル操縦フラグ
    private boolean locallyControlled = false;

    // 実装アイテムスタック保持
    private ItemStack installedBattery = ItemStack.EMPTY;
    private ItemStack installedMotor = ItemStack.EMPTY;

    // 操縦入力
    public float inputElevator = 0.0f; // S(+1.0:機首上げ) / W(-1.0:機首下げ)
    public float inputAileron = 0.0f;  // A(-1.0:左ロール) / D(+1.0:右ロール)
    public float inputRudder = 0.0f;   // A(-1.0:左旋回) / D(+1.0:右旋回)
    public float inputThrottleDelta = 0.0f; // Space(+)/Shift(-): スロットル増減

    // フライトステート
    public float throttle = 0.0f; // 0.0f 〜 1.0f (スロットル開度)
    public float airspeed = 0.0f; // ブロック/tick
    public int flightMode = 0;    // 0: ASSIST (直感アシスト), 1: ACRO (アクロバット)
    public boolean isStalling = false; // 失速状態フラグ（チャタリング防止ヒステリシス付き）

    // 累積垂直速度
    public double verticalVelocity = 0.0;

    // デバッグログ用
    private int logTimer = 0;

    public RcPlaneEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    public float maxUpStep() {
        return 0.5f;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * 【戦車・ドローン準拠: サーバー遅延テレポート引き戻し遮断 lerpTo】
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled) {
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

        builder.define(DATA_THROTTLE, 0.0f);
        builder.define(DATA_AIRSPEED, 0.0f);
        builder.define(DATA_PITCH_ANGLE, 0.0f);
        builder.define(DATA_ROLL_ANGLE, 0.0f);
        builder.define(DATA_ELEVATOR_INPUT, 0.0f);
        builder.define(DATA_AILERON_INPUT, 0.0f);
        builder.define(DATA_RUDDER_INPUT, 0.0f);
        builder.define(DATA_PROP_ROT, 0.0f);
        builder.define(DATA_FLIGHT_MODE, 0);
        builder.define(DATA_VARIANT_ID, "default");
    }

    public boolean hasBattery() { return entityData.get(DATA_HAS_BATTERY); }
    public int getBatteryRemaining() { return entityData.get(DATA_BATTERY_REMAINING); }
    public int getBatteryMax() { return entityData.get(DATA_BATTERY_MAX); }
    public ItemStack getInstalledBattery() { return entityData.get(DATA_INSTALLED_BATTERY); }
    public int getMotorTier() { return entityData.get(DATA_MOTOR_TIER); }
    public ItemStack getInstalledMotor() { return entityData.get(DATA_INSTALLED_MOTOR); }

    public float getThrottle() { return this.throttle; }
    public float getAirspeed() { return this.airspeed; }
    public float getPitchAngle() { return this.pitchAngle; }
    public float getRollAngle() { return this.rollAngle; }
    public float getElevatorInput() { return entityData.get(DATA_ELEVATOR_INPUT); }
    public float getAileronInput() { return entityData.get(DATA_AILERON_INPUT); }
    public float getRudderInput() { return entityData.get(DATA_RUDDER_INPUT); }
    public float getPropRotation() { return this.propRotation; }
    public int getFlightMode() { return entityData.get(DATA_FLIGHT_MODE); }

    public String getVariantId() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    public void setVariantId(String variantId) {
        this.entityData.set(DATA_VARIANT_ID, (variantId != null && !variantId.isBlank()) ? variantId : "default");
    }

    public void setFlightMode(int mode) {
        this.flightMode = mode;
        if (!level().isClientSide) {
            entityData.set(DATA_FLIGHT_MODE, mode);
        }
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

    @Override
    public void tick() {
        super.tick();

        this.prevPitchAngle = this.pitchAngle;
        this.prevRollAngle = this.rollAngle;

        if (!level().isClientSide) {
            entityData.set(DATA_THROTTLE, this.throttle);
            entityData.set(DATA_AIRSPEED, this.airspeed);
            entityData.set(DATA_PITCH_ANGLE, this.pitchAngle);
            entityData.set(DATA_ROLL_ANGLE, this.rollAngle);
            entityData.set(DATA_PROP_ROT, this.propRotation);
        } else if (!this.locallyControlled) {
            this.throttle = entityData.get(DATA_THROTTLE);
            this.airspeed = entityData.get(DATA_AIRSPEED);
            this.pitchAngle = entityData.get(DATA_PITCH_ANGLE);
            this.rollAngle = entityData.get(DATA_ROLL_ANGLE);
            this.propRotation = entityData.get(DATA_PROP_ROT);
            this.flightMode = entityData.get(DATA_FLIGHT_MODE);
        }

        // サーバー側監視
        if (!level().isClientSide && isPiloted()) {
            ServerPlayer player = (ServerPlayer) level().getPlayerByUUID(pilotUUID);
            if (player != null) {
                double distToStart = Math.sqrt(this.distanceToSqr(this.startX, this.startY, this.startZ));
                if (distToStart > 250.0) {
                    player.sendSystemMessage(Component.translatable("message.microdrone.signal_lost_failsafe"));
                    stopPiloting(player);
                    return;
                }

                ChunkPos currentChunk = new ChunkPos(this.blockPosition());
                if (!currentChunk.equals(this.lastChunkPos)) {
                    this.lastChunkPos = currentChunk;
                    com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(player, this);
                }

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

        if (this.locallyControlled || (!level().isClientSide && isPiloted())) {
            tickFlightPhysics();
            emitFlightDebugLog();
        } else if (!level().isClientSide && !isPiloted()) {
            // 無操縦時の自然落下
            if (!this.onGround()) {
                Vec3 curDelta = this.getDeltaMovement();
                this.verticalVelocity = Math.max(-0.6, this.verticalVelocity - 0.05);
                this.setDeltaMovement(curDelta.x * 0.95, this.verticalVelocity, curDelta.z * 0.95);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.pitchAngle = Mth.lerp(0.08f, this.pitchAngle, -25.0f);
                this.rollAngle = Mth.lerp(0.1f, this.rollAngle, 0.0f);
            } else {
                this.verticalVelocity = 0.0;
                this.pitchAngle = Mth.lerp(0.2f, this.pitchAngle, 6.0f);
                this.rollAngle = Mth.lerp(0.2f, this.rollAngle, 0.0f);
            }
        }
    }

    /**
     * 【詳細フライトログ出力システム】
     * 20 tick (1秒) おきの定期ステータス出力 ＆ 姿勢急変・接地バウンス時の即座アラートログ
     */
    private void emitFlightDebugLog() {
        logTimer++;

        // 姿勢の急変（ガタつき検知）チェック
        float pitchDiff = Math.abs(Mth.wrapDegrees(this.pitchAngle - this.prevPitchAngle));
        float rollDiff = Math.abs(Mth.wrapDegrees(this.rollAngle - this.prevRollAngle));

        if (pitchDiff > 25.0f || rollDiff > 25.0f) {
            MicroDroneMod.LOGGER.warn(String.format("[RC_PLANE] [POSTURE_JUMP_ALERT] Tick: %d | Jump Detected! Pitch: %.1f->%.1f (diff=%.1f), Roll: %.1f->%.1f (diff=%.1f) | onGround: %b, vVel: %.3f, Spd: %.3f",
                    tickCount, prevPitchAngle, pitchAngle, pitchDiff, prevRollAngle, rollAngle, rollDiff, this.onGround(), verticalVelocity, airspeed));
        }

        // 1秒おき (20 tick) のサマリーログ
        if (logTimer % 20 == 0) {
            String side = level().isClientSide ? "CLIENT" : "SERVER";
            String modeStr = flightMode == 0 ? "ASSIST" : "ACRO";
            MicroDroneMod.LOGGER.info(String.format("[RC_PLANE] [%s] [%s] Alt:%.1fm, Spd:%.0fkm/h(%.3f), Thr:%.0f%%, Grnd:%b, vVel:%.3f | Angles: [Yaw=%.1f, Pitch=%.1f, Roll=%.1f] | Input: [Elev=%.2f, Ail=%.2f, Rud=%.2f]",
                    side, modeStr, getY(), airspeed * 72.0f, airspeed, throttle * 100.0f, this.onGround(), verticalVelocity,
                    getYRot(), pitchAngle, rollAngle, inputElevator, inputAileron, inputRudder));
        }
    }

    /**
     * 固定翼航空力学・失速急降下・バンク旋回物理シミュレーション
     */
    private void tickFlightPhysics() {
        boolean hasPower = hasBattery() && getBatteryRemaining() > 0;
        int tier = getMotorTier();

        // 1. スロットル調整 (0.0 〜 1.0)
        if (hasPower) {
            this.throttle = Mth.clamp(this.throttle + this.inputThrottleDelta * 0.035f, 0.0f, 1.0f);
        } else {
            this.throttle = Math.max(0.0f, this.throttle - 0.02f);
        }

        // プロペラ回転
        float propSpeed = 15.0f + this.throttle * (65.0f + tier * 15.0f);
        this.propRotation = (this.propRotation + propSpeed) % 360.0f;

        // 2. 推進力 (Thrust) と 対気速度 (Airspeed)
        float maxSpeedBlocks = 0.58f + (tier * 0.12f);
        float targetAirspeed = this.throttle * maxSpeedBlocks;

        float accelRate = 0.018f + (tier * 0.005f);
        if (this.airspeed < targetAirspeed) {
            this.airspeed = Math.min(this.airspeed + accelRate, targetAirspeed);
        } else {
            this.airspeed = Math.max(this.airspeed - 0.007f, targetAirspeed);
        }

        boolean onGround = this.onGround();

        // 【地上タキシング状態の厳密判定】:
        // 単に onGround なだけでなく、「低速(airspeed < 0.20)」かつ「水平に近い(|roll| < 20度)」ときのみ地上走行とする。
        // 高速飛行中やバンク旋回中に地面をかすめた場合は、空中姿勢を維持したままバウンドさせる！
        boolean isTaxiing = onGround && (this.airspeed < 0.20f) && (Math.abs(this.rollAngle) < 22.0f);

        // 3. 3軸姿勢角の制御
        if (isTaxiing) {
            // === 【完全接地タキシング＆滑走状態】 ===
            this.verticalVelocity = 0.0;

            // 地上待機ピッチ(+6度)。滑走中のエレベーター引きで機首が上下
            float groundPitch = 6.0f + (this.inputElevator * 5.0f);
            this.pitchAngle = Mth.lerp(0.25f, this.pitchAngle, groundPitch);
            this.rollAngle = Mth.lerp(0.25f, this.rollAngle, 0.0f);

            // 地上ステアリング:
            // inputRudder: 左(-1.0)で左旋回(YRot減少)、右(+1.0)で右旋回(YRot増加)
            float turnRate = 2.4f * this.inputRudder * Math.max(0.15f, this.airspeed);
            this.setYRot(this.getYRot() + turnRate);

            // 離陸判定 (Takeoff): 速度0.16以上 かつ Sキー引き(inputElevator > 0.2f)
            if (this.airspeed > 0.16f && this.inputElevator > 0.2f) {
                this.setOnGround(false);
                this.verticalVelocity = 0.16;
                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.16, 0));
                this.pitchAngle = 18.0f;
            }
        } else {
            // === 【空中飛行状態（地面かすめバウンス含む）】 ===
            if (onGround) {
                // 地面かすめ時: 姿勢を強制リセットせず、軽くバウンドさせて飛行を継続
                this.verticalVelocity = Math.max(0.06, -this.verticalVelocity * 0.35);
                MicroDroneMod.LOGGER.info(String.format("[RC_PLANE] [GROUND_SCRATCH] Scratched ground at Y=%.1f, Spd=%.2f, Roll=%.1f -> Bouncing gently without posture reset",
                        getY(), airspeed, rollAngle));
            }

            // 失速判定（ヒステリシス: 突入0.15f以下、脱出0.19f以上でチャタリング・がたつきを完全防止）
            float stallEntrySpeed = 0.15f;
            float stallExitSpeed = 0.19f;
            if (!this.isStalling && this.airspeed < stallEntrySpeed) {
                this.isStalling = true;
                MicroDroneMod.LOGGER.warn(String.format("[RC_PLANE] [STALL_ENTER] Entered stall at Alt:%.1fm, Spd:%.1fkm/h(%.3f), Pitch:%.1f, Roll:%.1f, Mode:%s",
                        getY(), this.airspeed * 72.0f, this.airspeed, this.pitchAngle, this.rollAngle, this.flightMode == 0 ? "ASSIST" : "ACRO"));
            } else if (this.isStalling && this.airspeed > stallExitSpeed) {
                this.isStalling = false;
                MicroDroneMod.LOGGER.info(String.format("[RC_PLANE] [STALL_RECOVER] Recovered from stall at Alt:%.1fm, Spd:%.1fkm/h(%.3f)",
                        getY(), this.airspeed * 72.0f, this.airspeed));
            }

            float controlEfficacy = Mth.clamp(this.airspeed / (maxSpeedBlocks * 0.5f), 0.12f, 1.0f);

            if (this.flightMode == 0) {
                // --- 【直感アシスト飛行モード (ASSIST)】 ---
                if (this.isStalling) {
                    // 失速時: 自動で機首を優しく下げて速度回復を補助（急激な引き戻しはせずスムーズに）
                    this.pitchAngle = Mth.lerp(0.06f, this.pitchAngle, -24.0f);
                    this.rollAngle = Mth.lerp(0.06f, this.rollAngle, 0.0f);
                    this.verticalVelocity = Math.max(-0.65, this.verticalVelocity - 0.05);
                    this.airspeed = Math.min(this.airspeed + 0.010f, maxSpeedBlocks * 0.6f);
                } else {
                    float targetRoll = Mth.clamp(this.inputAileron * 35.0f, -45.0f, 45.0f);
                    this.rollAngle = Mth.lerp(0.18f * controlEfficacy, this.rollAngle, targetRoll);

                    float targetPitch = Mth.clamp(this.inputElevator * 35.0f, -30.0f, 35.0f);
                    this.pitchAngle = Mth.lerp(0.18f * controlEfficacy, this.pitchAngle, targetPitch);

                    // バンク旋回＋エレベーター引き起こし求心旋回（右ロール(roll>0)で右旋回(Yaw増加)に正整合）
                    float rollRad = (float) Math.toRadians(this.rollAngle);
                    float bankTurnFactor = (float) Math.sin(rollRad);
                    float elevatorTighten = 1.0f + (this.inputElevator > 0 ? this.inputElevator * 1.0f : 0.0f);

                    float bankYawRate = bankTurnFactor * 1.9f * elevatorTighten * controlEfficacy;
                    float rudderYawRate = this.inputRudder * 1.2f * controlEfficacy;

                    this.setYRot(this.getYRot() + bankYawRate + rudderYawRate);

                    // 昇降速度
                    float pitchRad = (float) Math.toRadians(this.pitchAngle);
                    float liftFactor = Mth.clamp(this.airspeed / 0.22f, 0.0f, 1.0f);
                    double lift = 0.045 * liftFactor * Math.max(0.0, Math.cos(pitchRad) * Math.cos(rollRad));
                    double forwardY = Math.sin(pitchRad) * this.airspeed * Math.max(0.2, Math.cos(rollRad));

                    this.verticalVelocity = forwardY + lift - 0.045;
                }
            } else {
                // --- 【本格アクロバットモード (ACRO)】 ---
                // ※ 失速しても姿勢（ピッチ・ロール）を勝手に固定・リセットしない！
                // アクロバット機らしく、パイロットの手動操縦を100%尊重。
                // 機体ローカル座標系（主翼・胴体基準）の3軸角速度からワールドオイラー角へ正しく変換
                
                // 1. 機体ローカル角速度の算出（旋回半径を拡大・旋回速度をマイルド化）
                // q: ピッチ角速度 (機首上げ正: 4.5f -> 3.4f でバンク旋回時の急激な回り込みを抑制し旋回半径を拡大)
                float localPitchRate = this.inputElevator * 3.4f * controlEfficacy;
                // p: ロール角速度 (右ロール正: 6.5f -> 5.8f で適度な安定性)
                float localRollRate = this.inputAileron * 5.8f * controlEfficacy;
                // r: ヨー角速度 (右ラダー正) + 主翼揚力による自然な求心旋回モーメント (1.6f -> 1.2f)
                float rollRad = (float) Math.toRadians(this.rollAngle);
                float pitchRad = (float) Math.toRadians(this.pitchAngle);
                float bankNaturalTurn = (float) Math.sin(rollRad) * 1.2f * controlEfficacy;
                float localYawRate = this.inputRudder * 1.6f * controlEfficacy + bankNaturalTurn;

                // 2. 航空機オイラー角微分変換 (Body to World Euler transformation)
                // 横を向いている(Roll=±90°)時にエレベーターを引くと、ワールド上下(Pitch)ではなく
                // ワールド水平(Yaw)の急旋回へ正しく変換され、旋回半径が縮小する
                float cosRoll = (float) Math.cos(rollRad);
                float sinRoll = (float) Math.sin(rollRad);
                float cosPitchSafe = Math.max(0.15f, Math.abs((float) Math.cos(pitchRad)));

                float deltaPitch = localPitchRate * cosRoll - localYawRate * sinRoll;
                float deltaYaw = (localPitchRate * sinRoll + localYawRate * cosRoll) / cosPitchSafe;
                float deltaRoll = localRollRate;

                this.pitchAngle = Mth.wrapDegrees(this.pitchAngle + deltaPitch);
                this.setYRot(this.getYRot() + deltaYaw);
                this.rollAngle = Mth.wrapDegrees(this.rollAngle + deltaRoll);

                if (this.isStalling) {
                    // アクロバット失速: 揚力喪失により重力落下、急降下で速度が自然回復
                    this.verticalVelocity = Math.max(-0.75, this.verticalVelocity - 0.055);
                    this.airspeed = Math.min(this.airspeed + 0.010f, maxSpeedBlocks * 0.6f);
                } else {
                    float liftFactor = Mth.clamp(this.airspeed / 0.22f, 0.0f, 1.0f);
                    double lift = 0.045 * liftFactor * Math.max(0.0, Math.cos(pitchRad) * Math.cos(rollRad));
                    double forwardY = Math.sin(pitchRad) * this.airspeed * Math.max(0.2, Math.cos(rollRad));

                    this.verticalVelocity = forwardY + lift - 0.045;
                }
            }
        }

        // 4. 運動ベクトル計算（前進・昇降）
        float yawRad = (float) Math.toRadians(this.getYRot());
        float pitchRad = (float) Math.toRadians(this.pitchAngle);

        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double mx = fwdX * this.airspeed;
        double my = this.verticalVelocity;
        double mz = fwdZ * this.airspeed;

        this.setDeltaMovement(mx, my, mz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 接地衝撃パーティクル
        if (this.onGround() && !onGround && Math.abs(my) > 0.4) {
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY(), getZ(), 6, 0.2, 0.1, 0.2, 0.02);
            }
        }

        // 車輪回転
        if (onGround && this.airspeed > 0.01f) {
            this.wheelRotation = (this.wheelRotation + this.airspeed * 45.0f) % 360.0f;
        }

        // バッテリー消費
        if (!level().isClientSide && hasPower) {
            if (this.throttle > 0.1f && tickCount % (int) Math.max(10, 40 - (this.throttle * 25)) == 0) {
                consumeBattery(1);
            }
        }

        if (!level().isClientSide) {
            entityData.set(DATA_ELEVATOR_INPUT, this.inputElevator);
            entityData.set(DATA_AILERON_INPUT, this.inputAileron);
            entityData.set(DATA_RUDDER_INPUT, this.inputRudder);
        }
    }

    public void consumeBattery(int amount) {
        if (!this.installedBattery.isEmpty() && this.installedBattery.getItem() instanceof DroneBatteryItem bat) {
            bat.consumeFlightDistance(this.installedBattery, amount);
            updateBatterySyncData();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 0. ハサミ (Items.SHEARS) でステッカー全剥がし
        if (held.is(net.minecraft.world.item.Items.SHEARS)) {
            if (!this.getDecals().isEmpty()) {
                this.clearDecals();
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.car_decal_cleared"));
                return InteractionResult.SUCCESS;
            }
        }

        // 1. スニーク右クリック: 機体回収
        if (player.isShiftKeyDown() && held.isEmpty()) {
            ItemStack dropStack = new ItemStack(ModItems.RC_PLANE_ITEM.get());
            if (!"default".equalsIgnoreCase(getVariantId())) {
                net.minecraft.world.item.component.CustomData.update(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        dropStack,
                        tag -> tag.putString("Variant", getVariantId())
                );
                var config = com.microdrone.mod.config.plane.PlaneConfigManager.getConfig(getVariantId());
                if (config != null && config.displayName != null) {
                    dropStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(config.displayName));
                }
            }
            if (!this.decals.isEmpty()) {
                net.minecraft.world.item.component.CustomData.update(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        dropStack,
                        tag -> tag.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals))
                );
            }
            if (!player.getInventory().add(dropStack)) {
                level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), dropStack));
            }
            if (!this.installedBattery.isEmpty()) {
                level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 0.2, getZ(), this.installedBattery));
                this.installedBattery = ItemStack.EMPTY;
            }
            if (!this.installedMotor.isEmpty()) {
                level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 0.2, getZ(), this.installedMotor));
                this.installedMotor = ItemStack.EMPTY;
            }

            level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            this.discard();
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

        RcVehicleManager.registerPiloting(this.pilotUUID, this);
        PacketDistributor.sendToPlayer(player, new com.microdrone.mod.network.DronePilotStatePayload(this.getId(), true, startX, startY, startZ));
        level().playSound(null, blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f);
        return true;
    }

    @Override
    public void stopPiloting(ServerPlayer player) {
        if (this.pilotUUID == null) return;
        UUID oldPilotUUID = this.pilotUUID;
        this.pilotUUID = null;

        RcVehicleManager.unregisterPiloting(oldPilotUUID);

        if (player != null) {
            PacketDistributor.sendToPlayer(player, new com.microdrone.mod.network.DronePilotStatePayload(this.getId(), false, startX, startY, startZ));
        }

        this.inputElevator = 0.0f;
        this.inputAileron = 0.0f;
        this.inputRudder = 0.0f;
        this.inputThrottleDelta = 0.0f;
    }

    public void setLocallyControlled(boolean local) {
        this.locallyControlled = local;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("InstalledBattery")) {
            this.installedBattery = ItemStack.parseOptional(this.registryAccess(), tag.getCompound("InstalledBattery"));
        }
        if (tag.contains("InstalledMotor")) {
            this.installedMotor = ItemStack.parseOptional(this.registryAccess(), tag.getCompound("InstalledMotor"));
        }
        updateBatterySyncData();
        if (!this.installedMotor.isEmpty() && this.installedMotor.getItem() instanceof DroneMotorItem motor) {
            entityData.set(DATA_MOTOR_TIER, motor.getTier());
            entityData.set(DATA_INSTALLED_MOTOR, this.installedMotor.copy());
        }
        this.throttle = tag.getFloat("Throttle");
        this.airspeed = tag.getFloat("Airspeed");
        this.flightMode = tag.getInt("FlightMode");
        if (tag.contains("Decals", 10)) {
            setDecals(com.microdrone.mod.decal.CarDecalData.deserializeDecals(tag.getCompound("Decals")));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!this.installedBattery.isEmpty()) {
            tag.put("InstalledBattery", this.installedBattery.save(this.registryAccess()));
        }
        if (!this.installedMotor.isEmpty()) {
            tag.put("InstalledMotor", this.installedMotor.save(this.registryAccess()));
        }
        tag.putFloat("Throttle", this.throttle);
        tag.putFloat("Airspeed", this.airspeed);
        tag.putInt("FlightMode", this.flightMode);
        if (!this.decals.isEmpty()) {
            tag.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    // === デカール (ステッカー) 管理 ===
    @Override
    public java.util.List<com.microdrone.mod.decal.CarDecalData> getDecals() {
        if (this.level().isClientSide) {
            CompoundTag syncTag = this.entityData.get(DATA_DECALS);
            java.util.List<com.microdrone.mod.decal.CarDecalData> list = com.microdrone.mod.decal.CarDecalData.deserializeDecals(syncTag);
            if (!list.isEmpty()) {
                this.decals.clear();
                this.decals.addAll(list);
            }
            return list;
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
