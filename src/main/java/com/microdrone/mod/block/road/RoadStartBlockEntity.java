package com.microdrone.mod.block.road;

import com.microdrone.mod.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 道路生成始点ブロック（Road Start Marker）用 BlockEntity
 */
public class RoadStartBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos linkedEndPos = null;
    private final RoadSplineData splineData = new RoadSplineData();

    public RoadStartBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ROAD_START_BE.get(), pos, blockState);
    }

    public RoadSplineData getSplineData() {
        return splineData;
    }

    @Nullable
    public BlockPos getLinkedEndPos() {
        return linkedEndPos;
    }

    public void setLinkedEndPos(@Nullable BlockPos endPos) {
        this.linkedEndPos = endPos;
        setChanged();
        if (level != null) {
            RoadSplineManager.registerRoad(level, this);
            if (!level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            RoadSplineManager.registerRoad(level, this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            RoadSplineManager.unregisterRoad(level, worldPosition);
        }
        super.setRemoved();
    }

    /**
     * 周囲から最も近い RoadEndBlock を探索して自動リンク
     * ※既に他の有効な始点マーカーとペアになっている終点は検索から除外されます。
     */
    public boolean autoFindNearestEndBlock(int radius) {
        if (level == null) return false;
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius / 2; dy <= radius / 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = worldPosition.offset(dx, dy, dz);
                    if (p.equals(worldPosition)) continue;
                    if (level.getBlockState(p).getBlock() instanceof RoadEndBlock) {
                        // 既に他の有効な始点マーカーとペアリングされている場合は除外
                        if (level.getBlockEntity(p) instanceof RoadEndBlockEntity endBe) {
                            BlockPos otherStartPos = endBe.getLinkedStartPos();
                            if (otherStartPos != null && !otherStartPos.equals(worldPosition)) {
                                if (level.getBlockEntity(otherStartPos) instanceof RoadStartBlockEntity otherStart) {
                                    if (p.equals(otherStart.getLinkedEndPos())) {
                                        // 既に有効なペアが成立しているためスキップ
                                        continue;
                                    }
                                }
                            }
                        }

                        double dSq = worldPosition.distSqr(p);
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            bestPos = p.immutable();
                        }
                    }
                }
            }
        }

        if (bestPos != null) {
            setLinkedEndPos(bestPos);
            // 終点側にも逆リンクを設定
            if (level.getBlockEntity(bestPos) instanceof RoadEndBlockEntity endBe) {
                endBe.setLinkedStartPos(worldPosition);
            }
            return true;
        }
        return false;
    }

    /**
     * チャンク境界をまたいでも描画がカリングされないよう、始点〜終点を包含する巨大AABBを返却
     */
    public AABB getRoadBoundingBox() {
        if (linkedEndPos != null) {
            int minX = Math.min(worldPosition.getX(), linkedEndPos.getX()) - 16;
            int minY = Math.min(worldPosition.getY(), linkedEndPos.getY()) - 8;
            int minZ = Math.min(worldPosition.getZ(), linkedEndPos.getZ()) - 16;
            int maxX = Math.max(worldPosition.getX(), linkedEndPos.getX()) + 17;
            int maxY = Math.max(worldPosition.getY(), linkedEndPos.getY()) + 16;
            int maxZ = Math.max(worldPosition.getZ(), linkedEndPos.getZ()) + 17;
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return new AABB(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (linkedEndPos != null) {
            tag.put("LinkedEndPos", NbtUtils.writeBlockPos(linkedEndPos));
        }
        CompoundTag dataTag = new CompoundTag();
        splineData.saveToNbt(dataTag);
        tag.put("SplineData", dataTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("LinkedEndPos")) {
            this.linkedEndPos = NbtUtils.readBlockPos(tag, "LinkedEndPos").orElse(null);
        } else {
            this.linkedEndPos = null;
        }
        if (tag.contains("SplineData")) {
            this.splineData.loadFromNbt(tag.getCompound("SplineData"));
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
