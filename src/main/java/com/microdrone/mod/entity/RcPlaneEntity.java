package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.entity.RcControllable;
import com.microdrone.mod.entity.RcVehicleManager;
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
 * - 固定翼航空力学: スロットル推進力、対気速度、揚力、失速(ストール)、滑空
 * - 3軸舵面可動: エレベーター(昇降舵)、エルロン(補助翼)、ラダー(方向舵)
 * - 尾輪式(テールドラッガー)ランディングギア: 前2輪 + 尾輪1輪による地上タキシング＆滑走離着陸
 * - モード切替ハイブリッド: 直感アシスト飛行(自動バンク) ⇔ 本格アクロバット(手動ロール・宙返り)
 * - バッテリー・モーター換装スロット
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
    public float inputElevator = 0.0f; // W/S: ピッチ (-1.0 〜 +1.0)
    public float inputAileron = 0.0f;  // A/D: ロール (-1.0 〜 +1.0)
    public float inputRudder = 0.0f;   // ラダー/ステア (-1.0 〜 +1.0)
    public float inputThrottleDelta = 0.0f; // Space(+)/Shift(-): スロットル増減

    // フライトステート
    public float throttle = 0.0f; // 0.0f 〜 1.0f (スロットル開度)
    public float airspeed = 0.0f; // m/s
    public int flightMode = 0;    // 0: ASSIST (直感アシスト), 1: ACRO (アクロバット)

    public RcPlaneEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    public float maxUpStep() {
        return 0.5f; // 滑走路の段差やハーフブロックを踏破
    }

    @Override
    public boolean isPickable() {
        return true;
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

    // === Getter / Setter ===
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
                // 電波距離監視 (250m)
                double distToStart = Math.sqrt(this.distanceToSqr(this.startX, this.startY, this.startZ));
                if (distToStart > 250.0) {
                    player.sendSystemMessage(Component.translatable("message.microdrone.signal_lost_failsafe"));
                    stopPiloting(player);
                    return;
                }

                // チャンク配信
                ChunkPos currentChunk = new ChunkPos(this.blockPosition());
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

        if (this.locallyControlled || (!level().isClientSide && isPiloted())) {
            tickFlightPhysics();
        } else if (!level().isClientSide && !isPiloted()) {
            // 自然落下重力・滑空沈降
            if (!this.onGround()) {
                Vec3 curDelta = this.getDeltaMovement();
                this.setDeltaMovement(curDelta.x * 0.95, curDelta.y - 0.05, curDelta.z * 0.95);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.pitchAngle = Mth.lerp(0.05f, this.pitchAngle, 15.0f); // 機首を下げて降下
                this.rollAngle = Mth.lerp(0.1f, this.rollAngle, 0.0f);
            } else {
                this.pitchAngle = Mth.lerp(0.2f, this.pitchAngle, 6.0f); // テールドラッガー地上傾斜(+6度)
                this.rollAngle = Mth.lerp(0.2f, this.rollAngle, 0.0f);
            }
        }
    }

    /**
     * 固定翼航空力学・飛行物理シミュレーション
     */
    private void tickFlightPhysics() {
        boolean hasPower = hasBattery() && getBatteryRemaining() > 0;
        int tier = getMotorTier();

        // 1. スロットル調整 (0.0 〜 1.0)
        if (hasPower) {
            this.throttle = Mth.clamp(this.throttle + this.inputThrottleDelta * 0.03f, 0.0f, 1.0f);
        } else {
            this.throttle = Math.max(0.0f, this.throttle - 0.02f);
        }

        // プロペラ回転 (スロットル開度に応じた回転速度)
        float propSpeed = 15.0f + this.throttle * (65.0f + tier * 15.0f);
        this.propRotation = (this.propRotation + propSpeed) % 360.0f;

        // 2. 推進力 (Thrust) と 対気速度 (Airspeed)
        float maxSpeedBlocks = 0.55f + (tier * 0.12f); // 約 40 〜 60 km/h (マイクロRCプレーンのスケール速度)
        float targetAirspeed = this.throttle * maxSpeedBlocks;

        // 加減速
        float accelRate = 0.015f + (tier * 0.005f);
        if (this.airspeed < targetAirspeed) {
            this.airspeed = Math.min(this.airspeed + accelRate, targetAirspeed);
        } else {
            // 空気抵抗による自然減速
            this.airspeed = Math.max(this.airspeed - 0.008f, targetAirspeed);
        }

        // 3. 3軸姿勢角の制御 (ピッチ・ロール・ヨー)
        boolean onGround = this.onGround();

        if (onGround) {
            // === 【地上タキシング＆滑走状態】 ===
            // テールドラッガー地上傾斜姿勢(+6度)。滑走中のエレベーター引きで機首が上下に反応
            float groundPitch = 6.0f + (this.inputElevator * 5.0f);
            this.pitchAngle = Mth.lerp(0.25f, this.pitchAngle, groundPitch);
            this.rollAngle = Mth.lerp(0.25f, this.rollAngle, 0.0f);

            // 地上ステアリング (ラダー/A-D操作で直感的に滑らかにステア)
            // 速度が出ている時ほど自然に旋回
            float turnRate = 2.2f * this.inputRudder * Math.max(0.12f, this.airspeed);
            this.setYRot(this.getYRot() - turnRate);

            // 離陸判定 (Takeoff):
            // スロットルを上げて対気速度が 0.16 以上に達し、かつ Sキー (inputElevator > 0.2f: 引き起こし上昇) でふわりと離陸！
            if (this.airspeed > 0.16f && this.inputElevator > 0.2f) {
                this.setOnGround(false);
                this.setDeltaMovement(this.getDeltaMovement().add(0, 0.18, 0));
                this.pitchAngle = 18.0f; // 離陸初頭の上昇角
            }
        } else {
            // === 【空中飛行状態】 ===
            // 対気速度が足りているほど舵面がよく効く（低速では舵の効きが甘くなるリアルな特性）
            float controlEfficacy = Mth.clamp(this.airspeed / (maxSpeedBlocks * 0.5f), 0.25f, 1.0f);

            if (this.flightMode == 0) {
                // --- 【直感アシスト飛行モード (ASSIST)】 ---
                // Sキー(+1.0: 引き)で機首上げ上昇(+35度)、Wキー(-1.0: 倒し)で機首下げ降下(-30度)
                float targetPitch = Mth.clamp(this.inputElevator * 35.0f, -30.0f, 35.0f);
                this.pitchAngle = Mth.lerp(0.18f * controlEfficacy, this.pitchAngle, targetPitch);

                // A/D入力に応じた自然な自動バンク傾斜 (最大36度)
                float targetRoll = Mth.clamp(-this.inputAileron * 36.0f, -45.0f, 45.0f);
                this.rollAngle = Mth.lerp(0.18f * controlEfficacy, this.rollAngle, targetRoll);

                // バンク角とラダーによるスムーズな協調旋回
                float yawTurn = (this.rollAngle * 0.07f) - (this.inputRudder * 1.5f);
                this.setYRot(this.getYRot() + yawTurn * controlEfficacy);
            } else {
                // --- 【本格アクロバットモード (ACRO)】 ---
                // 完全マニュアル3軸制御（宙返り・バレルロール自由自在）
                float pitchRate = this.inputElevator * 4.2f * controlEfficacy;
                this.pitchAngle = (this.pitchAngle + pitchRate) % 360.0f;

                float rollRate = -this.inputAileron * 6.5f * controlEfficacy;
                this.rollAngle = (this.rollAngle + rollRate) % 360.0f;

                float yawRate = -this.inputRudder * 2.0f * controlEfficacy;
                this.setYRot(this.getYRot() + yawRate);
            }
        }

        // 4. 運動ベクトル計算（3次元空間への投影）
        float yawRad = (float) Math.toRadians(this.getYRot());
        float pitchRad = (float) Math.toRadians(this.pitchAngle);

        // 前進方向ベクトル（機首上げ pitchAngle > 0 で上向き fwdY > 0）
        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdY = Math.sin(pitchRad);
        double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);

        // 揚力 (Lift): 水平対気速度から発生
        double lift = 0.0;
        if (!onGround) {
            float liftFactor = Mth.clamp(this.airspeed / 0.20f, 0.0f, 1.0f);
            lift = 0.045 * liftFactor * Math.max(0.0, Math.cos(pitchRad));
        }

        double gravity = onGround ? 0.0 : -0.045;

        // 速度ベクトル
        double mx = fwdX * this.airspeed;
        double my = (fwdY * this.airspeed) + lift + gravity;
        double mz = fwdZ * this.airspeed;

        this.setDeltaMovement(mx, my, mz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 車輪回転 (地上走行時)
        if (onGround && this.airspeed > 0.01f) {
            this.wheelRotation = (this.wheelRotation + this.airspeed * 45.0f) % 360.0f;
        }

        // バッテリー消費 (スロットル開度に応じて消費)
        if (!level().isClientSide && hasPower) {
            if (this.throttle > 0.1f && tickCount % (int) Math.max(10, 40 - (this.throttle * 25)) == 0) {
                consumeBattery(1);
            }
        }

        // エンティティデータ同期
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

    // === インタラクト処理（バッテリー・モーター換装・回収） ===
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
        // ボーンの回転は GeoModel でダイナミックに制御するためデフォルトコントローラーを登録
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
