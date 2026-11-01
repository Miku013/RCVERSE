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
 * - 固定翼航空力学: スロットル推進力、対気速度、揚力、失速(ストール)、急降下重力、滑空
 * - 3軸舵面可動: エレベーター(昇降舵)、エルロン(補助翼)、ラダー(方向舵)
 * - 尾輪式(テールドラッガー)ランディングギア: 前2輪 + 尾輪1輪による地上タキシング＆滑走離着陸
 * - モード切替ハイブリッド: 直感アシスト飛行(自動水平復帰) ⇔ 本格アクロバット(手動ロール・宙返り)
 * - 144Hz超滑らか補間 (lerpToオーバーライド) によるカメラカクつき防止
 */
public class RcPlaneEntity extends Entity implements GeoEntity, RcControllable {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === 同期用 EntityData ===
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
    private static final EntityDataAccessor<Integer> DATA_FLIGHT_MODE =
            SynchedEntityData.defineId(RcPlaneEntity.class, EntityDataSerializers.INT);

    // 姿勢角 (度)
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

    // 累積垂直速度（重力・失速急降下用）
    private double verticalVelocity = 0.0;

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
     * 【ドローン準拠: カメラカクつき防止 lerpTo】
     * 自プレイヤーが操縦している場合は、サーバーからの遅延位置同期パケットによる
     * 画面のガクつき・引き戻しを防止し、クライアント側の滑らかな移動を最優先する。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            // ズレが極端に大きい場合（壁埋まりなど）を除き、クライアント側の滑らかな移動を優先
            if (distSq > 36.0) {
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

        // サーバー側監視（電波距離・チャンク維持・バッテリー）
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
     * 固定翼航空力学・失速急降下・旋回物理シミュレーション
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
        float maxSpeedBlocks = 0.58f + (tier * 0.12f); // 約 42 〜 65 km/h
        float targetAirspeed = this.throttle * maxSpeedBlocks;

        float accelRate = 0.018f + (tier * 0.005f);
        if (this.airspeed < targetAirspeed) {
            this.airspeed = Math.min(this.airspeed + accelRate, targetAirspeed);
        } else {
            this.airspeed = Math.max(this.airspeed - 0.007f, targetAirspeed);
        }

        boolean onGround = this.onGround();

        // 3. 3軸姿勢角の制御
        if (onGround) {
            this.verticalVelocity = 0.0;

            // 地上待機ピッチ(+6度)。エレベーター操作でピョコピョコ上下
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
            // === 【空中飛行状態】 ===
            float stallSpeed = 0.16f; // 失速境界速度 (約 12 km/h)
            boolean isStalling = this.airspeed < stallSpeed;

            // 舵面効果度: 速度があるほど舵が効き、失速時は舵が効かない
            float controlEfficacy = Mth.clamp(this.airspeed / (maxSpeedBlocks * 0.5f), 0.1f, 1.0f);

            if (isStalling) {
                // 【失速物理 (Stall Physics)】:
                // 揚力を完全に失い、機首が重力で下を向く（ノーズダイブ急降下）
                this.pitchAngle = Mth.lerp(0.08f, this.pitchAngle, -32.0f);
                this.rollAngle = Mth.lerp(0.08f, this.rollAngle, 0.0f);

                // 重力による急降下加速（その場に留まらずリアルに墜落）
                this.verticalVelocity = Math.max(-0.75, this.verticalVelocity - 0.065);

                // 急降下によって対気速度が重力から供給される（リカバリーへの道）
                this.airspeed = Math.min(this.airspeed + 0.012f, maxSpeedBlocks * 0.6f);
            } else {
                // 【通常飛行物理】
                if (this.flightMode == 0) {
                    // --- 【直感アシスト飛行モード (ASSIST)】 ---
                    // Sキー(+1.0:引き)で機首上げ(+35度)、Wキー(-1.0:倒し)で機首下げ(-30度)
                    float targetPitch = Mth.clamp(this.inputElevator * 35.0f, -30.0f, 35.0f);
                    this.pitchAngle = Mth.lerp(0.18f * controlEfficacy, this.pitchAngle, targetPitch);

                    // A/D入力による自然なバンク傾斜:
                    // inputAileron: 左(-1.0)で左ロール(-32度)、右(+1.0)で右ロール(+32度)
                    float targetRoll = Mth.clamp(this.inputAileron * 32.0f, -40.0f, 40.0f);
                    this.rollAngle = Mth.lerp(0.18f * controlEfficacy, this.rollAngle, targetRoll);

                    // バンク角とラダーによるスムーズな協調旋回:
                    // 左ロール(負)で左旋回(YRot減少)、右ロール(正)で右旋回(YRot増加)
                    float yawTurn = (this.rollAngle * 0.08f) + (this.inputRudder * 1.8f);
                    this.setYRot(this.getYRot() + yawTurn * controlEfficacy);
                } else {
                    // --- 【本格アクロバットモード (ACRO)】 ---
                    // 完全マニュアル3軸制御（宙返り・バレルロール自由自在）
                    float pitchRate = this.inputElevator * 4.2f * controlEfficacy;
                    this.pitchAngle = (this.pitchAngle + pitchRate) % 360.0f;

                    float rollRate = this.inputAileron * 6.5f * controlEfficacy;
                    this.rollAngle = (this.rollAngle + rollRate) % 360.0f;

                    float yawRate = this.inputRudder * 2.2f * controlEfficacy;
                    this.setYRot(this.getYRot() + yawRate);
                }

                // 通常飛行時の昇降速度計算（揚力と重力のバランス）
                float pitchRad = (float) Math.toRadians(this.pitchAngle);
                float liftFactor = Mth.clamp(this.airspeed / 0.22f, 0.0f, 1.0f);
                double lift = 0.045 * liftFactor * Math.max(0.0, Math.cos(pitchRad));
                double forwardY = Math.sin(pitchRad) * this.airspeed;

                this.verticalVelocity = forwardY + lift - 0.045;
            }
        }

        // 4. 運動ベクトル計算（前進・昇降）
        float yawRad = (float) Math.toRadians(this.getYRot());
        float pitchRad = (float) Math.toRadians(this.pitchAngle);

        // 前進方向ベクトル（Yaw=0は南=+Z、Yaw=90は西=-X）
        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double mx = fwdX * this.airspeed;
        double my = this.verticalVelocity;
        double mz = fwdZ * this.airspeed;

        this.setDeltaMovement(mx, my, mz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 着地判定
        if (this.onGround() && !onGround) {
            // 接地した瞬間に垂直速度リセット
            this.verticalVelocity = 0.0;
            if (Math.abs(my) > 0.4) {
                // ハードランディング・煙パーティクル
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY(), getZ(), 6, 0.2, 0.1, 0.2, 0.02);
                }
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

        // 1. スニーク右クリック: 機体回収
        if (player.isShiftKeyDown() && held.isEmpty()) {
            ItemStack dropStack = new ItemStack(ModItems.RC_PLANE_ITEM.get());
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
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
