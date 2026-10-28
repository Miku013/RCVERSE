package com.microdrone.mod.block.entity;

import com.microdrone.mod.block.DroneChargerBlock;
import com.microdrone.mod.item.DroneBatteryItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DroneChargerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 充電器にセットされたバッテリーアイテム
    private ItemStack storedBattery = ItemStack.EMPTY;

    // tick カウンター
    private int tickCount = 0;

    public DroneChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_CHARGER_BE.get(), pos, state);
    }

    public ItemStack getStoredBattery() {
        return this.storedBattery;
    }

    public void setStoredBattery(ItemStack battery) {
        this.storedBattery = battery;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            // クライアント側へ同期
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /**
     * サーバーサイドの充電処理ループ
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneChargerBlockEntity charger) {
        charger.tickCount++;

        boolean isCharging = false;
        ItemStack battery = charger.storedBattery;

        if (!battery.isEmpty() && battery.getItem() instanceof DroneBatteryItem batteryItem) {
            int currentDamage = battery.getDamageValue();
            if (currentDamage > 0) {
                // 充電処理: 毎tick 15ブロック分（1秒あたり 300m）急速充電
                int chargePerTick = 15;
                int newDamage = Math.max(0, currentDamage - chargePerTick);
                battery.setDamageValue(newDamage);
                charger.setChanged();

                isCharging = true;

                ServerLevel serverLevel = (ServerLevel) level;

                // 4tickごとに充電スパークパーティクルを発生
                if (charger.tickCount % 4 == 0) {
                    serverLevel.sendParticles(
                            ParticleTypes.ELECTRIC_SPARK,
                            pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                            2, 0.15, 0.05, 0.15, 0.02
                    );
                }

                // 20tick（1秒）ごとに低音の充電動作音を再生
                if (charger.tickCount % 20 == 0) {
                    level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3F, 1.8F);
                }

                // 満充電に達した瞬間のエフェクト
                if (newDamage == 0) {
                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 1.5F);
                    serverLevel.sendParticles(
                            ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                            8, 0.25, 0.1, 0.25, 0.05
                    );
                }
            }
        }

        // ブロック状態の CHARGING プロパティを同期
        if (state.getValue(DroneChargerBlock.CHARGING) != isCharging) {
            level.setBlock(pos, state.setValue(DroneChargerBlock.CHARGING, isCharging), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.storedBattery.isEmpty()) {
            tag.put("StoredBattery", this.storedBattery.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("StoredBattery", 10)) {
            this.storedBattery = ItemStack.parse(registries, tag.getCompound("StoredBattery")).orElse(ItemStack.EMPTY);
        } else {
            this.storedBattery = ItemStack.EMPTY;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // === GeckoLib 4 アニメーションコントローラー ===
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "charger_controller", 5, state -> {
            if (this.getBlockState().getValue(DroneChargerBlock.CHARGING)) {
                return state.setAndContinue(RawAnimation.begin().thenLoop("charging"));
            }
            return state.setAndContinue(RawAnimation.begin().thenLoop("idle"));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
