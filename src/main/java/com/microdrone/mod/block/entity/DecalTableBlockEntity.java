package com.microdrone.mod.block.entity;

import com.microdrone.mod.block.ModBlocks;
import com.microdrone.mod.item.RcCarItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DecalTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 作業台の上に置かれているRCカーのアイテム
    private ItemStack storedCar = ItemStack.EMPTY;

    public DecalTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECAL_TABLE_BE.get(), pos, state);
    }

    public ItemStack getStoredCar() {
        return this.storedCar;
    }

    public void setStoredCar(ItemStack car) {
        this.storedCar = car != null ? car : ItemStack.EMPTY;
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public boolean hasCar() {
        return !this.storedCar.isEmpty() && this.storedCar.getItem() instanceof RcCarItem;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.storedCar.isEmpty()) {
            tag.put("StoredCar", this.storedCar.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("StoredCar", 10)) {
            this.storedCar = ItemStack.parse(registries, tag.getCompound("StoredCar")).orElse(ItemStack.EMPTY);
        } else {
            this.storedCar = ItemStack.EMPTY;
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

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        } else {
            this.storedCar = ItemStack.EMPTY;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.CONTINUE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}

