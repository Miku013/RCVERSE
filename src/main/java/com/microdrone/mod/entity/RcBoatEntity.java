package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.RcBoatItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;

/**
 * マイクロRCボート（高速パワーボート・スピードボート）
 * 水上浮力物理、ツインスクリュー推進、方向舵ステアリング、プレーニング（滑走滑走）、波しぶきパーティクルをシミュレート
 */
public class RcBoatEntity extends Entity implements GeoEntity, RcControllable, com.microdrone.mod.decal.RcDecalHolder {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // バッテリー & モーター同期データ
    private static final EntityDataAccessor<CompoundTag> DATA_DECALS =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final java.util.List<com.microdrone.mod.decal.CarDecalData> decals = new java.util.ArrayList<>();

    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final EntityDataAccessor<Integer> DATA_MOTOR_TIER =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_MOTOR =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.ITEM_STACK);

    // クライアント描画同期（舵角、ロール傾斜）
    private static final EntityDataAccessor<Float> DATA_RUDDER_ANGLE =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL_ANGLE =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH_ANGLE =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<String> DATA_VARIANT_ID =
            SynchedEntityData.defineId(RcBoatEntity.class, EntityDataSerializers.STRING);

    // 操縦者UUID
    private UUID pilotUUID = null;
    private double startX = Double.NaN, startY = Double.NaN, startZ = Double.NaN;
    private UUID dummyEntityUUID = null;
    private RcPilotDummyEntity dummyEntityRef = null;
    private ChunkPos lastChunkPos = null;

    // 装着中のアイテム
    private ItemStack installedBattery = ItemStack.EMPTY;
    private ItemStack installedMotor = ItemStack.EMPTY;
    private double accumulatedDistance = 0.0;

    // 操作入力（サーバー/クライアント共用）
    public float inputForward = 0.0f;  // 前進(+1.0) / 後退(-0.6)
    public float inputSteer = 0.0f;    // 左ステア(-1.0) / 右ステア(+1.0)

    // 撮影モード（Cinematic）同調フラグ
    private int cinematicTicks = 0;
    public void setCinematicActive(boolean active) { this.cinematicTicks = active ? 4 : 0; }
    public boolean isCinematicActive() { return this.cinematicTicks > 0; }

    // 内部物理パラメータ
    private float currentSpeed = 0.0f;
    private float propellerRotation = 0.0f;
    public float prevPropellerRotation = 0.0f;
    private float rudderAngle = 0.0f;
    public float prevRudderAngle = 0.0f;
    private float rollAngle = 0.0f;
    public float prevRollAngle = 0.0f;
    private float pitchAngle = 0.0f;
    public float prevPitchAngle = 0.0f;

    private boolean locallyControlled = false;
    private int outOfWaterTicks = 0;

    public RcBoatEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            if (dx * dx + dy * dy + dz * dz > 16.0) {
                super.lerpTo(x, y, z, yRot, xRot, steps);
            }
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_DECALS, new CompoundTag());
        builder.define(DATA_HAS_BATTERY, false);
        builder.define(DATA_BATTERY_REMAINING, 0);
        builder.define(DATA_BATTERY_MAX, 0);
        builder.define(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);
        builder.define(DATA_MOTOR_TIER, 1);
        builder.define(DATA_INSTALLED_MOTOR, ItemStack.EMPTY);

        builder.define(DATA_RUDDER_ANGLE, 0.0f);
        builder.define(DATA_ROLL_ANGLE, 0.0f);
        builder.define(DATA_PITCH_ANGLE, 0.0f);
        builder.define(DATA_VARIANT_ID, "default");
    }

    public boolean hasBattery() { return this.entityData.get(DATA_HAS_BATTERY); }
    public int getBatteryRemaining() { return this.entityData.get(DATA_BATTERY_REMAINING); }
    public int getBatteryMax() { return this.entityData.get(DATA_BATTERY_MAX); }
    public String getVariantId() { return this.entityData.get(DATA_VARIANT_ID); }
    public void setVariantId(String variantId) { this.entityData.set(DATA_VARIANT_ID, (variantId != null && !variantId.isBlank()) ? variantId : "default"); }
    public int getMotorTier() { return this.entityData.get(DATA_MOTOR_TIER); }
    public ItemStack getInstalledBattery() { return this.entityData.get(DATA_INSTALLED_BATTERY); }
    public ItemStack getInstalledMotor() { return this.entityData.get(DATA_INSTALLED_MOTOR); }

    public float getRudderAngle() { return this.rudderAngle; }
    public float getRollAngle() { return this.rollAngle; }
    public float getPitchAngle() { return this.pitchAngle; }
    public float getPropellerRotation() { return this.propellerRotation; }

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
    public boolean isPiloted() { return this.pilotUUID != null; }

    @Override
    public boolean isPilotedBy(ServerPlayer player) {
        return this.pilotUUID != null && this.pilotUUID.equals(player.getUUID());
    }

    @Override
    public UUID getPilotUUID() { return this.pilotUUID; }

    @Override
    public boolean startPiloting(ServerPlayer player) {
        if (this.pilotUUID != null) return false;

        this.pilotUUID = player.getUUID();
        this.startX = player.getX();
        this.startY = player.getY();
        this.startZ = player.getZ();

        RcVehicleManager.registerPiloting(player.getUUID(), this);

        // 操縦者プレイヤーの身代わりクローン（RcPilotDummyEntity）の生成
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

        player.setInvisible(true);

        com.microdrone.mod.network.DronePilotStatePayload payload =
                new com.microdrone.mod.network.DronePilotStatePayload(this.getId(), true, this.startX, this.startY, this.startZ);
        PacketDistributor.sendToPlayer(player, payload);
        player.sendSystemMessage(Component.translatable("message.microdrone.boat_pilot_start"));

        this.lastChunkPos = new ChunkPos(this.blockPosition());
        com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(player, this);

        return true;
    }

    @Override
    public void stopPiloting(ServerPlayer player) {
        if (this.pilotUUID != null && this.pilotUUID.equals(player.getUUID())) {
            ServerLevel serverLevel = player.serverLevel();

            if (this.dummyEntityRef != null) {
                this.dummyEntityRef.discard();
                this.dummyEntityRef = null;
            }
            if (this.dummyEntityUUID != null) {
                Entity dummy = serverLevel.getEntity(this.dummyEntityUUID);
                if (dummy != null) dummy.discard();
                RcVehicleManager.unregisterDummy(this.dummyEntityUUID);
                this.dummyEntityUUID = null;
            }

            this.lastChunkPos = null;
            com.microdrone.mod.chunk.DroneChunkManager.releaseDroneChunks(player, this);

            player.setCamera(player);
            player.setInvisible(false);

            boolean hasValidStartPos = !Double.isNaN(this.startX) &&
                    !(Math.abs(this.startX) < 0.001 && Math.abs(this.startY) < 0.001 && Math.abs(this.startZ) < 0.001);
            if (hasValidStartPos) {
                player.teleportTo(serverLevel, this.startX, this.startY, this.startZ, player.getYRot(), player.getXRot());
            } else {
                MicroDroneMod.LOGGER.warn("[SERVER] [RC_BOAT] Invalid start position on stopPiloting. Retaining player position at {}", player.position());
            }

            RcVehicleManager.unregisterPiloting(player.getUUID());
            this.pilotUUID = null;
            this.inputForward = 0.0f;
            this.inputSteer = 0.0f;

            double sendX = hasValidStartPos ? this.startX : player.getX();
            double sendY = hasValidStartPos ? this.startY : player.getY();
            double sendZ = hasValidStartPos ? this.startZ : player.getZ();
            this.startX = Double.NaN;
            this.startY = Double.NaN;
            this.startZ = Double.NaN;

            PacketDistributor.sendToPlayer(player, new com.microdrone.mod.network.DronePilotStatePayload(this.getId(), false, sendX, sendY, sendZ));
            player.sendSystemMessage(Component.translatable("message.microdrone.pilot_stop"));
        }
    }

    @Override
    public void tick() {
        super.tick();

        this.prevPropellerRotation = this.propellerRotation;
        this.prevRudderAngle = this.rudderAngle;
        this.prevRollAngle = this.rollAngle;
        this.prevPitchAngle = this.pitchAngle;

        if (!this.level().isClientSide) {
            if (this.cinematicTicks > 0) {
                this.cinematicTicks--;
            }
            if (this.pilotUUID != null) {
                ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
                if (pilot == null || !pilot.isAlive() || pilot.level() != this.level()) {
                    this.pilotUUID = null;
                }
            }
            tickBoatPhysics();
        } else {
            if (this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
                tickBoatPhysics();
            } else {
                this.rudderAngle = this.entityData.get(DATA_RUDDER_ANGLE);
                this.rollAngle = this.entityData.get(DATA_ROLL_ANGLE);
                this.pitchAngle = this.entityData.get(DATA_PITCH_ANGLE);
            }
        }
    }

    /**
     * 水上浮力シミュレーション＆スクリュー推進物理
     */
    private void tickBoatPhysics() {
        BlockPos pos = this.blockPosition();
        FluidState fluid = this.level().getFluidState(pos);
        boolean inWater = fluid.is(FluidTags.WATER) || this.level().getFluidState(pos.below()).is(FluidTags.WATER);

        double vx = this.getDeltaMovement().x;
        double vy = this.getDeltaMovement().y;
        double vz = this.getDeltaMovement().z;

        // モーター性能値（速度を現在の1/3に調整）
        int tier = getMotorTier();
        double maxForwardSpeed = (0.35 + (tier * 0.12)) / 3.0; // 最高速度 (ブロック/tick)
        double accel = (0.03 + (tier * 0.015)) / 3.0;
        double turnSpeed = 4.8f + (tier * 0.5f);

        if (inWater) {
            this.outOfWaterTicks = 0;

            // === 1. 水上浮力 (Buoyancy) ===
            // 水面高さの検出
            double waterSurfaceY = pos.getY() + fluid.getHeight(this.level(), pos);
            if (!fluid.is(FluidTags.WATER)) {
                FluidState belowFluid = this.level().getFluidState(pos.below());
                if (belowFluid.is(FluidTags.WATER)) {
                    waterSurfaceY = pos.below().getY() + belowFluid.getHeight(this.level(), pos.below());
                }
            }

            double depth = waterSurfaceY - this.getY();
            // 目標喫水深さは約 0.05m（船底がわずかに水中に浸かる）
            double targetDepth = 0.05;
            double depthDiff = depth - targetDepth;

            if (depthDiff > 0) {
                // 浮力で持ち上げ
                vy += Math.min(depthDiff * 0.25, 0.08);
            } else {
                // 水面より高い場合は重力降下
                vy -= 0.03;
            }

            // 垂直方向の減衰（水面での跳ね返りダンピング）
            vy *= 0.65;

            // === 2. スクリュー推力 (Thrust) & ステアリング (Steering) ===
            boolean hasPower = (hasBattery() && getBatteryRemaining() > 0) || isCinematicActive();
            if (!hasPower) {
                this.inputForward = 0.0f;
                this.inputSteer = 0.0f;
            }

            // 加減速
            if (this.inputForward > 0.05f) {
                this.currentSpeed = (float) Math.min(this.currentSpeed + accel, maxForwardSpeed);
            } else if (this.inputForward < -0.05f) {
                this.currentSpeed = (float) Math.max(this.currentSpeed - accel * 0.8, -maxForwardSpeed * 0.5);
            } else {
                this.currentSpeed *= 0.90f; // 水抵抗による減速
            }

            // スクリュー回転アニメーション更新（速度1/3でもリアルな回転速度を維持）
            if (Math.abs(this.currentSpeed) > 0.003f) {
                this.propellerRotation += this.currentSpeed * 320.0f;
            }

            // 舵角（ラダー）の追従
            float targetRudder = -this.inputSteer * 35.0f;
            this.rudderAngle = Mth.rotLerp(0.35f, this.rudderAngle, targetRudder);

            // ステアリング旋回（速度に比例して舵が効く・後退時は舵を切った向きへ後退）
            if (Math.abs(this.currentSpeed) > 0.005f) {
                float steerFactor = Math.min((float) (Math.abs(this.currentSpeed) / maxForwardSpeed), 1.0f);
                float dirSign = (this.currentSpeed >= 0 ? 1.0f : -1.0f);
                float yawChange = (float) (-this.inputSteer * turnSpeed * steerFactor * dirSign);
                this.setYRot(this.getYRot() + yawChange);
            }

            // 水平推進速度ベクトルの算出
            float yawRad = (float) Math.toRadians(this.getYRot());
            vx = -Math.sin(yawRad) * this.currentSpeed;
            vz = Math.cos(yawRad) * this.currentSpeed;

            // 水平横滑り（ドリフト）の減衰
            vx *= 0.90;
            vz *= 0.90;

            // === 3. 船体姿勢（ロール・ピッチ）===
            // 旋回時のインサイドバンク（内傾斜・後退時も旋回方向に追従）
            float dirSign = (this.currentSpeed >= 0 ? 1.0f : -1.0f);
            float targetRoll = this.inputSteer * Math.min((float)(Math.abs(this.currentSpeed) / maxForwardSpeed) * 15.0f, 15.0f) * dirSign;
            this.rollAngle = Mth.rotLerp(0.20f, this.rollAngle, targetRoll);

            // 高速滑走時の船首持ち上がり（プレーニング: Bow Rise ピッチ約 -4度〜-7.5度）
            float targetPitch = (this.currentSpeed > (maxForwardSpeed * 0.25f)) ? (float) (-(this.currentSpeed / maxForwardSpeed) * 7.5f) : 0.0f;
            this.pitchAngle = Mth.rotLerp(0.15f, this.pitchAngle, targetPitch);

            // === 4. 水しぶき・泡パーティクル ===
            if (this.level().isClientSide && Math.abs(this.currentSpeed) > 0.02f) {
                // スクリュー泡（船尾後方）
                double sternX = this.getX() + Math.sin(yawRad) * 0.5;
                double sternZ = this.getZ() - Math.cos(yawRad) * 0.5;
                this.level().addParticle(ParticleTypes.SPLASH, sternX, this.getY() + 0.05, sternZ,
                        (random.nextDouble() - 0.5) * 0.2, 0.15, (random.nextDouble() - 0.5) * 0.2);
                this.level().addParticle(ParticleTypes.BUBBLE, sternX, this.getY(), sternZ,
                        -Math.sin(yawRad) * 0.1, -0.05, Math.cos(yawRad) * 0.1);

                // 船首波しぶき（ウェイク）
                if (Math.abs(this.currentSpeed) > (maxForwardSpeed * 0.6f)) {
                    double bowX = this.getX() - Math.sin(yawRad) * 0.4;
                    double bowZ = this.getZ() + Math.cos(yawRad) * 0.4;
                    this.level().addParticle(ParticleTypes.SPLASH, bowX, this.getY() + 0.08, bowZ,
                            Math.cos(yawRad) * 0.15, 0.1, Math.sin(yawRad) * 0.15);
                }
            }

        } else {
            // 水外（陸地・空中）での挙動: 重力落下＆陸上摩擦
            this.outOfWaterTicks++;
            vy = Math.max(vy - 0.04, -0.8);
            vx *= 0.5;
            vz *= 0.5;
            this.currentSpeed *= 0.8f;
            this.rollAngle = Mth.rotLerp(0.1f, this.rollAngle, 0.0f);
            this.pitchAngle = Mth.rotLerp(0.1f, this.pitchAngle, 0.0f);
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 同期データ更新
        if (!this.level().isClientSide) {
            this.entityData.set(DATA_RUDDER_ANGLE, this.rudderAngle);
            this.entityData.set(DATA_ROLL_ANGLE, this.rollAngle);
            this.entityData.set(DATA_PITCH_ANGLE, this.pitchAngle);

            // サーバー側操縦プレイヤー追従 & チャンク保護
            if (this.pilotUUID != null && this.getServer() != null) {
                ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
                if (pilot != null && pilot.isAlive()) {
                    pilot.absMoveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                    pilot.serverLevel().getChunkSource().move(pilot);

                    ChunkPos currentChunk = new ChunkPos(this.blockPosition());
                    if (this.lastChunkPos == null || !this.lastChunkPos.equals(currentChunk)) {
                        com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(pilot, this);
                        this.lastChunkPos = currentChunk;
                    }

                    // バッテリー消費
                    double moveDist = Math.sqrt(vx * vx + vz * vz);
                    this.accumulatedDistance += moveDist;
                    if (this.accumulatedDistance >= 2.0) {
                        int blocksToConsume = (int) this.accumulatedDistance;
                        this.accumulatedDistance -= blocksToConsume;
                        if (!this.installedBattery.isEmpty() && this.installedBattery.getItem() instanceof DroneBatteryItem batteryItem) {
                            batteryItem.consumeFlightDistance(this.installedBattery, blocksToConsume);
                            updateBatterySyncData();
                        }
                    }
                }
            }
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
            this.entityData.set(DATA_BATTERY_REMAINING, 0);
            this.entityData.set(DATA_BATTERY_MAX, 0);
            this.entityData.set(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            ItemStack heldItem = player.getItemInHand(hand);

            // ハサミでステッカー全剥がし
            if (heldItem.is(net.minecraft.world.item.Items.SHEARS)) {
                if (!this.getDecals().isEmpty()) {
                    this.clearDecals();
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.microdrone.car_decal_cleared"));
                    return InteractionResult.SUCCESS;
                }
            }

            if (heldItem.getItem() instanceof DroneBatteryItem) {
                if (!this.installedBattery.isEmpty()) {
                    ItemStack old = this.installedBattery.copy();
                    this.installedBattery = heldItem.split(1);
                    if (!player.getInventory().add(old)) player.drop(old, false);
                } else {
                    this.installedBattery = heldItem.split(1);
                }
                updateBatterySyncData();
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.8F, 1.2F);
                return InteractionResult.SUCCESS;
            }

            if (heldItem.getItem() instanceof DroneMotorItem motorItem) {
                if (!this.installedMotor.isEmpty()) {
                    ItemStack old = this.installedMotor.copy();
                    this.installedMotor = heldItem.split(1);
                    if (!player.getInventory().add(old)) player.drop(old, false);
                } else {
                    this.installedMotor = heldItem.split(1);
                }
                this.entityData.set(DATA_MOTOR_TIER, motorItem.getTier());
                this.entityData.set(DATA_INSTALLED_MOTOR, this.installedMotor.copy());
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
                return InteractionResult.SUCCESS;
            }

            // スニーク右クリックでアイテム回収
            if (player.isShiftKeyDown() && !isPiloted()) {
                ItemStack drop = new ItemStack(com.microdrone.mod.item.ModItems.RC_BOAT_ITEM.get());
                if (!"default".equalsIgnoreCase(getVariantId())) {
                    net.minecraft.world.item.component.CustomData.update(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            drop,
                            tag -> tag.putString("Variant", getVariantId())
                    );
                    var config = com.microdrone.mod.config.boat.BoatConfigManager.getConfig(getVariantId());
                    if (config != null && config.displayName != null) {
                        drop.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(config.displayName));
                    }
                }
                if (!this.decals.isEmpty()) {
                    net.minecraft.world.item.component.CustomData.update(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            drop,
                            tag -> tag.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals))
                    );
                }
                if (!player.getInventory().add(drop)) player.drop(drop, false);
                if (!this.installedBattery.isEmpty()) player.drop(this.installedBattery, false);
                if (!this.installedMotor.isEmpty()) player.drop(this.installedMotor, false);
                this.discard();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (source.getEntity() instanceof Player player) {
                if (!player.getAbilities().instabuild) {
                    ItemStack drop = new ItemStack(com.microdrone.mod.item.ModItems.RC_BOAT_ITEM.get());
                if (!"default".equalsIgnoreCase(getVariantId())) {
                    net.minecraft.world.item.component.CustomData.update(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            drop,
                            tag -> tag.putString("Variant", getVariantId())
                    );
                    var config = com.microdrone.mod.config.boat.BoatConfigManager.getConfig(getVariantId());
                    if (config != null && config.displayName != null) {
                        drop.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(config.displayName));
                    }
                }
                if (!this.decals.isEmpty()) {
                    net.minecraft.world.item.component.CustomData.update(
                            net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            drop,
                            tag -> tag.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals))
                    );
                }
                    this.spawnAtLocation(drop);
                    if (!this.installedBattery.isEmpty()) this.spawnAtLocation(this.installedBattery);
                    if (!this.installedMotor.isEmpty()) this.spawnAtLocation(this.installedMotor);
                }
                this.discard();
                return true;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isPickable() { return !this.isRemoved(); }

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
        if (compound.contains("InstalledBattery")) {
            this.installedBattery = ItemStack.parseOptional(this.registryAccess(), compound.getCompound("InstalledBattery"));
            updateBatterySyncData();
        }
        if (compound.contains("InstalledMotor")) {
            this.installedMotor = ItemStack.parseOptional(this.registryAccess(), compound.getCompound("InstalledMotor"));
            if (this.installedMotor.getItem() instanceof DroneMotorItem motorItem) {
                this.entityData.set(DATA_MOTOR_TIER, motorItem.getTier());
            }
            this.entityData.set(DATA_INSTALLED_MOTOR, this.installedMotor.copy());
        }
        if (compound.contains("Decals", 10)) {
            setDecals(com.microdrone.mod.decal.CarDecalData.deserializeDecals(compound.getCompound("Decals")));
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
        if (!this.decals.isEmpty()) {
            compound.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // ボーン可動はジオモデルのボーン直接操作で行う
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
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
