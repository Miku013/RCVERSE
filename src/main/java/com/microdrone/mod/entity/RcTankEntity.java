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
public class RcTankEntity extends Entity implements GeoEntity, RcControllable {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === 同期用 EntityData ===
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

    // 操縦者UUID
    private UUID pilotUUID = null;
    private double startX, startY, startZ;

    // クライアント側ローカル操縦フラグ
    private boolean locallyControlled = false;

    // 実装アイテムスタック保持
    private ItemStack installedBattery = ItemStack.EMPTY;
    private ItemStack installedMotor = ItemStack.EMPTY;

    // 操縦入力（W/S: 前進後退, A/D: 超信地旋回/ステア, Space: 主砲発射）
    public float inputForward = 0.0f;
    public float inputSteer = 0.0f;
    public boolean inputFire = false;
    public float targetTurretYaw = 0.0f;
    public float targetCannonPitch = 0.0f;

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
        return this.locallyControlled;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled) {
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

        builder.define(DATA_LEFT_TRACK_ROT, 0.0f);
        builder.define(DATA_RIGHT_TRACK_ROT, 0.0f);
        builder.define(DATA_TURRET_YAW, 0.0f);
        builder.define(DATA_CANNON_PITCH, 0.0f);
        builder.define(DATA_RECOIL, 0.0f);
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
    public float getTurretYaw() { return entityData.get(DATA_TURRET_YAW); }
    public float getCannonPitch() { return entityData.get(DATA_CANNON_PITCH); }
    public float getRecoil() { return entityData.get(DATA_RECOIL); }

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

        // リコイル反動の減衰
        float currentRecoil = getRecoil();
        if (currentRecoil > 0.01f) {
            float newRecoil = currentRecoil * 0.75f;
            if (newRecoil < 0.01f) newRecoil = 0.0f;
            if (!level().isClientSide) {
                entityData.set(DATA_RECOIL, newRecoil);
            }
        }

        // バッテリー切れフェイルセーフ (Server-side)
        if (!level().isClientSide && isPiloted()) {
            if (!hasBattery() || getBatteryRemaining() <= 0) {
                ServerPlayer player = (ServerPlayer) level().getPlayerByUUID(pilotUUID);
                stopPiloting(player);
                return;
            }
        }

        if (this.locallyControlled || (!level().isClientSide && isPiloted())) {
            tickTankPhysics();
        } else if (!level().isClientSide && !isPiloted()) {
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
        boolean hasPower = hasBattery() && getBatteryRemaining() > 0;
        int tier = getMotorTier();

        // 速度パラメータ（実機ラジコン戦車の重厚感を再現: 従来の約1/3に調整）
        float maxSpeed = 0.06f + (tier * 0.012f);
        float accel = 0.007f + (tier * 0.002f);
        float pivotTurnSpeed = 1.2f + (tier * 0.18f); // 超信地旋回速度 (度/tick)

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

        if (!level().isClientSide) {
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
                fireCooldown = 20; // 1秒間隔
            }
        }
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

        // 迫力の発砲音
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.8f, 1.6f);
        serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.0f, 0.8f);

        // マズルフラッシュ・煙
        serverLevel.sendParticles(ParticleTypes.FLASH, muzzleX, muzzleY, muzzleZ, 1, 0, 0, 0, 0);
        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzleX, muzzleY, muzzleZ, 4, dirX * 0.1, dirY * 0.1, dirZ * 0.1, 0.05);

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

        // 1. スニーク右クリック: 回収
        if (player.isShiftKeyDown() && !isPiloted()) {
            ItemStack dropStack = new ItemStack(ModItems.RC_TANK_ITEM.get());
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
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (!this.installedBattery.isEmpty()) {
            compound.put("InstalledBattery", this.installedBattery.save(this.registryAccess()));
        }
        if (!this.installedMotor.isEmpty()) {
            compound.put("InstalledMotor", this.installedMotor.save(this.registryAccess()));
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        if (!level().isClientSide && !this.isRemoved()) {
            level().playSound(null, blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 1.0f);
            this.discard();
        }
        return true;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
