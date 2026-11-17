package com.microdrone.mod.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * RC操縦中に操縦開始位置に佇む「プレイヤーの身代わりクローン」エンティティ。
 * 操縦者本人のスキンテクスチャ、名前、防具、手持ちプロポを完全にコピーして表示する。
 */
public class RcPilotDummyEntity extends LivingEntity {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER_UUID =
            SynchedEntityData.defineId(RcPilotDummyEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(RcPilotDummyEntity.class, EntityDataSerializers.STRING);

    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);

    @Nullable
    private GameProfile cachedProfile = null;

    public RcPilotDummyEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER_UUID, Optional.empty());
        builder.define(DATA_OWNER_NAME, "");
    }

    /**
     * 操縦者プレイヤーのプロファイルや装備をクローン
     */
    public void setupFromPlayer(ServerPlayer player) {
        this.setOwnerUUID(player.getUUID());
        this.setOwnerName(player.getGameProfile().getName());
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot());
        this.setYHeadRot(player.getYHeadRot());
        this.setYBodyRot(player.getYRot());

        // 装備アイテム（防具4スロット）をコピー
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                this.setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
        }

        // 手持ちのプロポ（メインハンドアイテム）をコピー
        this.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copy());

        // 頭上のネームタグ設定
        this.setCustomName(Component.translatable("entity.microdrone.pilot_dummy_name", player.getName().getString()));
        this.setCustomNameVisible(true);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_OWNER_UUID, Optional.ofNullable(uuid));
        this.cachedProfile = null;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return this.entityData.get(DATA_OWNER_UUID).orElse(null);
    }

    public void setOwnerName(String name) {
        this.entityData.set(DATA_OWNER_NAME, name != null ? name : "");
        this.cachedProfile = null;
    }

    public String getOwnerName() {
        return this.entityData.get(DATA_OWNER_NAME);
    }

    @Nullable
    public GameProfile getOwnerProfile() {
        if (this.cachedProfile == null) {
            UUID uuid = getOwnerUUID();
            String name = getOwnerName();
            if (uuid != null) {
                this.cachedProfile = new GameProfile(uuid, name.isEmpty() ? "Pilot" : name);
            }
        }
        return this.cachedProfile;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 攻撃を受けた場合、操縦中の機体を探して安全に操縦終了
        if (!this.level().isClientSide) {
            UUID ownerId = getOwnerUUID();
            if (ownerId != null) {
                RcControllable vehicle = RcVehicleManager.getByPilot(ownerId);
                if (vehicle != null && this.getServer() != null) {
                    ServerPlayer player = this.getServer().getPlayerList().getPlayer(ownerId);
                    if (player != null) {
                        vehicle.stopPiloting(player);
                    }
                }
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0, 0, 0);

        if (!this.level().isClientSide) {
            // ① 操縦中のビークルと紐付いていない孤立ダミーは即座に完全消滅！ (ワールド再開・リロード時のゾンビダミー即時排除)
            if (RcVehicleManager.getByDummy(this.getUUID()) == null) {
                this.discard();
                return;
            }

            // ② 操縦者プレイヤーがサーバーに存在しない・死亡している場合も即座に消滅
            UUID ownerId = getOwnerUUID();
            if (ownerId == null || this.getServer() == null) {
                this.discard();
                return;
            }

            ServerPlayer player = this.getServer().getPlayerList().getPlayer(ownerId);
            if (player == null || !player.isAlive()) {
                this.discard();
            }
        }
    }

    @Override
    public boolean save(CompoundTag compound) {
        // ダミー人形はRC操縦中のみ一時的に表示する身代わりクローン。
        // ワールドセーブデータ（NBT）への保存を完全禁止し、ワールド再入室時の残留・増殖を100%防止する
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {}

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> this.handItems.get(slot.getIndex());
            case HUMANOID_ARMOR -> this.armorItems.get(slot.getIndex());
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND -> this.handItems.set(slot.getIndex(), stack);
            case HUMANOID_ARMOR -> this.armorItems.set(slot.getIndex(), stack);
            default -> {}
        }
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        UUID ownerId = getOwnerUUID();
        if (ownerId != null) {
            compound.putUUID("OwnerUUID", ownerId);
        }
        compound.putString("OwnerName", getOwnerName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.hasUUID("OwnerUUID")) {
            setOwnerUUID(compound.getUUID("OwnerUUID"));
        }
        if (compound.contains("OwnerName")) {
            setOwnerName(compound.getString("OwnerName"));
        }
        // 過去のセーブデータ等から読み込まれた孤立ダミーは即座に完全消去
        if (!this.level().isClientSide && RcVehicleManager.getByDummy(this.getUUID()) == null) {
            this.discard();
        }
    }
}

