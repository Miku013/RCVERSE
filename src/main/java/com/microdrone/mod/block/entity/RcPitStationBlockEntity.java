package com.microdrone.mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * RCピット作業台のブロックエンティティ。
 * チューニング・鑑賞対象のRCビークルItemStackを保管します。
 */
public class RcPitStationBlockEntity extends BlockEntity {
    private ItemStack storedVehicle = ItemStack.EMPTY;

    public RcPitStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RC_PIT_STATION.get(), pos, state);
    }

    public ItemStack getStoredVehicle() {
        return this.storedVehicle;
    }

    public void setStoredVehicle(ItemStack stack) {
        this.storedVehicle = stack.copy();
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.storedVehicle.isEmpty()) {
            tag.put("StoredVehicle", this.storedVehicle.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("StoredVehicle", 10)) {
            this.storedVehicle = ItemStack.parse(registries, tag.getCompound("StoredVehicle")).orElse(ItemStack.EMPTY);
        } else {
            this.storedVehicle = ItemStack.EMPTY;
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
        }
    }
}
