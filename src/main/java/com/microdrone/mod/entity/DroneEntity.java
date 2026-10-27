package com.microdrone.mod.entity;

import com.microdrone.mod.MicroDroneMod;
import com.microdrone.mod.item.ModItems;
import com.microdrone.mod.network.DronePilotStatePayload;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class DroneEntity extends Entity {

    // === 飛行制御パラメータ (Phase 3) ===
    public static final double MAX_HORIZONTAL_SPEED = 0.35;    // 最大水平速度 (約 7 m/s)
    public static final double ACCELERATION = 0.04;             // 水平加速度
    public static final double HORIZONTAL_DRAG = 0.90;          // 水平慣性摩擦
    public static final double MAX_VERTICAL_SPEED = 0.25;      // 最大昇降速度 (約 5 m/s)
    public static final double VERTICAL_ACCELERATION = 0.035;   // 昇降加速度
    public static final double VERTICAL_DRAG = 0.82;            // 垂直慣性摩擦 (ホバリング維持)
    public static final float YAW_SENSITIVITY = 1.0f;           // 旋回感度

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

    // クライアント側で自プレイヤーが操縦中かどうかのフラグ（カクつき防止・クライアント予測用）
    private boolean locallyControlled = false;


    // 入力値 (-1.0 ~ 1.0)
    private float inputForward = 0.0f;
    private float inputStrafe = 0.0f;
    private float inputVertical = 0.0f;
    private float inputYaw = 0.0f;
    private float inputPitch = 0.0f;

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
    }

    public boolean isPiloted() {
        return this.pilotUUID != null;
    }

    public boolean isPilotedBy(Player player) {
        return player != null && this.pilotUUID != null && this.pilotUUID.equals(player.getUUID());
    }

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
        dummy.setInvulnerable(true);
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

        PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), true));
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

            // 1. 身代わりダミーの消去
            if (this.dummyEntityUUID != null) {
                Entity dummy = serverLevel.getEntity(this.dummyEntityUUID);
                if (dummy != null) {
                    dummy.discard();
                    MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_DUMMY_REMOVE] Removed dummy: {}", this.dummyEntityUUID);
                }
                this.dummyEntityUUID = null;
            }

            // 2. 初期位置チャンクの保護解除
            if (this.startChunkPos != null) {
                serverLevel.setChunkForced(this.startChunkPos.x, this.startChunkPos.z, false);
                MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_BODY_UNPROTECT] Unprotected player start chunk: [{}, {}]",
                        this.startChunkPos.x, this.startChunkPos.z);
                this.startChunkPos = null;
            }

            // 3. ドローン周辺チャンクの解放
            this.lastChunkPos = null;
            com.microdrone.mod.chunk.DroneChunkManager.releaseDroneChunks(player, this);

            // 4. サーバー側のカメラをプレイヤー自身に復元
            player.setCamera(player);
            player.setInvisible(false); // 操縦終了時に透明化を解除

            // 5. 【パターンAの核心】プレイヤーの実体を記録した初期位置へテレポート復元！
            if (this.startDimension != null && player.getServer() != null) {
                ServerLevel targetLevel = player.getServer().getLevel(this.startDimension);
                if (targetLevel != null) {
                    player.teleportTo(targetLevel, this.startX, this.startY, this.startZ, this.startYaw, this.startPitch);
                    MicroDroneMod.LOGGER.info("[SERVER] [PLAYER_RESTORE] Teleported player {} back to start pos: [{}, {}, {}]",
                            player.getName().getString(),
                            String.format("%.2f", this.startX), String.format("%.2f", this.startY), String.format("%.2f", this.startZ));
                }
                this.startDimension = null;
            }

            this.pilotUUID = null;
            this.inputForward = 0.0f;
            this.inputStrafe = 0.0f;
            this.inputVertical = 0.0f;
            this.inputYaw = 0.0f;
            this.inputPitch = 0.0f;
            PacketDistributor.sendToPlayer(player, new DronePilotStatePayload(this.getId(), false));
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
        this.inputYaw = yaw * YAW_SENSITIVITY;
        this.inputPitch = pitch;
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
        // 1. 旋回 (Yaw) と ピッチ (Pitch)
        if (Math.abs(this.inputYaw) > 0.001f) {
            float newYaw = this.getYRot() + this.inputYaw;
            this.setYRot(newYaw);
            this.setYHeadRot(newYaw);
        }
        float clampedPitch = Mth.clamp(this.inputPitch, -60.0F, 60.0F);
        this.setXRot(clampedPitch);

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

        // Phase 5: 機体の移動に完全に追従するチャンク更新（チャンク境界を跨いだ瞬間即時、または10ticksごと）
        if (!this.level().isClientSide && this.pilotUUID != null) {
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
                ServerPlayer pilot = this.getServer().getPlayerList().getPlayer(this.pilotUUID);
                if (pilot != null) {
                    com.microdrone.mod.chunk.DroneChunkManager.updateDroneChunks(pilot, this);
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        MicroDroneMod.LOGGER.info("[SERVER] [ENTITY_REMOVE] DroneUUID: {}, Reason: {}, WasPiloted: {}, PilotUUID: {}",
                this.getUUID(), reason, this.isPiloted(), this.pilotUUID);

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

            if (player.isShiftKeyDown()) {
                this.dropItemAndDiscard();
                return InteractionResult.SUCCESS;
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
            this.discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("PilotUUID")) {
            this.pilotUUID = compound.getUUID("PilotUUID");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.pilotUUID != null) {
            compound.putUUID("PilotUUID", this.pilotUUID);
        }
    }
}
