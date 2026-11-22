package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.DroneBatteryItem;
import com.microdrone.mod.item.DroneMotorItem;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.DronePilotStatePayload;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DroneEntity extends Entity implements GeoEntity, RcControllable, com.microdrone.mod.decal.RcDecalHolder, com.microdrone.mod.sync.ISnapshotInterpolated {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // === マルチプレイ同期用 スナップショット補間バッファ（第1段階） ===
    private final com.microdrone.mod.sync.VehicleSnapshotBuffer snapshotBuffer = new com.microdrone.mod.sync.VehicleSnapshotBuffer();

    @Override
    public com.microdrone.mod.sync.VehicleSnapshotBuffer getSnapshotBuffer() {
        return this.snapshotBuffer;
    }

    // フェイルセーフ用: 操縦中プレイヤーUUID -> ドローン
    private static final Map<UUID, DroneEntity> ACTIVE_PILOTED_DRONES = new ConcurrentHashMap<>();
    // フェイルセーフ用: 身代わりダミー人形UUID -> ドローン
    private static final Map<UUID, DroneEntity> DUMMY_TO_DRONE = new ConcurrentHashMap<>();

    public static DroneEntity getByPilot(UUID pilotUUID) {
        RcControllable v = RcVehicleManager.getByPilot(pilotUUID);
        return v instanceof DroneEntity drone ? drone : null;
    }

    public static DroneEntity getByDummy(UUID dummyUUID) {
        RcControllable v = RcVehicleManager.getByDummy(dummyUUID);
        return v instanceof DroneEntity drone ? drone : null;
    }

    public UUID getPilotUUID() {
        return this.pilotUUID;
    }

    // === デフォルト飛行制御パラメータ (Tier 1 相当) ===
    public static final double DEFAULT_MAX_HORIZONTAL_SPEED = 0.35;    // 最大水平速度 (約 7 m/s)
    public static final double DEFAULT_ACCELERATION = 0.04;             // 水平加速度
    public static final double HORIZONTAL_DRAG = 0.90;                  // 水平慣性摩擦
    public static final double DEFAULT_MAX_VERTICAL_SPEED = 0.25;      // 最大昇降速度 (約 5 m/s)
    public static final double DEFAULT_VERTICAL_ACCELERATION = 0.035;   // 昇降加速度
    public static final double VERTICAL_DRAG = 0.82;                    // 垂直慣性摩擦 (ホバリング維持)
    public static final float YAW_SENSITIVITY = 1.0f;                   // 旋回感度

    // === バッテリー同期用 EntityData ===
    private static final EntityDataAccessor<CompoundTag> DATA_DECALS =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private final java.util.List<com.microdrone.mod.decal.CarDecalData> decals = new java.util.ArrayList<>();

    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);

    // === モーター同期用 EntityData ===
    private static final EntityDataAccessor<Integer> DATA_MOTOR_TIER =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_MOTOR =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> DATA_VARIANT_ID =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);

    // 装着中のバッテリー
    private ItemStack installedBattery = ItemStack.EMPTY;
    // 装着中のモーター (サーバー側保持用)
    private ItemStack installedMotor = ItemStack.EMPTY;
    // 前後移動距離積算用
    private double accumulatedDistance = 0.0;

    // === 電波距離制限パラメータ ===
    public static final double MAX_SIGNAL_RANGE = 250.0;       // 最大電波距離 (m)
    public static final double WARN_SIGNAL_RANGE = 175.0;      // 減衰・警告開始距離 (70%)
    public static final double CRITICAL_SIGNAL_RANGE = 225.0;  // 限界・危険開始距離 (90%)
    private int outOfRangeTicks = 0;                          // 電波圏外継続tickカウント

    private UUID pilotUUID = null;

    // パターンA: 操縦開始時のプレイヤー初期位置（終了時テレポート復元用）
    private ResourceKey<Level> startDimension = null;
    private double startX = Double.NaN;
    private double startY = Double.NaN;
    private double startZ = Double.NaN;
    private float startYaw = 0;
    private float startPitch = 0;
    private ChunkPos startChunkPos = null;
    private UUID dummyEntityUUID = null;
    private RcPilotDummyEntity dummyEntityRef = null;

    // クライアント側で自プレイヤーが操縦中かどうかのフラグ（カクつき防止・クライアント予測用）
    private boolean locallyControlled = false;

    // 入力値 (-1.0 ~ 1.0)
    private float inputForward = 0.0f;
    private float inputStrafe = 0.0f;
    private float inputVertical = 0.0f;
    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;

    // ホバリング時の微小な浮遊アニメーション用
    private int hoverTicks = 0;

    // 機体のチャンク移動追従用
    private ChunkPos lastChunkPos = null;

    // 撮影モード（Cinematic）同調フラグ
    private int cinematicTicks = 0;
    public void setCinematicActive(boolean active) { this.cinematicTicks = active ? 4 : 0; }
    public boolean isCinematicActive() { return this.cinematicTicks > 0; }

    public DroneEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
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
        builder.define(DATA_VARIANT_ID, "default");
    }

    // === バッテリー関連 ===
    public boolean hasBattery() {
        return this.entityData.get(DATA_HAS_BATTERY);
    }

    public int getBatteryRemaining() {
        return this.entityData.get(DATA_BATTERY_REMAINING);
    }

    public int getBatteryMax() {
        return this.entityData.get(DATA_BATTERY_MAX);
    }

    public String getVariantId() {
        return this.entityData.get(DATA_VARIANT_ID);
    }

    public void setVariantId(String variantId) {
        this.entityData.set(DATA_VARIANT_ID, (variantId != null && !variantId.isBlank()) ? variantId : "default");
    }

    public float getBatteryPercentage() {
        int max = getBatteryMax();
        return max > 0 ? (float) getBatteryRemaining() / (float) max : 0.0f;
    }

    public ItemStack getInstalledBattery() {
        return this.entityData.get(DATA_INSTALLED_BATTERY);
    }

    public void updateBatterySyncData() {
        if (!this.installedBattery.isEmpty() && this.installedBattery.getItem() instanceof DroneBatteryItem batteryItem) {
            this.entityData.set(DATA_HAS_BATTERY, true);
            this.entityData.set(DATA_BATTERY_REMAINING, batteryItem.getRemainingBlocks(this.installedBattery));
            this.entityData.set(DATA_BATTERY_MAX, batteryItem.getMaxFlightBlocks());
            this.entityData.set(DATA_INSTALLED_BATTERY, this.installedBattery.copy());
        } else {
            this.entityData.set(DATA_HAS_BATTERY, false);
            this.entityData.set(DATA_BATTERY_REMAINING, 0);
            this.entityData.set(DATA_BATTERY_MAX, 0);
            this.entityData.set(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);
        }
    }

    // === モーター関連 ===
    public int getMotorTier() {
        return this.entityData.get(DATA_MOTOR_TIER);
    }

    public ItemStack getInstalledMotor() {
        return this.entityData.get(DATA_INSTALLED_MOTOR);
    }

    public void updateMotorSyncData() {
        if (!this.installedMotor.isEmpty() && this.installedMotor.getItem() instanceof DroneMotorItem motorItem) {
            this.entityData.set(DATA_MOTOR_TIER, motorItem.getTier());
            this.entityData.set(DATA_INSTALLED_MOTOR, this.installedMotor.copy());
        } else {
            this.entityData.set(DATA_MOTOR_TIER, 1);
            this.entityData.set(DATA_INSTALLED_MOTOR, ItemStack.EMPTY);
        }
    }

    /**
     * クライアント側・サーバー側共通で現在有効なモーター定義を安全・確実に取得する
     */
    public DroneMotorItem getActiveMotorItem() {
        ItemStack motorStack = getInstalledMotor();
        if (!motorStack.isEmpty() && motorStack.getItem() instanceof DroneMotorItem motorItem) {
            return motorItem;
        }
        int tier = getMotorTier();
        return switch (tier) {
            case 2 -> ModItems.MOTOR_TIER_2.get();
            case 3 -> ModItems.MOTOR_TIER_3.get();
            case 4 -> ModItems.MOTOR_TIER_4.get();
            default -> ModItems.MOTOR_TIER_1.get();
        };
    }

    public double getCurrentMaxSpeed() {
        var config = com.microdrone.mod.config.drone.DroneConfigManager.getConfig(getVariantId());
        return getActiveMotorItem().getMaxHorizontalSpeed() * config.properties.maxSpeedMultiplier;
    }

    public double getCurrentAcceleration() {
        var config = com.microdrone.mod.config.drone.DroneConfigManager.getConfig(getVariantId());
        return getActiveMotorItem().getAcceleration() * config.properties.maxSpeedMultiplier;
    }

    public double getCurrentMaxVerticalSpeed() {
        var config = com.microdrone.mod.config.drone.DroneConfigManager.getConfig(getVariantId());
        return getActiveMotorItem().getMaxVerticalSpeed() * config.properties.verticalSpeedMultiplier;
    }

    public double getCurrentVerticalAcceleration() {
        double accel = getCurrentAcceleration();
        return accel * (DEFAULT_VERTICAL_ACCELERATION / DEFAULT_ACCELERATION);
    }

    public int getMotorColor() {
        return getActiveMotorItem().getMotorColor();
    }

    // === GeckoLib 4 アニメーションコントローラー ===
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "drone_flight_controller", 2, state -> {
            if (this.isPiloted() || !this.onGround()) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("flying"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

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

    public double getStartX() { return this.startX; }
    public double getStartY() { return this.startY; }
    public double getStartZ() { return this.startZ; }

    public void setLocallyControlled(boolean locallyControlled) {
        this.locallyControlled = locallyControlled;
    }

    public boolean isLocallyControlled() {
        if (this.level().isClientSide && com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            return false;
        }
        return this.locallyControlled;
    }

    /**
     * サーバーからの位置同期パケット受信時の処理。
     * 自プレイヤーが操縦している場合は、サーバーからの遅延同期で画面がガクつくのを防ぐため、
     * ズレが極端に大きい場合（壁埋まりテレポートなど）を除き、クライアント側の滑らかな移動を優先する。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            // 高速移動（Tier 4: 19m/s = 約1ブロック/tick）時の通信遅延を考慮した動的閾値
            double maxSpeedPerTick = getCurrentMaxSpeed();
            double dynamicThreshold = Math.max(9.0, Math.pow(maxSpeedPerTick * 12.0, 2));

            if (distSq > dynamicThreshold) {
                super.lerpTo(x, y, z, yRot, xRot, steps);
            }
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    public boolean startPiloting(ServerPlayer player) {
        if (this.pilotUUID != null && !this.pilotUUID.equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.microdrone.already_piloted"));
            return false;
        }

        // バッテリー装着・残量チェック
        if (this.installedBattery.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.microdrone.no_battery"));
            return false;
        }
        if (this.getBatteryRemaining() <= 0) {
            player.sendSystemMessage(Component.translatable("message.microdrone.battery_depleted"));
            return false;
        }

        this.pilotUUID = player.getUUID();

        // 操縦開始時の初期位置を記憶
        ServerLevel serverLevel = player.serverLevel();
        this.startDimension = serverLevel.dimension();
        this.startX = player.getX();
        this.startY = player.getY();
        this.startZ = player.getZ();
        this.startYaw = player.getYRot();
        this.startPitch = player.getXRot();
        this.startChunkPos = new ChunkPos(player.blockPosition());

        // 1. 初期位置チャンクの強制ロード保護
        serverLevel.setChunkForced(this.startChunkPos.x, this.startChunkPos.z, true);
        MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_BODY_PROTECT] Protected player start chunk: [{}, {}]",
                this.startChunkPos.x, this.startChunkPos.z);

        // 2. 操縦者プレイヤーの身代わりクローン（RcPilotDummyEntity）の配置
        if (com.microdrone.mod.cinematic.CinematicRecordingManager.isSpawnDummy()) {
            RcPilotDummyEntity dummy = new RcPilotDummyEntity(ModEntities.RC_PILOT_DUMMY.get(), serverLevel);
            dummy.moveTo(this.startX, this.startY, this.startZ, this.startYaw, this.startPitch);
            dummy.setupFromPlayer(player);
            serverLevel.addFreshEntity(dummy);
            this.dummyEntityUUID = dummy.getUUID();
            this.dummyEntityRef = dummy;
            DUMMY_TO_DRONE.put(dummy.getUUID(), this);
            RcVehicleManager.registerDummy(dummy.getUUID(), this);
            MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_SPAWN] Spawned body dummy {} at [{}, {}, {}]",
                    this.dummyEntityUUID,
                    String.format("%.2f", this.startX), String.format("%.2f", this.startY), String.format("%.2f", this.startZ));
        }
        ACTIVE_PILOTED_DRONES.put(player.getUUID(), this);
        RcVehicleManager.registerPiloting(player.getUUID(), this);

        MicroDroneMod.LOGGER.info("[SERVER] [PILOT_START] Pilot: {} ({}), PlayerPos: [{}, {}, {}], DronePos: [{}, {}, {}], DroneChunk: [{}, {}]",
                player.getName().getString(), player.getUUID(),
                String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()),
                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                this.chunkPosition().x, this.chunkPosition().z);

        PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), true, this.startX, this.startY, this.startZ));
        player.sendSystemMessage(Component.translatable("message.microdrone.pilot_start"));

        // 操縦開始時にドローン周辺チャンクをロード・配信
        this.lastChunkPos = new ChunkPos(this.blockPosition());
        com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(player, this);

        return true;
    }

    public void stopPiloting(ServerPlayer player) {
        if (this.pilotUUID != null && this.pilotUUID.equals(player.getUUID())) {
            MicroDroneMod.LOGGER.info("[SERVER] [PILOT_STOP] Pilot: {} ({}), PlayerPos: [{}, {}, {}], DronePos: [{}, {}, {}], DroneChunk: [{}, {}]",
                    player.getName().getString(), player.getUUID(),
                    String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()),
                    String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                    this.chunkPosition().x, this.chunkPosition().z);

            ServerLevel serverLevel = player.serverLevel();

            UUID targetDummyUUID = this.dummyEntityUUID;
            RcPilotDummyEntity targetDummyRef = this.dummyEntityRef;
            this.dummyEntityRef = null;
            this.dummyEntityUUID = null;

            if (targetDummyUUID != null) {
                DUMMY_TO_DRONE.remove(targetDummyUUID);
                RcVehicleManager.unregisterDummy(targetDummyUUID);
            }

            // 1. テレポート前: 直接参照およびエンティティ検索による身代わりダミーの消去試行
            if (targetDummyRef != null) {
                targetDummyRef.discard();
                MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_REMOVE] Discarded dummy via direct reference: {}", targetDummyUUID);
            }
            if (targetDummyUUID != null) {
                Entity dummy = serverLevel.getEntity(targetDummyUUID);
                if (dummy != null) {
                    dummy.discard();
                    MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_REMOVE] Discarded dummy via level entity search: {}", targetDummyUUID);
                }
            }

            // 2. ドローン周辺チャンクの解放
            this.lastChunkPos = null;
            com.microdrone.mod.chunk.DroneChunkManager.releaseDroneChunks(player, this);

            // 3. サーバー側のカメラをプレイヤー自身に復元
            player.setCamera(player);
            player.setInvisible(false);

            // 4. プレイヤーの実体を記録した初期位置へテレポート復元
            double restoreX = this.startX;
            double restoreY = this.startY;
            double restoreZ = this.startZ;
            ChunkPos restoreChunkPos = this.startChunkPos;
            this.startChunkPos = null;

            boolean hasValidStartPos = !Double.isNaN(this.startX) &&
                    !(Math.abs(this.startX) < 0.001 && Math.abs(this.startY) < 0.001 && Math.abs(this.startZ) < 0.001);

            if (this.startDimension != null && player.getServer() != null && hasValidStartPos) {
                ServerLevel targetLevel = player.getServer().getLevel(this.startDimension);
                if (targetLevel != null) {
                    player.teleportTo(targetLevel, this.startX, this.startY, this.startZ, this.startYaw, this.startPitch);
                    MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_RESTORE] Teleported player {} back to start pos: [{}, {}, {}]",
                            player.getName().getString(),
                            String.format("%.2f", this.startX), String.format("%.2f", this.startY), String.format("%.2f", this.startZ));

                    // 5. テレポート後ダミー確実消去フェイルセーフ
                    if (targetDummyUUID != null) {
                        Entity dummyAfter = targetLevel.getEntity(targetDummyUUID);
                        if (dummyAfter != null) {
                            dummyAfter.discard();
                            MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_REMOVE_POST_TP] Discarded dummy by UUID after teleport: {}", targetDummyUUID);
                        }
                    }

                    AABB clearBox = new AABB(restoreX - 1.5, restoreY - 1.0, restoreZ - 1.5,
                                             restoreX + 1.5, restoreY + 2.5, restoreZ + 1.5);
                    List<RcPilotDummyEntity> nearbyStands = targetLevel.getEntitiesOfClass(RcPilotDummyEntity.class, clearBox,
                            stand -> (targetDummyUUID != null && targetDummyUUID.equals(stand.getUUID()))
                                  || (stand.getCustomName() != null && stand.getCustomName().getString().contains("[操縦中]")));
                    for (RcPilotDummyEntity stand : nearbyStands) {
                        stand.discard();
                        MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_CLEANUP] Discarded remaining dummy stand: {}", stand.getUUID());
                    }
                }
                this.startDimension = null;
            } else if (!hasValidStartPos) {
                MicroDroneMod.LOGGER.warn("[SERVER] [DRONE] Invalid or origin start position ({}, {}, {}) on stopPiloting. Retaining player position at {}",
                        this.startX, this.startY, this.startZ, player.position());
                this.startDimension = null;
            }

            // 6. 初期位置チャンクの保護解除
            if (restoreChunkPos != null) {
                serverLevel.setChunkForced(restoreChunkPos.x, restoreChunkPos.z, false);
                MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_BODY_UNPROTECT] Unprotected player start chunk: [{}, {}]",
                        restoreChunkPos.x, restoreChunkPos.z);
            }

            ACTIVE_PILOTED_DRONES.remove(player.getUUID());
            RcVehicleManager.unregisterPiloting(player.getUUID());
            this.pilotUUID = null;
            this.inputForward = 0.0f;
            this.inputStrafe = 0.0f;
            this.inputVertical = 0.0f;
            this.targetYaw = 0.0f;
            this.targetPitch = 0.0f;

            double sendX = hasValidStartPos ? this.startX : player.getX();
            double sendY = hasValidStartPos ? this.startY : player.getY();
            double sendZ = hasValidStartPos ? this.startZ : player.getZ();
            PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), false, sendX, sendY, sendZ));

            this.startX = Double.NaN;
            this.startY = Double.NaN;
            this.startZ = Double.NaN;
            player.sendSystemMessage(Component.translatable("message.microdrone.pilot_stop"));
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        if (this.isPilotedBy(player)) {
            return true;
        }
        return super.broadcastToPlayer(player);
    }

    public void applyControlInput(float forward, float strafe, float vertical, float yaw, float pitch) {
        this.inputForward = Math.max(-1.0f, Math.min(1.0f, forward));
        this.inputStrafe = Math.max(-1.0f, Math.min(1.0f, strafe));
        this.inputVertical = Math.max(-1.0f, Math.min(1.0f, vertical));
        this.targetYaw = yaw;
        this.targetPitch = pitch;
    }

    @Override
    public void tick() {
        super.tick();
        this.hoverTicks++;

        if (!this.level().isClientSide) {
            // === サーバー側 ===
            if (this.cinematicTicks > 0) {
                this.cinematicTicks--;
            }
            if (this.pilotUUID != null) {
                ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
                if (pilot == null || !pilot.isAlive() || pilot.level() != this.level()) {
                    this.pilotUUID = null;
                }
            }

            if (this.isPiloted() || isCinematicActive()) {
                tickFlightPhysics();
            } else {
                tickUnpilotedPhysics();
            }

            // マルチプレイ同期: Snapshot Interpolation用パケットを周囲のプレイヤーへ配信（第1段階）
            boolean isMoving = this.isPiloted() || this.getDeltaMovement().lengthSqr() > 0.0001;
            if (isMoving || (this.tickCount % 4 == 0)) {
                PacketDistributor.sendToPlayersTrackingEntity(this, new com.microdrone.mod.network.RcVehicleSnapshotPayload(
                        this.getId(),
                        this.tickCount,
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        this.getYRot(),
                        this.getXRot(),
                        0.0f,
                        (float) this.getDeltaMovement().x,
                        (float) this.getDeltaMovement().y,
                        (float) this.getDeltaMovement().z
                ));
            }
        } else {
            // === クライアント側 ===
            if (this.locallyControlled && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
                tickFlightPhysics();
            } else if (!this.isNoGravity() && !this.onGround() && !com.microdrone.mod.client.ReplayHelper.isReplayActive()) {
                tickUnpilotedPhysics();
            }
        }
    }

    /**
     * 操縦中の飛行物理計算（サーバー＆クライアントローカル共通）
     */
    private void tickFlightPhysics() {
        if (!this.level().isClientSide) {
            this.setYRot(this.targetYaw);
            this.setYHeadRot(this.targetYaw);
            this.setXRot(Mth.clamp(this.targetPitch, -85.0F, 85.0F));
        }

        Vec3 currentVel = this.getDeltaMovement();

        // モーター性能値の取得
        double maxHSpd = getCurrentMaxSpeed();
        double accel = getCurrentAcceleration();
        double maxVSpd = getCurrentMaxVerticalSpeed();
        double vAccel = getCurrentVerticalAcceleration();

        // 2. 機首方向に応じた目標加速度ベクトルの算出
        float rad = (float) Math.toRadians(this.getYRot());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        double forwardAccelX = -sin * this.inputForward * accel;
        double forwardAccelZ = cos * this.inputForward * accel;

        double strafeAccelX = cos * this.inputStrafe * accel;
        double strafeAccelZ = sin * this.inputStrafe * accel;

        double vx = (currentVel.x + forwardAccelX + strafeAccelX) * HORIZONTAL_DRAG;
        double vz = (currentVel.z + forwardAccelZ + strafeAccelZ) * HORIZONTAL_DRAG;

        double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
        if (horizontalSpeed > maxHSpd) {
            double scale = maxHSpd / horizontalSpeed;
            vx *= scale;
            vz *= scale;
        }

        // 3. 垂直昇降とホバリング高度維持
        double vy = currentVel.y;
        if (Math.abs(this.inputVertical) > 0.05f) {
            vy += this.inputVertical * vAccel;
            vy = Math.max(-maxVSpd, Math.min(maxVSpd, vy));
        } else {
            vy *= VERTICAL_DRAG;
            if (Math.abs(vy) < 0.005) {
                vy = 0.0;
            }
        }

        // 微小なホバリングゆらぎ
        if (Math.abs(this.inputVertical) <= 0.05f && Math.abs(vy) < 0.01) {
            vy = Math.sin(this.hoverTicks * 0.15) * 0.003;
        }

        this.setDeltaMovement(vx, vy, vz);

        // 4. 移動と衝突判定
        this.move(MoverType.SELF, this.getDeltaMovement());

        // 5. 衝突後の安全処理
        Vec3 afterMoveVel = this.getDeltaMovement();
        double fixedVx = afterMoveVel.x;
        double fixedVy = afterMoveVel.y;
        double fixedVz = afterMoveVel.z;

        if (this.horizontalCollision) {
            fixedVx = 0.0;
            fixedVz = 0.0;
            if (horizontalSpeed > 0.15 && !this.level().isClientSide) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.BLOCKS, 0.4F, 1.8F);
            }
        }

        if (this.verticalCollision) {
            fixedVy = 0.0;
        }

        this.setDeltaMovement(fixedVx, fixedVy, fixedVz);
        checkAndResolveSuffocation();

        // サーバー側処理: チャンク更新 (RCカーと同様にプレイヤー本体は開始地点に静止させ、遠距離チャンクのみ配信)
        if (!this.level().isClientSide && this.pilotUUID != null && this.getServer() != null) {
            ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
            if (pilot != null && pilot.isAlive()) {
                ChunkPos currentChunk = new ChunkPos(this.blockPosition());
                boolean chunkChanged = (this.lastChunkPos == null || !this.lastChunkPos.equals(currentChunk));
                if (chunkChanged || (this.hoverTicks % 10 == 0)) {
                    this.lastChunkPos = currentChunk;
                    com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(pilot, this);
                }

                // 3. 電波距離制限と通信途絶フェイルセーフ
                double dx = this.getX() - this.startX;
                double dy = this.getY() - this.startY;
                double dz = this.getZ() - this.startZ;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance > MAX_SIGNAL_RANGE) {
                    this.outOfRangeTicks++;
                    this.inputForward = 0.0f;
                    this.inputStrafe = 0.0f;
                    this.inputVertical = -0.05f;

                    if (this.outOfRangeTicks >= 60 || (this.outOfRangeTicks > 10 && this.onGround())) {
                        MicroDroneMod.LOGGER.warn("[SERVER] [SIGNAL_LOST_FAILSAFE] Drone {} exceeded max range ({}m > {}m). Initiating emergency stop.",
                                this.getUUID(), String.format("%.2f", distance), MAX_SIGNAL_RANGE);
                        pilot.sendSystemMessage(Component.translatable("message.microdrone.signal_lost_failsafe"));
                        this.stopPiloting(pilot);
                        return;
                    }
                } else {
                    this.outOfRangeTicks = 0;
                }

                // 4. 移動距離に応じたバッテリー消費
                double moveDx = this.getX() - this.xOld;
                double moveDz = this.getZ() - this.zOld;
                double moveDist = Math.sqrt(moveDx * moveDx + moveDz * moveDz);
                this.accumulatedDistance += moveDist;

                if (this.accumulatedDistance >= 1.0) {
                    int blocksToConsume = (int) this.accumulatedDistance;
                    this.accumulatedDistance -= blocksToConsume;

                    if (!this.installedBattery.isEmpty() && this.installedBattery.getItem() instanceof DroneBatteryItem batteryItem) {
                        batteryItem.consumeFlightDistance(this.installedBattery, blocksToConsume);
                        updateBatterySyncData();
                    }
                }

                // バッテリー切れフェイルセーフ
                if (!isCinematicActive() && (this.installedBattery.isEmpty() || this.getBatteryRemaining() <= 0)) {
                    this.inputForward = 0.0f;
                    this.inputStrafe = 0.0f;
                    this.inputVertical = -0.04f;

                    if (this.onGround()) {
                        MicroDroneMod.LOGGER.warn("[SERVER] [BATTERY_EMPTY_FAILSAFE] Drone {} ran out of battery. Emergency landing complete.", this.getUUID());
                        pilot.sendSystemMessage(Component.translatable("message.microdrone.battery_empty_failsafe"));
                        this.stopPiloting(pilot);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        MicroDroneMod.LOGGER.info("[SERVER] [ENTITY_REMOVE] DroneUUID: {}, Reason: {}, WasPiloted: {}, PilotUUID: {}",
                this.getUUID(), reason, this.isPiloted(), this.pilotUUID);

        if (this.pilotUUID != null) {
            ACTIVE_PILOTED_DRONES.remove(this.pilotUUID);
            RcVehicleManager.unregisterPiloting(this.pilotUUID);
        }
        if (this.dummyEntityUUID != null) {
            DUMMY_TO_DRONE.remove(this.dummyEntityUUID);
            RcVehicleManager.unregisterDummy(this.dummyEntityUUID);
        }

        if (!this.level().isClientSide && this.pilotUUID != null) {
            ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
            if (pilot != null) {
                this.stopPiloting(pilot);
            }
        }
        super.remove(reason);
    }

    private void checkAndResolveSuffocation() {
        AABB box = this.getBoundingBox();
        if (!this.level().noCollision(this, box)) {
            this.setPos(this.xOld, this.yOld, this.zOld);
            this.setDeltaMovement(0, 0, 0);
        }
    }

    private void tickUnpilotedPhysics() {
        if (!this.isNoGravity()) {
            Vec3 delta = this.getDeltaMovement();
            if (!this.onGround()) {
                this.setDeltaMovement(delta.x * 0.9, Math.max(delta.y - 0.04, -0.5), delta.z * 0.9);
            } else {
                this.setDeltaMovement(delta.x * 0.8, 0.0, delta.z * 0.8);
            }
            this.move(MoverType.SELF, this.getDeltaMovement());
        }
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
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

            // 1. バッテリーアイテム所持時: バッテリー装着
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

            // 2. モーターアイテム所持時: モーター換装 (装着・交換)
            if (heldItem.getItem() instanceof DroneMotorItem motorItem) {
                ItemStack oldMotor = this.installedMotor.copy();
                this.installedMotor = heldItem.split(1);
                updateMotorSyncData();

                if (!oldMotor.isEmpty()) {
                    if (!player.getInventory().add(oldMotor)) {
                        this.spawnAtLocation(oldMotor);
                    }
                }

                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
                player.sendSystemMessage(Component.translatable("message.microdrone.motor_installed",
                        motorItem.getTier(), motorItem.getTierName(), String.format("%.1f", motorItem.getDisplaySpeedMps())));
                return InteractionResult.SUCCESS;
            }

            // 3. コントローラー所持時: 操縦開始 / 終了
            if (heldItem.getItem() instanceof com.microdrone.mod.item.DroneControllerItem) {
                if (player instanceof ServerPlayer serverPlayer) {
                    if (this.isPilotedBy(player)) {
                        this.stopPiloting(serverPlayer);
                    } else {
                        this.startPiloting(serverPlayer);
                    }
                    return InteractionResult.SUCCESS;
                }
            }

            if (this.isPiloted()) {
                return InteractionResult.FAIL;
            }

            // 4. スニーク右クリック時: バッテリー取り外し（未装着ならドローン本体回収）
            if (player.isShiftKeyDown()) {
                if (!this.installedBattery.isEmpty()) {
                    if (!player.getInventory().add(this.installedBattery)) {
                        this.spawnAtLocation(this.installedBattery);
                    }
                    this.installedBattery = ItemStack.EMPTY;
                    updateBatterySyncData();
                    this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
                    player.sendSystemMessage(Component.translatable("message.microdrone.battery_removed"));
                    return InteractionResult.SUCCESS;
                } else {
                    this.dropItemAndDiscard();
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (!this.level().isClientSide && !this.isRemoved()) {
            if (this.pilotUUID != null) {
                ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
                if (pilot != null) {
                    this.stopPiloting(pilot);
                }
            }

            if (damageSource.getEntity() instanceof Player player && player.getAbilities().instabuild) {
                this.discard();
                return true;
            }
            this.dropItemAndDiscard();
            return true;
        }
        return super.hurt(damageSource, amount);
    }

    private void dropItemAndDiscard() {
        if (!this.level().isClientSide) {
            ItemEntity item = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(),
                    new ItemStack(ModItems.DRONE_SPAWN_ITEM.get()));
            if (!"default".equalsIgnoreCase(getVariantId())) {
                net.minecraft.world.item.component.CustomData.update(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        item.getItem(),
                        tag -> tag.putString("Variant", getVariantId())
                );
                var config = com.microdrone.mod.config.drone.DroneConfigManager.getConfig(getVariantId());
                if (config != null && config.displayName != null) {
                    item.getItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(config.displayName));
                }
            }
            if (!this.decals.isEmpty()) {
                net.minecraft.world.item.component.CustomData.update(
                        net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        item.getItem(),
                        tag -> tag.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals))
                );
            }
            this.level().addFreshEntity(item);

            // バッテリーが装着されていれば一緒にドロップ
            if (!this.installedBattery.isEmpty()) {
                ItemEntity batItem = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedBattery);
                this.level().addFreshEntity(batItem);
                this.installedBattery = ItemStack.EMPTY;
            }

            // モーターが装着されていれば一緒にドロップ
            if (!this.installedMotor.isEmpty()) {
                ItemEntity motorItem = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedMotor);
                this.level().addFreshEntity(motorItem);
                this.installedMotor = ItemStack.EMPTY;
            }

            this.discard();
        }
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
            this.installedBattery = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledBattery")).orElse(ItemStack.EMPTY);
            updateBatterySyncData();
        }
        if (compound.contains("InstalledMotor", 10)) {
            this.installedMotor = ItemStack.parse(this.registryAccess(), compound.getCompound("InstalledMotor")).orElse(ItemStack.EMPTY);
            updateMotorSyncData();
        }
        this.accumulatedDistance = compound.getDouble("BatteryAccumulatedDist");
        if (compound.contains("VariantId", 8)) {
            setVariantId(compound.getString("VariantId"));
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
        compound.putDouble("BatteryAccumulatedDist", this.accumulatedDistance);
        compound.putString("VariantId", getVariantId());
        if (!this.decals.isEmpty()) {
            compound.put("Decals", com.microdrone.mod.decal.CarDecalData.serializeDecals(this.decals));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // バニラ標準では小型サイズ(0.5m)に比例して約32mで描画が打ち切られるところを、
        // ラジコン遠隔操縦・目視飛行・他プレイヤーからの視認のため最大電波距離(250m)超まで描画判定を大幅拡張
        double range = Math.max(256.0, this.getBoundingBox().getSize() * 64.0) * getViewScale();
        return distance < range * range;
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