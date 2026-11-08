package com.microdrone.mod.block.road;

import com.microdrone.mod.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 道路生成終点ブロック（Road End Marker）用 BlockEntity
 */
public class RoadEndBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos linkedStartPos = null;

    public RoadEndBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ROAD_END_BE.get(), pos, blockState);
    }

    @Nullable
    public BlockPos getLinkedStartPos() {
        return linkedStartPos;
    }

    public void setLinkedStartPos(@Nullable BlockPos startPos) {
        this.linkedStartPos = startPos;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkedStartPos != null) {
            tag.put("LinkedStartPos", NbtUtils.writeBlockPos(linkedStartPos));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LinkedStartPos")) {
            this.linkedStartPos = NbtUtils.readBlockPos(tag, "LinkedStartPos").orElse(null);
        } else {
            this.linkedStartPos = null;
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}

