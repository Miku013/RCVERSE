package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.DronePilotStatePayload;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import com.microdrone.mod.item.DroneBatteryItem;
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

public class DroneEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // フェイルセーフ用: 操縦中プレイヤーUUID -> ドローン
    private static final Map<UUID, DroneEntity> ACTIVE_PILOTED_DRONES = new ConcurrentHashMap<>();
    // フェイルセーフ用: 身代わりダミー人形UUID -> ドローン
    private static final Map<UUID, DroneEntity> DUMMY_TO_DRONE = new ConcurrentHashMap<>();

    public static DroneEntity getByPilot(UUID pilotUUID) {
        return pilotUUID != null ? ACTIVE_PILOTED_DRONES.get(pilotUUID) : null;
    }

    public static DroneEntity getByDummy(UUID dummyUUID) {
        return dummyUUID != null ? DUMMY_TO_DRONE.get(dummyUUID) : null;
    }

    public UUID getPilotUUID() {
        return this.pilotUUID;
    }

    // === 飛行制御パラメータ (Phase 3) ===
    public static final double MAX_HORIZONTAL_SPEED = 0.35;    // 最大水平速度 (約 7 m/s)
    public static final double ACCELERATION = 0.04;             // 水平加速度
    public static final double HORIZONTAL_DRAG = 0.90;          // 水平慣性摩擦
    public static final double MAX_VERTICAL_SPEED = 0.25;      // 最大昇降速度 (約 5 m/s)
    public static final double VERTICAL_ACCELERATION = 0.035;   // 昇降加速度
    public static final double VERTICAL_DRAG = 0.82;            // 垂直慣性摩擦 (ホバリング維持)
    public static final float YAW_SENSITIVITY = 1.0f;           // 旋回感度

    // === 電波距離制限パラメータ (Phase 4 / Step 4) ===
    // === バッテリー同期用 EntityData (Step 5) ===
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_REMAINING =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BATTERY_MAX =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DATA_INSTALLED_BATTERY =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.ITEM_STACK);

    // 装着中のバッテリー
    private ItemStack installedBattery = ItemStack.EMPTY;
    // 前後移動距離積算用
    private double accumulatedDistance = 0.0;

    public static final double MAX_SIGNAL_RANGE = 250.0;       // 最大電波距離 (m)
    public static final double WARN_SIGNAL_RANGE = 175.0;      // 減衰・警告開始距離 (70%)
    public static final double CRITICAL_SIGNAL_RANGE = 225.0;  // 限界・危険開始距離 (90%)
    private int outOfRangeTicks = 0;                          // 電波圏外継続tickカウント

    private UUID pilotUUID = null;

    // パターンA: 操縦開始時のプレイヤー初期位置（終了時テレポート復元用）
    private ResourceKey<Level> startDimension = null;
    private double startX = 0;
    private double startY = 0;
    private double startZ = 0;
    private float startYaw = 0;
    private float startPitch = 0;
    private ChunkPos startChunkPos = null;
    private UUID dummyEntityUUID = null;
    private ArmorStand dummyEntityRef = null;

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

    // Phase 5: 機体のチャンク移動追従用
    private ChunkPos lastChunkPos = null;

    public DroneEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HAS_BATTERY, false);
        builder.define(DATA_BATTERY_REMAINING, 0);
        builder.define(DATA_BATTERY_MAX, 0);
        builder.define(DATA_INSTALLED_BATTERY, ItemStack.EMPTY);
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
        return this.locallyControlled;
    }

    /**
     * サーバーからの位置同期パケット受信時の処理。
     * 自プレイヤーが操縦している場合は、サーバーからの遅延同期で画面がガクつくのを防ぐため、
     * ズレが極端に大きい場合（壁埋まりテレポートなど）を除き、クライアント側の滑らかな移動を優先する。
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (this.locallyControlled) {
            // サーバー位置との距離をチェック
            double dx = x - this.getX();
            double dy = y - this.getY();
            double dz = z - this.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq > 9.0) { // 3ブロック以上の大きなズレのみ補正
                super.lerpTo(x, y, z, yRot, xRot, steps);
            }
            // 微小なズレの場合はクライアント側の予測物理を維持し、カクつきを完全防止
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps);
    }

    public boolean startPiloting(ServerPlayer player) {
        if (this.pilotUUID != null && !this.pilotUUID.equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.microdrone.already_piloted"));
            return false;
        }

        // バッテリー装着・残量チェック (Step 5)
        if (this.installedBattery.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.microdrone.no_battery"));
            return false;
        }
        if (this.getBatteryRemaining() <= 0) {
            player.sendSystemMessage(Component.translatable("message.microdrone.battery_depleted"));
            return false;
        }

        this.pilotUUID = player.getUUID();

        // パターンA: 操縦開始時の初期位置を記憶
        ServerLevel serverLevel = player.serverLevel();
        this.startDimension = serverLevel.dimension();
        this.startX = player.getX();
        this.startY = player.getY();
        this.startZ = player.getZ();
        this.startYaw = player.getYRot();
        this.startPitch = player.getXRot();
        this.startChunkPos = new ChunkPos(player.blockPosition());

        // 1. 初期位置チャンクの強制ロード保護（プレイヤーの身体周辺がアンロードされるのを防止）
        serverLevel.setChunkForced(this.startChunkPos.x, this.startChunkPos.z, true);
        MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_BODY_PROTECT] Protected player start chunk: [{}, {}]",
                this.startChunkPos.x, this.startChunkPos.z);

        // 2. 身代わりダミー人形（ArmorStand）の配置
        ArmorStand dummy = new ArmorStand(serverLevel, this.startX, this.startY, this.startZ);
        dummy.setYRot(this.startYaw);
        dummy.setXRot(this.startPitch);
        dummy.setCustomName(Component.literal("§e[操縦中] §r" + player.getName().getString()));
        dummy.setCustomNameVisible(true);
        dummy.setNoGravity(true);
        dummy.setInvulnerable(false); // 被弾フェイルセーフを検知可能にする（ダメージイベント側で破壊防止キャンセル）
        dummy.setShowArms(true);      // 腕を表示（コントローラーを持たせる）
        dummy.setNoBasePlate(true);    // 台座を非表示（自然な足元に）

        // コントローラーを操作している自然なポーズ（両腕を前方に構えてコントローラーを見る）
        dummy.setRightArmPose(new Rotations(-25.0f, -15.0f, 0.0f));
        dummy.setLeftArmPose(new Rotations(-25.0f, 15.0f, 0.0f));
        dummy.setHeadPose(new Rotations(12.0f, 0.0f, 0.0f)); // やや下（手元のコントローラー）を向く

        // プレイヤーの防具をすべてコピー装着
        dummy.setItemSlot(EquipmentSlot.FEET, player.getItemBySlot(EquipmentSlot.FEET).copy());
        dummy.setItemSlot(EquipmentSlot.LEGS, player.getItemBySlot(EquipmentSlot.LEGS).copy());
        dummy.setItemSlot(EquipmentSlot.CHEST, player.getItemBySlot(EquipmentSlot.CHEST).copy());

        // 頭部: ヘルメットを被っていればそれを装着し、無ければプレイヤーのスキン頭部を生成して装着
        ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!headItem.isEmpty()) {
            dummy.setItemSlot(EquipmentSlot.HEAD, headItem.copy());
        } else {
            ItemStack customHead = new ItemStack(Items.PLAYER_HEAD);
            customHead.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
            dummy.setItemSlot(EquipmentSlot.HEAD, customHead);
        }

        // 手持ちアイテム（メインハンド: ドローンコントローラー、オフハンド）
        dummy.setItemSlot(EquipmentSlot.MAINHAND, player.getItemBySlot(EquipmentSlot.MAINHAND).copy());
        dummy.setItemSlot(EquipmentSlot.OFFHAND, player.getItemBySlot(EquipmentSlot.OFFHAND).copy());

        serverLevel.addFreshEntity(dummy);
        this.dummyEntityUUID = dummy.getUUID();
        this.dummyEntityRef = dummy;
        ACTIVE_PILOTED_DRONES.put(player.getUUID(), this);
        DUMMY_TO_DRONE.put(dummy.getUUID(), this);
        MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_SPAWN] Spawned body dummy {} at [{}, {}, {}]",
                this.dummyEntityUUID,
                String.format("%.2f", this.startX), String.format("%.2f", this.startY), String.format("%.2f", this.startZ));

        // 3. サーバー側のカメラエンティティをドローンに設定（バニラのチャンク配信ストリーミングを活用）
        player.setCamera(this);
        player.setInvisible(true); // 操縦中のプレイヤー本体を透明化（初期位置のダミー人形のみを表示）

        MicroDroneMod.LOGGER.info("[SERVER] [PILOT_START] Pilot: {} ({}), PlayerPos: [{}, {}, {}], DronePos: [{}, {}, {}], DroneChunk: [{}, {}]",
                player.getName().getString(), player.getUUID(),
                String.format("%.2f", player.getX()), String.format("%.2f", player.getY()), String.format("%.2f", player.getZ()),
                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                this.chunkPosition().x, this.chunkPosition().z);

        PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), true, this.startX, this.startY, this.startZ));
        player.sendSystemMessage(Component.translatable("message.microdrone.pilot_start"));

        // Phase 5: 操縦開始時にドローン周辺チャンクをロード・配信
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
            ArmorStand targetDummyRef = this.dummyEntityRef;
            this.dummyEntityRef = null;
            this.dummyEntityUUID = null;

            if (targetDummyUUID != null) {
                DUMMY_TO_DRONE.remove(targetDummyUUID);
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
            player.setInvisible(false); // 操縦終了時に透明化を解除

            // 4. 【パターンAの核心】プレイヤーの実体を記録した初期位置へテレポート復元！
            double restoreX = this.startX;
            double restoreY = this.startY;
            double restoreZ = this.startZ;
            ChunkPos restoreChunkPos = this.startChunkPos;
            this.startChunkPos = null;

            if (this.startDimension != null && player.getServer() != null) {
                ServerLevel targetLevel = player.getServer().getLevel(this.startDimension);
                if (targetLevel != null) {
                    player.teleportTo(targetLevel, this.startX, this.startY, this.startZ, this.startYaw, this.startPitch);
                    MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_RESTORE] Teleported player {} back to start pos: [{}, {}, {}]",
                            player.getName().getString(),
                            String.format("%.2f", this.startX), String.format("%.2f", this.startY), String.format("%.2f", this.startZ));

                    // 5. 【テレポート後ダミー確実消去（遠距離飛行からの復帰対策）】
                    // 遠距離から復元した場合、プレイヤーがテレポートしたことで初期位置チャンクのエンティティが確実にアクティブ化される。
                    // そのため、テレポート直後に再度UUID検索および足元範囲のダミー消去を実行！
                    if (targetDummyUUID != null) {
                        Entity dummyAfter = targetLevel.getEntity(targetDummyUUID);
                        if (dummyAfter != null) {
                            dummyAfter.discard();
                            MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_REMOVE_POST_TP] Discarded dummy by UUID after teleport: {}", targetDummyUUID);
                        }
                    }

                    // 足元周囲の身代わりダミー人形（ArmorStand）の安全なクリーンアップフェイルセーフ
                    AABB clearBox = new AABB(restoreX - 1.5, restoreY - 1.0, restoreZ - 1.5,
                                             restoreX + 1.5, restoreY + 2.5, restoreZ + 1.5);
                    List<ArmorStand> nearbyStands = targetLevel.getEntitiesOfClass(ArmorStand.class, clearBox,
                            stand -> (targetDummyUUID != null && targetDummyUUID.equals(stand.getUUID()))
                                  || (stand.getCustomName() != null && stand.getCustomName().getString().contains("[操縦中]")));
                    for (ArmorStand stand : nearbyStands) {
                        stand.discard();
                        MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_CLEANUP] Discarded remaining dummy stand: {}", stand.getUUID());
                    }
                }
                this.startDimension = null;
            }

            // 6. 初期位置チャンクの保護解除（テレポートとダミー消去が完全に完了した後に解除）
            if (restoreChunkPos != null) {
                serverLevel.setChunkForced(restoreChunkPos.x, restoreChunkPos.z, false);
                MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_BODY_UNPROTECT] Unprotected player start chunk: [{}, {}]",
                        restoreChunkPos.x, restoreChunkPos.z);
            }

            ACTIVE_PILOTED_DRONES.remove(player.getUUID());
            this.pilotUUID = null;
            this.inputForward = 0.0f;
            this.inputStrafe = 0.0f;
            this.inputVertical = 0.0f;
            this.targetYaw = 0.0f;
            this.targetPitch = 0.0f;
            PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), false, 0, 0, 0));
            player.sendSystemMessage(Component.translatable("message.microdrone.pilot_stop"));
        }
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        // 操縦中のプレイヤーに対しては、距離に関係なく常にエンティティ同期を維持
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
            if (this.pilotUUID != null) {
                ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
                if (pilot == null || !pilot.isAlive() || pilot.level() != this.level()) {
                    this.pilotUUID = null;
                }
            }

            if (this.isPiloted()) {
                tickFlightPhysics();
            } else {
                tickUnpilotedPhysics();
            }
        } else {
            // === クライアント側 ===
            // 自プレイヤーが操縦中の場合はクライアント側でも毎tick物理計算を実行！
            // これによりカメラの partialTick 補間が完全に滑らかになり、画面のカクつきが解消される。
            if (this.locallyControlled) {
                tickFlightPhysics();
            } else if (!this.isNoGravity() && !this.onGround()) {
                tickUnpilotedPhysics();
            }
        }
    }

    /**
     * 操縦中の飛行物理計算（サーバー＆クライアントローカル共通）
     */
    private void tickFlightPhysics() {
        // 1. 旋回 (Yaw) と カメラピッチ (Pitch) の適用
        // サーバー側でのみパケット角度を適用（クライアント側はバニラのMouseHandlerによる超滑らかなフレーム補間を完全維持）
        if (!this.level().isClientSide) {
            this.setYRot(this.targetYaw);
            this.setYHeadRot(this.targetYaw);
            this.setXRot(Mth.clamp(this.targetPitch, -85.0F, 85.0F));
        }

        Vec3 currentVel = this.getDeltaMovement();

        // 2. 機首方向に応じた目標加速度ベクトルの算出
        float rad = (float) Math.toRadians(this.getYRot());
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        double forwardAccelX = -sin * this.inputForward * ACCELERATION;
        double forwardAccelZ = cos * this.inputForward * ACCELERATION;

        double strafeAccelX = cos * this.inputStrafe * ACCELERATION;
        double strafeAccelZ = sin * this.inputStrafe * ACCELERATION;

        double vx = (currentVel.x + forwardAccelX + strafeAccelX) * HORIZONTAL_DRAG;
        double vz = (currentVel.z + forwardAccelZ + strafeAccelZ) * HORIZONTAL_DRAG;

        double horizontalSpeed = Math.sqrt(vx * vx + vz * vz);
        if (horizontalSpeed > MAX_HORIZONTAL_SPEED) {
            double scale = MAX_HORIZONTAL_SPEED / horizontalSpeed;
            vx *= scale;
            vz *= scale;
        }

        // 3. 垂直昇降とホバリング高度維持
        double vy = currentVel.y;
        if (Math.abs(this.inputVertical) > 0.05f) {
            vy += this.inputVertical * VERTICAL_ACCELERATION;
            vy = Math.max(-MAX_VERTICAL_SPEED, Math.min(MAX_VERTICAL_SPEED, vy));
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

        // サーバー側処理: 操縦中プレイヤーの実体をドローン座標へ追従移動 & チャンク更新
        if (!this.level().isClientSide && this.pilotUUID != null && this.getServer() != null) {
            ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
            if (pilot != null && pilot.isAlive()) {
                // 1. 【パターンAの核心】サーバー側プレイヤーの実体座標をドローンに同期！
                // これにより、バニラのChunkMap（エンティティ追跡）でドローンが常にプレイヤーの直近（0m）と認識され、
                // 遠距離飛行時（128m+）でもENTITY_LEAVE_LEVELによるドローン消失や強制切断が100%防止される。
                // 操縦終了時は初期位置へteleportToで完全復元される。
                pilot.absMoveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                pilot.serverLevel().getChunkSource().move(pilot);

                // 2. 機体の移動に完全に追従するチャンク更新（チャンク境界を跨いだ瞬間即時、または10ticksごと）
                ChunkPos currentChunk = new ChunkPos(this.blockPosition());
                boolean chunkChanged = (this.lastChunkPos == null || !this.lastChunkPos.equals(currentChunk));
                if (chunkChanged || (this.hoverTicks % 10 == 0)) {
                    if (chunkChanged) {
                        MicroDroneMod.LOGGER.debug("[SERVER] [CHUNK_BORDER_CROSS] DroneUUID: {}, DronePos: [{}, {}, {}], PrevChunk: {}, NewChunk: [{}, {}]",
                                this.getUUID(),
                                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()), String.format("%.2f", this.getZ()),
                                this.lastChunkPos != null ? "[" + this.lastChunkPos.x + ", " + this.lastChunkPos.z + "]" : "INIT",
                                currentChunk.x, currentChunk.z);
                    }
                    this.lastChunkPos = currentChunk;
                    com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(pilot, this);
                }

                // 3. 電波距離制限（フライトレンジ）と通信途絶フェイルセーフ (Step 4)
                double dx = this.getX() - this.startX;
                double dy = this.getY() - this.startY;
                double dz = this.getZ() - this.startZ;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (distance > MAX_SIGNAL_RANGE) {
                    this.outOfRangeTicks++;
                    // 入力操作を打ち消し、自動ホバリング＆緩やかな降下（安全着陸姿勢）
                    this.inputForward = 0.0f;
                    this.inputStrafe = 0.0f;
                    this.inputVertical = -0.05f;

                    // 60 ticks (約3秒間) 電波圏外が継続、または地面に着地した場合、自動で安全操縦終了
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

                // 4. 前後移動距離に応じたバッテリー消費 & オートランディング (Step 5)
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

                // バッテリー切れフェイルセーフ: 入力を打ち消し、緩やかに自動降下
                if (this.installedBattery.isEmpty() || this.getBatteryRemaining() <= 0) {
                    this.inputForward = 0.0f;
                    this.inputStrafe = 0.0f;
                    this.inputVertical = -0.04f; // 緩やかな自動降下着陸

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
        }
        if (this.dummyEntityUUID != null) {
            DUMMY_TO_DRONE.remove(this.dummyEntityUUID);
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

            // 2. コントローラー所持時: 操縦開始 / 終了
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

            // 3. スニーク右クリック時: バッテリー取り外し（未装着ならドローン本体回収）
            if (player.isShiftKeyDown()) {
                if (!this.installedBattery.isEmpty()) {
                    // バッテリー取り外し
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
            this.level().addFreshEntity(item);

            // バッテリーが装着されていれば一緒にドロップ (消失防止)
            if (!this.installedBattery.isEmpty()) {
                ItemEntity batItem = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.installedBattery);
                this.level().addFreshEntity(batItem);
                this.installedBattery = ItemStack.EMPTY;
            }

            this.discard();
        }
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
        compound.putDouble("BatteryAccumulatedDist", this.accumulatedDistance);
    }
}
