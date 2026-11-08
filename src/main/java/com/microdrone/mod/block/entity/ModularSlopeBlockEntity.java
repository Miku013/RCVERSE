package com.microdrone.mod.block.entity;

import com.microdrone.mod.block.ModularSlopeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * つなげた分だけ自動でどこまでも浅く・滑らかになる動的モジュラースロープ用 BlockEntity
 */
public class ModularSlopeBlockEntity extends BlockEntity {
    private int chainIndex = 0;
    private int totalChain = 1;
    private boolean hasMinSide = false; // ローカル X=0 側
    private boolean hasMaxSide = false; // ローカル X=1 側
    private boolean dirty = true;

    public ModularSlopeBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.MODULAR_SLOPE_BE.get(), pos, blockState);
    }

    public boolean isDirty() {
        return dirty;
    }

    public int getChainIndex() {
        if (dirty)
            recalculateChain();
        return chainIndex;
    }

    public int getTotalChain() {
        if (dirty)
            recalculateChain();
        return totalChain;
    }

    public boolean hasMinSideSlope() {
        if (dirty)
            recalculateChain();
        return hasMinSide;
    }

    public boolean hasMaxSideSlope() {
        if (dirty)
            recalculateChain();
        return hasMaxSide;
    }

    public void markDirtyChain() {
        this.dirty = true;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.dirty = true;
    }

    public void recalculateChain() {
        if (this.level == null)
            return;
        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof ModularSlopeBlock))
            return;

        Direction facing = state.getValue(ModularSlopeBlock.FACING);
        Direction backwardDir = facing.getOpposite();
        Direction forwardDir = facing;

        int backCount = 0;
        BlockPos checkPos = this.worldPosition.relative(backwardDir);
        while (backCount < 64 && isSameFacingSlope(checkPos, facing)) {
            backCount++;
            checkPos = checkPos.relative(backwardDir);
        }

        int fwdCount = 0;
        checkPos = this.worldPosition.relative(forwardDir);
        while (fwdCount < 64 && isSameFacingSlope(checkPos, facing)) {
            fwdCount++;
            checkPos = checkPos.relative(forwardDir);
        }

        this.chainIndex = backCount;
        this.totalChain = Math.max(1, backCount + fwdCount + 1);

        // facing=SOUTH のとき:
        // ClockWise = WEST = ローカル X=0 (MinSide)
        // CounterClockWise = EAST = ローカル X=1 (MaxSide)
        Direction minSideDir = facing.getClockWise();
        Direction maxSideDir = facing.getCounterClockWise();
        this.hasMinSide = isSameFacingSlope(this.worldPosition.relative(minSideDir), facing);
        this.hasMaxSide = isSameFacingSlope(this.worldPosition.relative(maxSideDir), facing);

        this.dirty = false;
    }

    private boolean isSameFacingSlope(BlockPos pos, Direction targetFacing) {
        if (this.level == null)
            return false;
        BlockState st = this.level.getBlockState(pos);
        return (st.getBlock() instanceof ModularSlopeBlock) && st.getValue(ModularSlopeBlock.FACING) == targetFacing;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.dirty) {
            recalculateChain();
        }
        tag.putInt("ChainIndex", this.chainIndex);
        tag.putInt("TotalChain", this.totalChain);
        tag.putBoolean("HasMinSide", this.hasMinSide);
        tag.putBoolean("HasMaxSide", this.hasMaxSide);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.chainIndex = tag.getInt("ChainIndex");
        this.totalChain = Math.max(1, tag.getInt("TotalChain"));
        this.hasMinSide = tag.getBoolean("HasMinSide");
        this.hasMaxSide = tag.getBoolean("HasMaxSide");
        this.dirty = false;
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
